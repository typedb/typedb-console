/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    collections::HashSet,
    fs::read_to_string,
    io::{self, BufRead, Write},
    path::{Path, PathBuf},
    process::exit,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Instant,
};

use clap::Parser;
use csv::StringRecord;
use futures::stream::{FuturesUnordered, StreamExt};
use tokio::task::JoinHandle;
use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
    transaction::{QueryGivenRow, QueryGivenRows},
};

use crate::{
    checkpoint::{
        Checkpoint, CheckpointParams, CheckpointWriter, Hashes, InFlightBatch,
        default_checkpoint_path, hash_file, hash_string,
    },
    cli::{Args, USERNAME_VALUE_NAME},
    data::{CsvLoader, RowRejection},
    progress::{LoadStats, print_progress, print_summary},
    query::parse_query_inputs,
    rejects::{RejectsWriter, default_rejects_path},
};

mod checkpoint;
mod cli;
mod data;
mod progress;
mod query;
mod rejects;

struct ResolvedParams {
    query: String,
    database: String,
    data: String,
    header: bool,
    null_values: Vec<String>,
    max_rows: Option<usize>,
    batch_rows: usize,
    parallel_batches: usize,
    rejects_file: Option<String>,
    rejects_log: Option<String>,
    stop_on_error: bool,
    max_rejects: Option<usize>,
    schema_file: Option<String>,
    create_db: bool,
    addresses: String,
    username: String,
    tls_disabled: bool,
    tls_root_ca: Option<String>,
}

impl ResolvedParams {
    fn to_checkpoint_params(&self) -> CheckpointParams {
        CheckpointParams {
            query: self.query.clone(),
            database: self.database.clone(),
            data: self.data.clone(),
            header: self.header,
            null_values: self.null_values.clone(),
            max_rows: self.max_rows,
            batch_rows: self.batch_rows,
            parallel_batches: self.parallel_batches,
            rejects_file: self.rejects_file.clone(),
            rejects_log: self.rejects_log.clone(),
            stop_on_error: self.stop_on_error,
            max_rejects: self.max_rejects,
            schema_file: self.schema_file.clone(),
            create_db: self.create_db,
            addresses: self.addresses.clone(),
            username: self.username.clone(),
            tls_disabled: self.tls_disabled,
            tls_root_ca: self.tls_root_ca.clone(),
        }
    }
}

#[tokio::main]
async fn main() {
    let args = Args::parse();

    // Install a Ctrl+C handler that requests graceful shutdown on the first interrupt and
    // hard-exits on the second. The main loop polls `shutdown` between batches and drains
    // anything in flight before terminating, so the final checkpoint reflects what really happened.
    let shutdown = Arc::new(AtomicBool::new(false));
    let shutdown_signal = shutdown.clone();
    tokio::spawn(async move {
        if tokio::signal::ctrl_c().await.is_err() {
            return;
        }
        shutdown_signal.store(true, Ordering::SeqCst);
        eprintln!("\nInterrupt received; finishing in-flight batches. Press Ctrl+C again to force exit.");
        if tokio::signal::ctrl_c().await.is_err() {
            return;
        }
        eprintln!("Force-exiting.");
        std::process::exit(130);
    });

    let resume_checkpoint: Option<Checkpoint> = match args.resume.as_deref() {
        Some(path) => Some(Checkpoint::load(Path::new(path)).unwrap_or_else(|err| fatal(err))),
        None => None,
    };
    let resuming = resume_checkpoint.is_some();
    if resuming && args.no_checkpoint {
        fatal_with(ExitCode::UserInputError, "--no-checkpoint cannot be combined with --resume");
    }

    let resolved = resolve_params(&args, resume_checkpoint.as_ref().map(|c| &c.params))
        .unwrap_or_else(|err| fatal_with(ExitCode::UserInputError, err));
    if resolved.batch_rows == 0 {
        fatal_with(ExitCode::UserInputError, "--batch-rows must be greater than 0");
    }
    if resolved.parallel_batches == 0 {
        fatal_with(ExitCode::UserInputError, "--parallel-batches must be greater than 0");
    }
    let password = args
        .password
        .clone()
        .unwrap_or_else(|| rpassword::prompt_password(format!("password for '{}': ", resolved.username)).unwrap());

    if resuming {
        if args.schema_file.is_some() {
            eprintln!("warning: --schema-file is ignored when resuming; the original schema query will not be re-run");
        }
        if args.create_db.unwrap_or(false) {
            eprintln!("warning: --create-db is ignored when resuming; the database is assumed to exist");
        }
    }

    let query_text =
        read_to_string(&resolved.query).unwrap_or_else(|err| fatal(format!("failed to read query file '{}': {err}", resolved.query)));
    let schema_to_apply: Option<String> = if resuming {
        None
    } else {
        resolved.schema_file.as_deref().map(|path| {
            read_to_string(path).unwrap_or_else(|err| fatal(format!("failed to read schema file '{path}': {err}")))
        })
    };

    let inputs = parse_query_inputs(&query_text).unwrap_or_else(|err| fatal(err));

    let addresses = parse_addresses(&resolved.addresses);
    let tls_config = if resolved.tls_disabled {
        DriverTlsConfig::disabled()
    } else if let Some(ca) = resolved.tls_root_ca.as_deref() {
        DriverTlsConfig::enabled_with_root_ca(Path::new(ca)).unwrap()
    } else {
        DriverTlsConfig::enabled_with_native_root_ca()
    };
    let driver = TypeDBDriver::new(addresses, Credentials::new(&resolved.username, &password), DriverOptions::new(tls_config))
        .await
        .unwrap_or_else(|err| fatal_with(ExitCode::ConnectionError, format!("failed to connect to TypeDB: {err}")));

    if !resuming && resolved.create_db {
        let exists = driver
            .databases()
            .contains(resolved.database.clone())
            .await
            .unwrap_or_else(|err| fatal(format!("failed to check if database '{}' exists: {err}", resolved.database)));
        if !exists {
            driver
                .databases()
                .create(resolved.database.clone())
                .await
                .unwrap_or_else(|err| fatal(format!("failed to create database '{}': {err}", resolved.database)));
        }
    }

    if let Some(schema) = schema_to_apply {
        let schema_tx = driver
            .transaction(resolved.database.clone(), TransactionType::Schema)
            .await
            .unwrap_or_else(|err| fatal(format!("failed to open schema transaction on '{}': {err}", resolved.database)));
        schema_tx.query(schema).await.unwrap_or_else(|err| fatal(format!("schema query failed: {err}")));
        schema_tx.commit().await.unwrap_or_else(|err| fatal(format!("failed to commit schema transaction: {err}")));
    }

    let checkpoint_writer = if args.no_checkpoint {
        None
    } else {
        let path = if resuming {
            PathBuf::from(args.resume.as_deref().unwrap())
        } else {
            args.checkpoint_file
                .clone()
                .map(PathBuf::from)
                .unwrap_or_else(|| default_checkpoint_path(&resolved.data))
        };
        if !resuming && path.exists() {
            fatal_with(ExitCode::UserInputError, format!(
                "checkpoint file already exists at '{}': pass --resume to continue from it, --checkpoint-file PATH to write elsewhere, or --no-checkpoint to disable checkpointing",
                path.display()
            ));
        }
        Some(CheckpointWriter::new(path))
    };

    // Hashes are computed iff checkpointing is enabled; resume implies checkpointing, so any
    // resume path can rely on these being present.
    let hashes: Option<Hashes> = if checkpoint_writer.is_some() {
        println!("Hashing data file (first 64 MB)...");
        let data = hash_file(Path::new(&resolved.data)).unwrap_or_else(|err| fatal(err));
        println!("Fetching live schema for hashing...");
        let database = driver
            .databases()
            .get(resolved.database.clone())
            .await
            .unwrap_or_else(|err| fatal(format!("failed to look up database '{}': {err}", resolved.database)));
        let schema_text = database
            .schema()
            .await
            .unwrap_or_else(|err| fatal(format!("failed to fetch live schema: {err}")));
        Some(Hashes { query: hash_string(&query_text), data, schema: hash_string(&schema_text) })
    } else {
        None
    };

    // Validate against checkpoint and prompt for in-flight handling before initialising state.
    let skipped_in_flight: HashSet<usize> = if let Some(prior) = resume_checkpoint.as_ref() {
        let hashes = hashes.as_ref().expect("resume requires checkpointing, which always produces hashes");
        let warnings = resume_warnings(&resolved, prior, hashes);
        if !warnings.is_empty() {
            eprintln!("\nResume warnings:");
            for w in &warnings {
                eprintln!("  - {w}");
            }
            if !confirm("Continue anyway?") {
                fatal_with(ExitCode::UserInputError, "aborted: resume cancelled by user");
            }
        }
        resolve_in_flight_skips(&prior.in_flight)
    } else {
        HashSet::new()
    };

    let rejects_csv_path = resolved
        .rejects_file
        .as_deref()
        .map(PathBuf::from)
        .unwrap_or_else(|| default_rejects_path(&resolved.data, "csv"));
    let rejects_log_path = resolved
        .rejects_log
        .as_deref()
        .map(PathBuf::from)
        .unwrap_or_else(|| default_rejects_path(&resolved.data, "log"));

    let mut state = match resume_checkpoint {
        Some(mut prior) => {
            // Update hashes to the freshly computed values so the checkpoint stays in sync with
            // the actual data, schema, and query going forward.
            if let Some(hashes) = hashes.as_ref() {
                prior.set_hashes(hashes.clone());
            }
            // Apply user skip decisions before any batches are read.
            for &idx in &skipped_in_flight {
                prior.mark_in_flight_as_skipped(idx);
            }
            prior
        }
        None => Checkpoint::new(resolved.to_checkpoint_params(), hashes.clone().unwrap_or_default()),
    };

    let seek_byte_offset = state.watermark_bytes;
    let mut next_batch_idx = state.watermark + 1;
    // Batches the prior run already completed (out-of-order) -- read past them but don't dispatch.
    let completed_above_watermark: HashSet<usize> =
        state.completed_above_watermark.iter().map(|c| c.batch_idx).collect();

    let mut loader = if seek_byte_offset > 0 {
        CsvLoader::resume_at(
            &resolved.data,
            resolved.header,
            inputs,
            resolved.null_values.clone(),
            resolved.max_rows.map(|m| m.saturating_sub(state.watermark * resolved.batch_rows)),
            seek_byte_offset,
        )
        .unwrap_or_else(|err| fatal(format!("failed to resume data file '{}': {err}", resolved.data)))
    } else {
        CsvLoader::open(
            &resolved.data,
            resolved.header,
            inputs,
            resolved.null_values.clone(),
            resolved.max_rows,
        )
        .unwrap_or_else(|err| fatal(format!("failed to open data file '{}': {err}", resolved.data)))
    };

    let mut rejects = if resuming {
        RejectsWriter::new_append(rejects_csv_path, rejects_log_path, loader.headers().cloned())
    } else {
        RejectsWriter::new(rejects_csv_path, rejects_log_path, loader.headers().cloned())
    };

    let total_bytes = loader.file_size();

    let driver = Arc::new(driver);
    let database: Arc<str> = Arc::from(resolved.database.as_str());
    let query_text: Arc<str> = Arc::from(query_text);

    let mut stats = LoadStats::default();
    let started = Instant::now();
    let mut stop = Stop::default();
    let mut producing = true;
    let mut in_flight: FuturesUnordered<JoinHandle<BatchResult>> = FuturesUnordered::new();

    // Persist the initial state so a fresh run leaves a checkpoint file even before the first
    // batch finishes.
    if let Some(writer) = &checkpoint_writer {
        writer.write(&state).unwrap_or_else(|err| fatal(err));
    }

    loop {
        if !stop.requested() && shutdown.load(Ordering::SeqCst) {
            stop.request("aborted: interrupted by user");
        }
        while producing && !stop.requested() && in_flight.len() < resolved.parallel_batches {
            let batch = match loader.next_batch(resolved.batch_rows) {
                Some(b) => b,
                None => {
                    producing = false;
                    break;
                }
            };
            let batch_idx = next_batch_idx;
            next_batch_idx += 1;
            // Already-completed batches from a prior run: read past them to keep the cursor aligned.
            // (User-skipped in-flight batches were merged into completed_above_watermark above.)
            if completed_above_watermark.contains(&batch_idx) {
                continue;
            }

            let first_row = batch.first_row.clone().unwrap_or_default();
            let byte_end = batch.byte_end;
            state.record_dispatch(InFlightBatch { batch_idx, byte_end, first_row });
            if let Some(writer) = &checkpoint_writer {
                writer.write(&state).unwrap_or_else(|err| fatal(err));
            }

            let driver = driver.clone();
            let database = database.clone();
            let query_text = query_text.clone();
            in_flight.push(tokio::spawn(async move {
                process_batch(driver, database, query_text, batch_idx, batch).await
            }));
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
        if resolved.stop_on_error && !result.parse_rejected.is_empty() {
            stop.request("aborted due to --stop-on-error");
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
                    if resolved.stop_on_error {
                        stop.request("aborted due to --stop-on-error");
                    }
                }
            }
        }

        // Record the finish in the checkpoint state regardless of success/failure -- the row data
        // has been recorded one way or another (committed, or written to the rejects file).
        state.record_finish(result.batch_idx);
        if let Some(writer) = &checkpoint_writer {
            writer.write(&state).unwrap_or_else(|err| fatal(err));
        }

        if let Some(limit) = resolved.max_rejects {
            if stats.rows_rejected > limit {
                stop.request(&format!(
                    "aborted: rejected rows ({}) exceeded --max-rejects {}",
                    stats.rows_rejected, limit
                ));
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
    if let Some(writer) = &checkpoint_writer {
        println!("  Checkpoint:     {}", writer.path().display());
    }
    if let Some(reason) = stop.into_reason() {
        eprintln!("error: {reason}");
        exit(1);
    }
}

/// Tracks a one-shot stop request: the first reason wins, so messages produced by later
/// stop conditions don't overwrite the trigger that started the drain.
#[derive(Default)]
struct Stop {
    reason: Option<String>,
}

impl Stop {
    fn requested(&self) -> bool {
        self.reason.is_some()
    }

    fn request(&mut self, reason: &str) {
        if self.reason.is_none() {
            self.reason = Some(reason.to_owned());
        }
    }

    fn into_reason(self) -> Option<String> {
        self.reason
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
    database: Arc<str>,
    query_text: Arc<str>,
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

fn parse_addresses(addresses: &str) -> Addresses {
    let split = addresses.split(',').map(str::to_string).collect::<Vec<_>>();
    Addresses::try_from_addresses_str(split)
        .unwrap_or_else(|err| fatal_with(ExitCode::UserInputError, format!("invalid addresses '{addresses}': {err}")))
}

#[derive(Debug, Copy, Clone)]
enum ExitCode {
    GeneralError = 1,
    UserInputError = 2,
    ConnectionError = 3,
}

fn fatal(message: impl AsRef<str>) -> ! {
    fatal_with(ExitCode::GeneralError, message)
}

fn fatal_with(code: ExitCode, message: impl AsRef<str>) -> ! {
    eprintln!("error: {}", message.as_ref());
    exit(code as i32);
}

/// Folds a `--no-X` opt-out flag into the value supplied by its companion `--X`. clap's
/// `conflicts_with` ensures both can't be set at once, so the `no_x = true` case wins iff `flag`
/// is `None`.
fn merge_no_flag(flag: Option<bool>, no_flag: bool) -> Option<bool> {
    flag.or(if no_flag { Some(false) } else { None })
}

fn resolve_params(args: &Args, checkpoint: Option<&CheckpointParams>) -> Result<ResolvedParams, String> {
    // Hard-error if --batch-rows is provided on resume with a different value.
    if let (Some(cli_batch), Some(prior)) = (args.batch_rows, checkpoint) {
        if cli_batch != prior.batch_rows {
            return Err(format!(
                "--batch-rows ({cli_batch}) differs from the checkpoint ({}); changing --batch-rows is not supported on resume",
                prior.batch_rows
            ));
        }
    }

    let pick_string = |cli: &Option<String>, prior: Option<&String>, name: &str| -> Result<String, String> {
        match cli.as_ref().or(prior) {
            Some(v) => Ok(v.clone()),
            None => Err(format!("--{name} is required")),
        }
    };
    let pick_optional_string = |cli: &Option<String>, prior: Option<&Option<String>>| -> Option<String> {
        cli.clone().or_else(|| prior.cloned().flatten())
    };
    let pick_bool = |cli: Option<bool>, prior: Option<bool>, default: bool| -> bool {
        cli.or(prior).unwrap_or(default)
    };
    let pick_usize = |cli: Option<usize>, prior: Option<usize>, default: usize| -> usize {
        cli.or(prior).unwrap_or(default)
    };
    let pick_opt_usize = |cli: Option<usize>, prior: Option<Option<usize>>| -> Option<usize> {
        cli.or_else(|| prior.flatten())
    };
    let pick_vec = |cli: &Option<Vec<String>>, prior: Option<&Vec<String>>| -> Vec<String> {
        cli.clone().or_else(|| prior.cloned()).unwrap_or_default()
    };

    let resolved = ResolvedParams {
        query: pick_string(&args.query, checkpoint.map(|c| &c.query), "query")?,
        database: pick_string(&args.database, checkpoint.map(|c| &c.database), "database")?,
        data: pick_string(&args.data, checkpoint.map(|c| &c.data), "data")?,
        header: pick_bool(merge_no_flag(args.header, args.no_header), checkpoint.map(|c| c.header), false),
        null_values: pick_vec(&args.null_values, checkpoint.map(|c| &c.null_values)),
        max_rows: pick_opt_usize(args.max_rows, checkpoint.map(|c| c.max_rows)),
        batch_rows: pick_usize(args.batch_rows, checkpoint.map(|c| c.batch_rows), 1000),
        parallel_batches: pick_usize(args.parallel_batches, checkpoint.map(|c| c.parallel_batches), 1),
        rejects_file: pick_optional_string(&args.rejects_file, checkpoint.map(|c| &c.rejects_file)),
        rejects_log: pick_optional_string(&args.rejects_log, checkpoint.map(|c| &c.rejects_log)),
        stop_on_error: pick_bool(
            merge_no_flag(args.stop_on_error, args.no_stop_on_error),
            checkpoint.map(|c| c.stop_on_error),
            false,
        ),
        max_rejects: pick_opt_usize(args.max_rejects, checkpoint.map(|c| c.max_rejects)),
        schema_file: pick_optional_string(&args.schema_file, checkpoint.map(|c| &c.schema_file)),
        create_db: pick_bool(args.create_db, checkpoint.map(|c| c.create_db), false),
        addresses: pick_string(&args.addresses, checkpoint.map(|c| &c.addresses), "address")?,
        username: pick_string(
            &args.username,
            checkpoint.map(|c| &c.username),
            USERNAME_VALUE_NAME,
        )?,
        tls_disabled: pick_bool(args.tls_disabled, checkpoint.map(|c| c.tls_disabled), false),
        tls_root_ca: pick_optional_string(&args.tls_root_ca, checkpoint.map(|c| &c.tls_root_ca)),
    };
    Ok(resolved)
}

/// Collects all the ways the current run's params or hashes diverge from the checkpoint. The
/// caller decides how to present them — this function is pure and easily testable.
fn resume_warnings(resolved: &ResolvedParams, prior: &Checkpoint, hashes: &Hashes) -> Vec<String> {
    let mut warnings: Vec<String> = Vec::new();

    if resolved.header != prior.params.header {
        warnings.push(format!(
            "--header changed since checkpoint ({} -> {}); CSV column interpretation may differ",
            prior.params.header, resolved.header
        ));
    }
    if resolved.null_values != prior.params.null_values {
        warnings.push(format!(
            "--null-values changed since checkpoint ({:?} -> {:?}); cell interpretation may differ",
            prior.params.null_values, resolved.null_values
        ));
    }
    if resolved.data != prior.params.data {
        warnings.push(format!(
            "--data path changed since checkpoint ('{}' -> '{}')",
            prior.params.data, resolved.data
        ));
    }
    if hashes.data != prior.hashes.data {
        warnings.push(format!(
            "data file hash mismatch: checkpoint expected {}, actual {}",
            prior.hashes.data, hashes.data
        ));
    }
    if hashes.schema != prior.hashes.schema {
        warnings.push(format!(
            "live TypeDB schema hash mismatch: checkpoint expected {}, actual {}",
            prior.hashes.schema, hashes.schema
        ));
    }
    if resolved.query != prior.params.query {
        warnings.push(format!(
            "--query path changed since checkpoint ('{}' -> '{}')",
            prior.params.query, resolved.query
        ));
    }
    if hashes.query != prior.hashes.query {
        warnings.push(format!(
            "query file content hash mismatch: checkpoint expected {}, actual {}",
            prior.hashes.query, hashes.query
        ));
    }
    if resolved.database != prior.params.database {
        warnings.push(format!(
            "--database changed since checkpoint ('{}' -> '{}')",
            prior.params.database, resolved.database
        ));
    }

    warnings
}

enum InFlightMode {
    ReprocessAll,
    SkipAll,
    DecideEach,
}

/// Returns the batch indices the user chose to skip (treat as already committed). Indices
/// not returned should be reprocessed.
fn resolve_in_flight_skips(in_flight: &[InFlightBatch]) -> HashSet<usize> {
    if in_flight.is_empty() {
        return HashSet::new();
    }
    eprintln!("\nThe checkpoint records {} in-flight batch(es) from the previous run.", in_flight.len());
    eprintln!("These batches were dispatched but never confirmed as committed. Verify them against the database before deciding.");
    for batch in in_flight {
        eprintln!("  - batch {} (first row: {})", batch.batch_idx, format_first_row(&batch.first_row));
    }
    eprintln!("\nOptions: [a]ll = reprocess all, [s]kip all = treat as already committed, [d]ecide each (default: all)");
    let choice = prompt("Choose action").trim().to_ascii_lowercase();
    let mode = match choice.as_str() {
        "" | "a" | "all" => InFlightMode::ReprocessAll,
        "s" | "skip" | "skip all" => InFlightMode::SkipAll,
        "d" | "each" | "decide" => InFlightMode::DecideEach,
        other => {
            eprintln!("Unknown choice '{other}', defaulting to reprocess all.");
            InFlightMode::ReprocessAll
        }
    };
    match mode {
        InFlightMode::ReprocessAll => HashSet::new(),
        InFlightMode::SkipAll => in_flight.iter().map(|b| b.batch_idx).collect(),
        InFlightMode::DecideEach => in_flight
            .iter()
            .filter(|batch| {
                let q = format!(
                    "Reprocess batch {} (first row: {})?",
                    batch.batch_idx,
                    format_first_row(&batch.first_row)
                );
                !confirm(&q)
            })
            .map(|batch| batch.batch_idx)
            .collect(),
    }
}

fn format_first_row(row: &[String]) -> String {
    if row.is_empty() {
        "<empty>".to_owned()
    } else {
        row.join(",")
    }
}

fn confirm(question: &str) -> bool {
    loop {
        let answer = prompt(&format!("{question} [y/N]")).trim().to_ascii_lowercase();
        match answer.as_str() {
            "y" | "yes" => return true,
            "" | "n" | "no" => return false,
            other => eprintln!("Please answer 'y' or 'n' (got '{other}')."),
        }
    }
}

fn prompt(message: &str) -> String {
    eprint!("{message}: ");
    let _ = io::stderr().flush();
    let mut line = String::new();
    let stdin = io::stdin();
    let mut handle = stdin.lock();
    let _ = handle.read_line(&mut line);
    line
}
