/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{fs::read_to_string, path::{Path, PathBuf}, process::exit, time::Instant};

use clap::Parser;
use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
    transaction::QueryGivenRows,
};

use crate::{
    cli::{Args, USERNAME_VALUE_NAME},
    data::CsvLoader,
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

    let query = read_to_string(&args.query)
        .unwrap_or_else(|err| fatal(format!("failed to read query file '{}': {err}", args.query)));
    let schema = args.schema_file.as_deref().map(|path| {
        read_to_string(path).unwrap_or_else(|err| fatal(format!("failed to read schema file '{path}': {err}")))
    });

    let inputs = parse_query_inputs(&query).unwrap_or_else(|err| fatal(err));
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

    let total_bytes = loader.file_size();
    let rejects_csv_path = args.rejects_file.map(PathBuf::from).unwrap_or_else(|| default_rejects_path(&args.data, "csv"));
    let rejects_log_path = args.rejects_log.map(PathBuf::from).unwrap_or_else(|| default_rejects_path(&args.data, "log"));
    let mut rejects = RejectsWriter::new(rejects_csv_path, rejects_log_path, loader.headers().cloned());

    let mut stats = LoadStats::default();
    let started = Instant::now();
    let mut batch_idx = 0usize;

    while let Some(batch) = loader.next_batch(args.batch_rows) {
        batch_idx += 1;
        stats.rows_attempted += batch.rows_attempted;
        for rejection in &batch.rejected {
            eprintln!("row {}: {}", rejection.row_number, rejection.message);
            rejects
                .record(rejection.row_number, rejection.record.as_ref(), &rejection.message)
                .unwrap_or_else(|err| fatal(err));
        }
        stats.rows_rejected += batch.rejected.len();

        let parsed_count = batch.rows.len();
        if parsed_count > 0 {
            match commit_batch(&driver, &args.database, &query, batch.rows).await {
                Ok(()) => stats.rows_committed += parsed_count,
                Err(err) => {
                    eprintln!("batch {batch_idx}: {parsed_count} rows rejected by commit: {err}");
                    let message = format!("batch {batch_idx} commit failed: {err}");
                    for (row_number, record) in batch.row_numbers.iter().zip(batch.records.iter()) {
                        rejects.record(*row_number, Some(record), &message).unwrap_or_else(|err| fatal(err));
                    }
                    stats.rows_rejected += parsed_count;
                }
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
}

async fn commit_batch(
    driver: &TypeDBDriver,
    database: &str,
    query: &str,
    rows: Vec<typedb_driver::transaction::QueryGivenRow>,
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

fn parse_addresses(addresses: &str) -> Addresses {
    let split = addresses.split(',').map(str::to_string).collect::<Vec<_>>();
    Addresses::try_from_addresses_str(split)
        .unwrap_or_else(|err| fatal(format!("invalid addresses '{addresses}': {err}")))
}

fn fatal(message: impl AsRef<str>) -> ! {
    eprintln!("error: {}", message.as_ref());
    exit(1);
}
