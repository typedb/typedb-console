/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{fs::read_to_string, path::Path, process::exit};

use clap::Parser;
use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
    concept::Value,
    transaction::{QueryGivenEntry, QueryGivenRow, QueryGivenRows},
};

use crate::cli::{Args, USERNAME_VALUE_NAME};

mod cli;

#[tokio::main]
async fn main() {
    let mut args = Args::parse();

    let username = args.username.take().unwrap_or_else(|| {
        eprintln!("error: username is required for connection authentication ('--{USERNAME_VALUE_NAME} <username>').");
        exit(1);
    });
    let password = args
        .password
        .take()
        .unwrap_or_else(|| rpassword::prompt_password(format!("password for '{username}': ")).unwrap());

    let query = read_to_string(&args.query)
        .unwrap_or_else(|err| fatal(format!("failed to read query file '{}': {err}", args.query)));

    let rows = read_csv_rows(&args.data, args.header)
        .unwrap_or_else(|err| fatal(format!("failed to read data file '{}': {err}", args.data)));

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

    let transaction = driver
        .transaction(args.database.clone(), TransactionType::Write)
        .await
        .unwrap_or_else(|err| fatal(format!("failed to open write transaction on '{}': {err}", args.database)));

    transaction
        .query_with_inputs(query, rows)
        .await
        .unwrap_or_else(|err| fatal(format!("query failed: {err}")));

    transaction.commit().await.unwrap_or_else(|err| fatal(format!("failed to commit: {err}")));
}

fn read_csv_rows(path: &str, header: bool) -> Result<QueryGivenRows, csv::Error> {
    let mut reader = csv::ReaderBuilder::new().has_headers(header).from_path(path)?;
    let mut rows = Vec::new();
    for record in reader.records() {
        let record = record?;
        let entries = record.iter().map(|cell| QueryGivenEntry::Value(Value::String(cell.to_owned()))).collect();
        rows.push(QueryGivenRow(entries));
    }
    Ok(QueryGivenRows(rows))
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
