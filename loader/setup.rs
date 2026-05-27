/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::path::Path;

use typedb_cli_common::{build_tls_config, parse_addresses};
use typedb_driver::{Credentials, DriverOptions, TransactionType, TypeDBDriver};

use crate::{ExitCode, fatal, fatal_with, params::ResolvedParams};

/// Connects to the cluster using the params and password supplied, exiting with
/// `ConnectionError` on failure. TLS config is derived from `--tls-disabled` / `--tls-root-ca`.
pub(crate) async fn connect(resolved: &ResolvedParams, password: &str) -> TypeDBDriver {
    let addresses =
        parse_addresses(&resolved.addresses).unwrap_or_else(|err| fatal_with(ExitCode::UserInputError, err));
    let tls_config = build_tls_config(resolved.tls_disabled, resolved.tls_root_ca.as_deref().map(Path::new))
        .unwrap_or_else(|err| fatal_with(ExitCode::UserInputError, err));
    TypeDBDriver::new(addresses, Credentials::new(&resolved.username, password), DriverOptions::new(tls_config))
        .await
        .unwrap_or_else(|err| fatal_with(ExitCode::ConnectionError, format!("failed to connect to TypeDB: {err}")))
}

/// Creates `database` if it does not already exist. No-op when it already exists.
pub(crate) async fn create_database_if_missing(driver: &TypeDBDriver, database: &str) {
    let exists = driver
        .databases()
        .contains(database.to_owned())
        .await
        .unwrap_or_else(|err| fatal(format!("failed to check if database '{database}' exists: {err}")));
    if !exists {
        driver
            .databases()
            .create(database.to_owned())
            .await
            .unwrap_or_else(|err| fatal(format!("failed to create database '{database}': {err}")));
    }
}

/// Runs the supplied schema text in a schema transaction and commits it.
pub(crate) async fn apply_schema(driver: &TypeDBDriver, database: &str, schema: String) {
    let schema_tx = driver
        .transaction(database.to_owned(), TransactionType::Schema)
        .await
        .unwrap_or_else(|err| fatal(format!("failed to open schema transaction on '{database}': {err}")));
    schema_tx.query(schema).await.unwrap_or_else(|err| fatal(format!("schema query failed: {err}")));
    schema_tx.commit().await.unwrap_or_else(|err| fatal(format!("failed to commit schema transaction: {err}")));
}
