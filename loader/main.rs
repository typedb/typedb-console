/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{collections::HashMap, fs::read_to_string, path::Path, process::exit, str::FromStr};

use chrono::{NaiveDate, NaiveDateTime};
use clap::Parser;
use typedb_driver::{
    Addresses, Credentials, DriverOptions, DriverTlsConfig, TransactionType, TypeDBDriver,
    concept::{
        Value,
        value::{Decimal, Duration},
    },
    transaction::{QueryGivenEntry, QueryGivenRow, QueryGivenRows},
};
use typeql::{
    Variable,
    query::{
        QueryStructure,
        pipeline::stage::{Stage, given::Given},
    },
    schema::definable::function::Argument,
    type_::{NamedType, NamedTypeAny},
};

use crate::cli::{Args, USERNAME_VALUE_NAME};

mod cli;

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

    let inputs = parse_query_inputs(&query);
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

#[derive(Debug, Clone, Copy)]
enum CellType {
    Boolean,
    Integer,
    Double,
    Decimal,
    String,
    Date,
    Datetime,
    DatetimeTz,
    Duration,
}

#[derive(Debug, Clone)]
struct GivenInput {
    name: String,
    cell_type: CellType,
    optional: bool,
}

fn parse_query_inputs(query_text: &str) -> Vec<GivenInput> {
    let parsed = typeql::parse_query(query_text).unwrap_or_else(|err| fatal(format!("failed to parse query: {err}")));
    let pipeline = match parsed.into_structure() {
        QueryStructure::Pipeline(p) => p,
        QueryStructure::Schema(_) => fatal("schema queries do not accept input rows"),
    };
    let given = pipeline.stages.into_iter().find_map(|stage| match stage {
        Stage::Given(given) => Some(given),
        _ => None,
    });
    let Given { variables, .. } = given.unwrap_or_else(|| fatal("query has no `given` stage; cannot bind input rows"));
    variables.into_iter().map(into_given_input).collect()
}

fn into_given_input(arg: Argument) -> GivenInput {
    let Argument { var, type_, .. } = arg;
    let name = match var {
        Variable::Named { ident, .. } => ident.as_str_unchecked().to_owned(),
        Variable::Anonymous { .. } => fatal("anonymous variables are not supported in `given` inputs"),
    };
    let (named_type, optional) = match type_ {
        NamedTypeAny::Simple(named) => (named, false),
        NamedTypeAny::Optional(opt) => (opt.inner, true),
        NamedTypeAny::List(_) => fatal(format!("list-typed `given` input '${name}' is not supported")),
    };
    let token = match named_type {
        NamedType::BuiltinValueType(t) => t.token,
        NamedType::Label(label) => fatal(format!(
            "`given` input '${name}' has type '{label}'; only built-in value types can be loaded from CSV"
        )),
    };
    let cell_type = match token {
        typeql::token::ValueType::Boolean => CellType::Boolean,
        typeql::token::ValueType::Integer => CellType::Integer,
        typeql::token::ValueType::Double => CellType::Double,
        typeql::token::ValueType::Decimal => CellType::Decimal,
        typeql::token::ValueType::String => CellType::String,
        typeql::token::ValueType::Date => CellType::Date,
        typeql::token::ValueType::DateTime => CellType::Datetime,
        typeql::token::ValueType::DateTimeTZ => CellType::DatetimeTz,
        typeql::token::ValueType::Duration => CellType::Duration,
    };
    GivenInput { name, cell_type, optional }
}

fn read_csv_rows(
    path: &str,
    has_header: bool,
    inputs: &[GivenInput],
    null_values: &[String],
    max_rows: Option<usize>,
) -> Result<QueryGivenRows, String> {
    let mut reader = csv::ReaderBuilder::new()
        .has_headers(has_header)
        .from_path(path)
        .map_err(|err| format!("opening CSV: {err}"))?;

    let column_indices: Vec<usize> = if has_header {
        let headers = reader.headers().map_err(|err| format!("reading CSV headers: {err}"))?;
        let header_index: HashMap<&str, usize> =
            headers.iter().enumerate().map(|(idx, name)| (name, idx)).collect();
        inputs
            .iter()
            .map(|input| {
                header_index
                    .get(input.name.as_str())
                    .copied()
                    .ok_or_else(|| format!("CSV header missing column '{}' required by query", input.name))
            })
            .collect::<Result<_, _>>()?
    } else {
        (0..inputs.len()).collect()
    };

    let mut rows = Vec::new();
    let row_limit = max_rows.unwrap_or(usize::MAX);
    for (row_idx, record) in reader.records().take(row_limit).enumerate() {
        let record = record.map_err(|err| format!("reading CSV row {}: {err}", row_idx + 1))?;
        let mut entries = Vec::with_capacity(inputs.len());
        for (input, &col) in inputs.iter().zip(&column_indices) {
            let cell = record.get(col).ok_or_else(|| {
                format!("CSV row {} missing column {} for input '${}'", row_idx + 1, col, input.name)
            })?;
            entries.push(parse_cell(cell, input, null_values).map_err(|err| {
                format!("CSV row {} column '${}': {err}", row_idx + 1, input.name)
            })?);
        }
        rows.push(QueryGivenRow(entries));
    }
    Ok(QueryGivenRows(rows))
}

fn parse_cell(cell: &str, input: &GivenInput, null_values: &[String]) -> Result<QueryGivenEntry, String> {
    let is_null = if null_values.is_empty() { cell.is_empty() } else { null_values.iter().any(|v| v == cell) };
    if is_null {
        return if input.optional {
            Ok(QueryGivenEntry::Empty)
        } else {
            Err("null value in non-optional column".to_owned())
        };
    }
    let value = match input.cell_type {
        CellType::Boolean => Value::Boolean(parse_bool(cell)?),
        CellType::Integer => Value::Integer(cell.parse::<i64>().map_err(|err| format!("invalid integer: {err}"))?),
        CellType::Double => Value::Double(cell.parse::<f64>().map_err(|err| format!("invalid double: {err}"))?),
        CellType::Decimal => Value::Decimal(parse_decimal(cell)?),
        CellType::String => Value::String(cell.to_owned()),
        CellType::Date => Value::Date(
            NaiveDate::parse_from_str(cell, "%Y-%m-%d").map_err(|err| format!("invalid date (YYYY-MM-DD): {err}"))?,
        ),
        CellType::Datetime => Value::Datetime(parse_naive_datetime(cell)?),
        CellType::DatetimeTz => fatal(format!(
            "datetime-tz inputs (e.g. '${}') are not yet supported by the loader",
            input.name
        )),
        CellType::Duration => {
            Value::Duration(Duration::from_str(cell).map_err(|_| format!("invalid ISO-8601 duration: '{cell}'"))?)
        }
    };
    Ok(QueryGivenEntry::Value(value))
}

fn parse_bool(cell: &str) -> Result<bool, String> {
    match cell.to_ascii_lowercase().as_str() {
        "true" => Ok(true),
        "false" => Ok(false),
        _ => Err(format!("invalid boolean (expected 'true' or 'false'): '{cell}'")),
    }
}

fn parse_decimal(cell: &str) -> Result<Decimal, String> {
    let (sign, body) = match cell.strip_prefix('-') {
        Some(rest) => (-1i128, rest),
        None => (1i128, cell.strip_prefix('+').unwrap_or(cell)),
    };
    let (integer_part, fractional_part) = match body.split_once('.') {
        Some((i, f)) => (i, f),
        None => (body, ""),
    };
    if integer_part.is_empty() && fractional_part.is_empty() {
        return Err(format!("invalid decimal: '{cell}'"));
    }
    let integer: i128 = if integer_part.is_empty() {
        0
    } else {
        integer_part.parse::<i128>().map_err(|err| format!("invalid decimal integer part: {err}"))?
    };
    let denom_log10 = Decimal::FRACTIONAL_PART_DENOMINATOR.ilog10() as usize;
    if fractional_part.len() > denom_log10 {
        return Err(format!("decimal fractional part exceeds {denom_log10} digits: '{cell}'"));
    }
    let fractional_raw: u128 = if fractional_part.is_empty() {
        0
    } else {
        fractional_part.parse::<u128>().map_err(|err| format!("invalid decimal fractional part: {err}"))?
    };
    let scale = 10u128.pow((denom_log10 - fractional_part.len()) as u32);
    let signed = sign * (integer * Decimal::FRACTIONAL_PART_DENOMINATOR as i128 + (fractional_raw * scale) as i128);
    let integer_out = (signed / Decimal::FRACTIONAL_PART_DENOMINATOR as i128) as i64;
    let fractional_out = signed.rem_euclid(Decimal::FRACTIONAL_PART_DENOMINATOR as i128) as u64;
    Ok(Decimal::new(integer_out, fractional_out))
}

fn parse_naive_datetime(cell: &str) -> Result<NaiveDateTime, String> {
    NaiveDateTime::parse_from_str(cell, "%Y-%m-%dT%H:%M:%S%.f")
        .or_else(|_| NaiveDateTime::parse_from_str(cell, "%Y-%m-%dT%H:%M:%S"))
        .or_else(|_| NaiveDateTime::parse_from_str(cell, "%Y-%m-%d %H:%M:%S"))
        .map_err(|err| format!("invalid datetime (expected ISO-8601): {err}"))
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
