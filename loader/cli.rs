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
    pub query: String,

    /// Name of the database to load data into.
    #[arg(long, value_name = "database")]
    pub database: String,

    /// Path to the data file to load.
    /// File path can be absolute or relative to the current directory.
    #[arg(long, value_name = "path to data file (.csv)")]
    pub data: String,

    /// Whether the data file contains a header row.
    #[arg(long = "header", default_value = "false")]
    pub header: bool,

    /// Strings in the data file to treat as null/empty values. May be repeated.
    /// If not provided, only empty strings are treated as null.
    #[arg(long = "null-values", value_name = "value")]
    pub null_values: Vec<String>,

    /// Process at most this many data rows from the CSV. If not provided, all rows are processed.
    #[arg(long = "max-rows", value_name = "n")]
    pub max_rows: Option<usize>,

    /// Number of rows submitted in each `query_with_inputs` invocation. Each batch is committed
    /// in its own write transaction.
    #[arg(long = "batch-rows", value_name = "n", default_value = "1000")]
    pub batch_rows: usize,

    /// Maximum number of batches submitted concurrently to the server.
    /// 1 means strictly sequential (default).
    #[arg(long = "parallel-batches", value_name = "n", default_value = "1")]
    pub parallel_batches: usize,

    /// Path to write rejected rows to in CSV form.
    /// Defaults to `<data-file-stem>-rejects.csv` next to the data file.
    #[arg(long = "rejects-file", value_name = "path to rejects file (.csv)")]
    pub rejects_file: Option<String>,

    /// Path to write the per-rejection error log to.
    /// Defaults to `<data-file-stem>-rejects.log` next to the data file.
    #[arg(long = "rejects-log", value_name = "path to rejects log file")]
    pub rejects_log: Option<String>,

    /// Abort on the first row or batch error instead of skipping and continuing.
    /// The offending row(s) are still written to the rejects file before exit.
    #[arg(long = "stop-on-error", default_value = "false")]
    pub stop_on_error: bool,

    /// Abort once the total number of rejected rows exceeds this threshold.
    /// Applies independently of --stop-on-error.
    #[arg(long = "max-rejects", value_name = "n")]
    pub max_rejects: Option<usize>,

    /// Path to a TypeQL schema file to run in a schema transaction before data loading.
    #[arg(long = "schema-file", value_name = "path to schema file (.tql)")]
    pub schema_file: Option<String>,

    /// Create the database if it does not already exist.
    #[arg(long = "create-db", default_value = "false")]
    pub create_db: bool,

    /// TypeDB address(es) to connect to.
    /// Accepts either `--address host:port` or `--addresses host1:port1,host2:port2,host3:port3`
    #[arg(long = "address", alias = "addresses", value_name = "host:port[,host:port]")]
    pub addresses: String,

    /// Username for authentication.
    #[arg(long, value_name = USERNAME_VALUE_NAME)]
    pub username: Option<String>,

    /// Password for authentication. Will be requested safely by default.
    #[arg(long, value_name = "password")]
    pub password: Option<String>,

    /// Connect to TypeDB with TLS encryption. Disable with caution.
    #[arg(long = "tls-disabled", default_value = "false")]
    pub tls_disabled: bool,

    /// Path to the TLS encryption root CA file.
    #[arg(long = "tls-root-ca", value_name = "path")]
    pub tls_root_ca: Option<String>,
}
