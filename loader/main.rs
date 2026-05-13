/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{fs::read_to_string, path::Path, process::exit};

use clap::Parser;
use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
    transaction::QueryGivenRows,
};

use crate::{
    cli::{Args, USERNAME_VALUE_NAME},
    data::read_csv_rows,
    query::parse_query_inputs,
};

mod cli;
mod data;
mod query;

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
    let rows = read_csv_rows(&args.data, args.header, &inputs, &args.null_values, args.max_rows)
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
    let mut remaining = rows.0;
    let total = remaining.len();
    let mut loaded = 0usize;
    let mut batch_idx = 0usize;
    while !remaining.is_empty() {
        let take = remaining.len().min(args.batch_rows);
        let batch: Vec<_> = remaining.drain(..take).collect();
        batch_idx += 1;
        let transaction = driver.transaction(args.database.clone(), TransactionType::Write).await.unwrap_or_else(|err| {
            fatal(format!("failed to open write transaction on '{}': {err}", args.database))
        });
        transaction
            .query_with_inputs(&query, QueryGivenRows(batch))
            .await
            .unwrap_or_else(|err| fatal(format!("query failed on batch {batch_idx}: {err}")));
        transaction
            .commit()
            .await
            .unwrap_or_else(|err| fatal(format!("failed to commit batch {batch_idx}: {err}")));
        loaded += take;
        println!("Committed batch {batch_idx}: {loaded}/{total} rows.");
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
