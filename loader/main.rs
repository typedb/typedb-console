/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{fs::read_to_string, path::{Path, PathBuf}, process::exit, sync::Arc, time::Instant};

use clap::Parser;
use csv::StringRecord;
use futures::stream::{FuturesUnordered, StreamExt};
use tokio::task::JoinHandle;
use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
    transaction::{QueryGivenRow, QueryGivenRows},
};

use crate::{
    cli::{Args, USERNAME_VALUE_NAME},
    data::{CsvLoader, RowRejection},
    progress::{LoadStats, print_progress, print_summary},
    query::parse_query_inputs,
    rejects::{RejectsWriter, default_rejects_path},
};

mod cli;
mod data;
mod progress;
mod query;
mod rejects;

#[tokio::main]
async fn main() {
    let mut args = Args::parse();

    let username = args.username.take().unwrap_or_else(|| {
        fatal(format!(
            "username is required for connection authentication ('--{USERNAME_VALUE_NAME} <username>')."
        ))
    });
    let password = args
        .password
        .take()
        .unwrap_or_else(|| rpassword::prompt_password(format!("password for '{username}': ")).unwrap());

    let query_text = read_to_string(&args.query)
        .unwrap_or_else(|err| fatal(format!("failed to read query file '{}': {err}", args.query)));
    let schema = args.schema_file.as_deref().map(|path| {
        read_to_string(path).unwrap_or_else(|err| fatal(format!("failed to read schema file '{path}': {err}")))
    });

    let inputs = parse_query_inputs(&query_text).unwrap_or_else(|err| fatal(err));
    let mut loader = CsvLoader::open(&args.data, args.header, inputs, args.null_values, args.max_rows)
        .unwrap_or_else(|err| fatal(format!("failed to open data file '{}': {err}", args.data)));

    let addresses = parse_addresses(&args.addresses);
    let tls_config = if args.tls_disabled {
        DriverTlsConfig::disabled()
    } else if let Some(ca) = args.tls_root_ca.as_deref() {
        DriverTlsConfig::enabled_with_root_ca(Path::new(ca)).unwrap()
    } else {
        DriverTlsConfig::enabled_with_native_root_ca()
    };
    let driver = TypeDBDriver::new(addresses, Credentials::new(&username, &password), DriverOptions::new(tls_config))
        .await
        .unwrap_or_else(|err| fatal(format!("failed to connect to TypeDB: {err}")));

    if args.create_db {
        let exists = driver
            .databases()
            .contains(args.database.clone())
            .await
            .unwrap_or_else(|err| fatal(format!("failed to check if database '{}' exists: {err}", args.database)));
        if !exists {
            driver
                .databases()
                .create(args.database.clone())
                .await
                .unwrap_or_else(|err| fatal(format!("failed to create database '{}': {err}", args.database)));
        }
    }

    if let Some(schema) = schema {
        let schema_tx = driver
            .transaction(args.database.clone(), TransactionType::Schema)
            .await
            .unwrap_or_else(|err| fatal(format!("failed to open schema transaction on '{}': {err}", args.database)));
        schema_tx.query(schema).await.unwrap_or_else(|err| fatal(format!("schema query failed: {err}")));
        schema_tx.commit().await.unwrap_or_else(|err| fatal(format!("failed to commit schema transaction: {err}")));
    }

    if args.batch_rows == 0 {
        fatal("--batch-rows must be greater than 0");
    }
    if args.parallel_batches == 0 {
        fatal("--parallel-batches must be greater than 0");
    }

    let total_bytes = loader.file_size();
    let rejects_csv_path = args.rejects_file.map(PathBuf::from).unwrap_or_else(|| default_rejects_path(&args.data, "csv"));
    let rejects_log_path = args.rejects_log.map(PathBuf::from).unwrap_or_else(|| default_rejects_path(&args.data, "log"));
    let mut rejects = RejectsWriter::new(rejects_csv_path, rejects_log_path, loader.headers().cloned());

    let driver = Arc::new(driver);
    let database = Arc::new(args.database.clone());
    let query_text = Arc::new(query_text);

    let mut stats = LoadStats::default();
    let started = Instant::now();
    let mut batch_idx = 0usize;
    let mut stop_now = false;
    let mut stop_reason: Option<String> = None;
    let mut producing = true;
    let mut in_flight: FuturesUnordered<JoinHandle<BatchResult>> = FuturesUnordered::new();

    loop {
        while producing && !stop_now && in_flight.len() < args.parallel_batches {
            match loader.next_batch(args.batch_rows) {
                Some(batch) => {
                    batch_idx += 1;
                    let driver = driver.clone();
                    let database = database.clone();
                    let query_text = query_text.clone();
                    in_flight.push(tokio::spawn(async move {
                        process_batch(driver, database, query_text, batch_idx, batch).await
                    }));
                }
                None => producing = false,
            }
        }

        let Some(joined) = in_flight.next().await else { break };
        let result = joined.unwrap_or_else(|err| fatal(format!("batch task panicked: {err}")));

        stats.rows_attempted += result.rows_attempted;
        for rejection in &result.parse_rejected {
            eprintln!("row {}: {}", rejection.row_number, rejection.message);
            rejects
                .record_row(rejection.row_number, rejection.record.as_ref(), &rejection.message)
                .unwrap_or_else(|err| fatal(err));
        }
        stats.rows_rejected += result.parse_rejected.len();
        if args.stop_on_error && !result.parse_rejected.is_empty() {
            set_stop("aborted due to --stop-on-error", &mut stop_now, &mut stop_reason);
        }

        if result.parsed_count > 0 {
            match result.commit_result {
                Ok(()) => stats.rows_committed += result.parsed_count,
                Err(err) => {
                    eprintln!("batch {}: {} rows rejected by commit: {err}", result.batch_idx, result.parsed_count);
                    rejects
                        .record_batch_failure(&result.parsed_row_numbers, &result.parsed_records, result.batch_idx, &err)
                        .unwrap_or_else(|err| fatal(err));
                    stats.rows_rejected += result.parsed_count;
                    if args.stop_on_error {
                        set_stop("aborted due to --stop-on-error", &mut stop_now, &mut stop_reason);
                    }
                }
            }
        }

        if let Some(limit) = args.max_rejects {
            if stats.rows_rejected > limit {
                set_stop(
                    &format!("aborted: rejected rows ({}) exceeded --max-rejects {}", stats.rows_rejected, limit),
                    &mut stop_now,
                    &mut stop_reason,
                );
            }
        }

        print_progress(&stats, started, loader.bytes_position(), total_bytes);
    }

    rejects.flush().unwrap_or_else(|err| fatal(err));
    print_summary(&stats, started);
    if rejects.was_written() {
        println!("  Rejects CSV:    {}", rejects.csv_path().display());
        println!("  Rejects log:    {}", rejects.log_path().display());
    }
    if let Some(reason) = stop_reason {
        eprintln!("error: {reason}");
        exit(1);
    }
}

struct BatchResult {
    batch_idx: usize,
    rows_attempted: usize,
    parse_rejected: Vec<RowRejection>,
    parsed_row_numbers: Vec<usize>,
    parsed_records: Vec<StringRecord>,
    parsed_count: usize,
    commit_result: Result<(), String>,
}

async fn process_batch(
    driver: Arc<TypeDBDriver>,
    database: Arc<String>,
    query_text: Arc<String>,
    batch_idx: usize,
    batch: data::BatchOutcome,
) -> BatchResult {
    let parsed_count = batch.rows.len();
    let commit_result = if parsed_count > 0 {
        commit_batch(&driver, &database, &query_text, batch.rows).await
    } else {
        Ok(())
    };
    BatchResult {
        batch_idx,
        rows_attempted: batch.rows_attempted,
        parse_rejected: batch.rejected,
        parsed_row_numbers: batch.row_numbers,
        parsed_records: batch.records,
        parsed_count,
        commit_result,
    }
}

async fn commit_batch(
    driver: &TypeDBDriver,
    database: &str,
    query: &str,
    rows: Vec<QueryGivenRow>,
) -> Result<(), String> {
    let transaction = driver
        .transaction(database.to_owned(), TransactionType::Write)
        .await
        .map_err(|err| format!("opening write transaction on '{database}': {err}"))?;
    transaction
        .query_with_inputs(query, QueryGivenRows(rows))
        .await
        .map_err(|err| format!("query failed: {err}"))?;
    transaction.commit().await.map_err(|err| format!("commit failed: {err}"))?;
    Ok(())
}

fn set_stop(reason: &str, stop_now: &mut bool, stop_reason: &mut Option<String>) {
    *stop_now = true;
    if stop_reason.is_none() {
        *stop_reason = Some(reason.to_owned());
    }
}

fn parse_addresses(addresses: &str) -> Addresses {
    let split = addresses.split(',').map(str::to_string).collect::<Vec<_>>();
    Addresses::try_from_addresses_str(split)
        .unwrap_or_else(|err| fatal(format!("invalid addresses '{addresses}': {err}")))
}

fn fatal(message: impl AsRef<str>) -> ! {
    eprintln!("error: {}", message.as_ref());
    exit(1);
}
