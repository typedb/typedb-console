/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::path::Path;

use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
};

use crate::{
    ExitCode, fatal, fatal_with,
    params::ResolvedParams,
};

/// Parses the comma-separated address list, exiting with `UserInputError` on a malformed value.
pub(crate) fn parse_addresses(addresses: &str) -> Addresses {
    let split = addresses.split(',').map(str::to_string).collect::<Vec<_>>();
    Addresses::try_from_addresses_str(split)
        .unwrap_or_else(|err| fatal_with(ExitCode::UserInputError, format!("invalid addresses '{addresses}': {err}")))
}

/// Connects to the cluster using the params and password supplied, exiting with
/// `ConnectionError` on failure. TLS config is derived from `--tls-disabled` / `--tls-root-ca`.
pub(crate) async fn connect(resolved: &ResolvedParams, password: &str) -> TypeDBDriver {
    let addresses = parse_addresses(&resolved.addresses);
    let tls_config = if resolved.tls_disabled {
        DriverTlsConfig::disabled()
    } else if let Some(ca) = resolved.tls_root_ca.as_deref() {
        DriverTlsConfig::enabled_with_root_ca(Path::new(ca)).unwrap()
    } else {
        DriverTlsConfig::enabled_with_native_root_ca()
    };
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
