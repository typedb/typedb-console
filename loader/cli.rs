/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use clap::Parser;

pub const USERNAME_VALUE_NAME: &str = "username";

#[derive(Parser, Debug)]
#[command(author, about)]
pub struct Args {
    /// Path to a TypeQL query file used as the loading template.
    /// File path can be absolute or relative to the current directory.
    #[arg(long, value_name = "path to query file (.tql)")]
    pub query: Option<String>,

    /// Name of the database to load data into.
    #[arg(long, value_name = "database")]
    pub database: Option<String>,

    /// Path to the data file to load.
    /// File path can be absolute or relative to the current directory.
    #[arg(long, value_name = "path to data file (.csv)")]
    pub data: Option<String>,

    /// Whether the data file contains a header row. Default: false.
    #[arg(long = "header", num_args = 0..=1, default_missing_value = "true")]
    pub header: Option<bool>,

    /// Strings in the data file to treat as null/empty values. May be repeated.
    /// If not provided, only empty strings are treated as null.
    #[arg(long = "null-values", value_name = "value")]
    pub null_values: Option<Vec<String>>,

    /// Process at most this many data rows from the CSV. If not provided, all rows are processed.
    #[arg(long = "max-rows", value_name = "n")]
    pub max_rows: Option<usize>,

    /// Number of rows submitted in each `query_with_inputs` invocation. Each batch is committed
    /// in its own write transaction. Default: 1000.
    /// On resume, this must match the value stored in the checkpoint.
    #[arg(long = "batch-rows", value_name = "n")]
    pub batch_rows: Option<usize>,

    /// Maximum number of batches submitted concurrently to the server. Default: 1 (strictly
    /// sequential).
    #[arg(long = "parallel-batches", value_name = "n")]
    pub parallel_batches: Option<usize>,

    /// Path to write rejected rows to in CSV form.
    /// Defaults to `<data-file-stem>-rejects.csv` next to the data file.
    #[arg(long = "rejects-file", value_name = "path to rejects file (.csv)")]
    pub rejects_file: Option<String>,

    /// Path to write the per-rejection error log to.
    /// Defaults to `<data-file-stem>-rejects.log` next to the data file.
    #[arg(long = "rejects-log", value_name = "path to rejects log file")]
    pub rejects_log: Option<String>,

    /// Abort on the first row or batch error instead of skipping and continuing.
    /// The offending row(s) are still written to the rejects file before exit. Default: false.
    #[arg(long = "stop-on-error", num_args = 0..=1, default_missing_value = "true")]
    pub stop_on_error: Option<bool>,

    /// Abort once the total number of rejected rows exceeds this threshold.
    /// Applies independently of --stop-on-error.
    #[arg(long = "max-rejects", value_name = "n")]
    pub max_rejects: Option<usize>,

    /// Path to a TypeQL schema file to run in a schema transaction before data loading.
    /// Ignored (with a warning) when resuming.
    #[arg(long = "schema-file", value_name = "path to schema file (.tql)")]
    pub schema_file: Option<String>,

    /// Create the database if it does not already exist. Ignored (with a warning) when resuming.
    /// Default: false.
    #[arg(long = "create-db", num_args = 0..=1, default_missing_value = "true")]
    pub create_db: Option<bool>,

    /// TypeDB address(es) to connect to.
    /// Accepts either `--address host:port` or `--addresses host1:port1,host2:port2,host3:port3`
    #[arg(long = "address", alias = "addresses", value_name = "host:port[,host:port]")]
    pub addresses: Option<String>,

    /// Username for authentication.
    #[arg(long, value_name = USERNAME_VALUE_NAME)]
    pub username: Option<String>,

    /// Password for authentication. Will be requested safely by default.
    #[arg(long, value_name = "password")]
    pub password: Option<String>,

    /// Disable TLS encryption for the connection to TypeDB. Default: false (TLS enabled).
    /// Disable with caution: credentials and queries are sent in plaintext.
    #[arg(long = "tls-disabled", num_args = 0..=1, default_missing_value = "true")]
    pub tls_disabled: Option<bool>,

    /// Path to the TLS encryption root CA file.
    #[arg(long = "tls-root-ca", value_name = "path")]
    pub tls_root_ca: Option<String>,

    /// Path to the checkpoint file. Defaults to `<data-file-stem>-checkpoint.json`
    /// next to the data file.
    #[arg(long = "checkpoint-file", value_name = "path")]
    pub checkpoint_file: Option<String>,

    /// Disable checkpointing entirely. The loader will not write or maintain a checkpoint file.
    #[arg(long = "no-checkpoint", default_value = "false")]
    pub no_checkpoint: bool,

    /// Resume a previous run from the given checkpoint file. Parameters from the checkpoint are
    /// used unless overridden on the command line.
    #[arg(long = "resume", value_name = "path to checkpoint file")]
    pub resume: Option<String>,
}
