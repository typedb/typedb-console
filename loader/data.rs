/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{collections::HashMap, str::FromStr};

use chrono::{NaiveDate, NaiveDateTime};
use typedb_driver::{
    concept::{
        Value,
        value::{Decimal, Duration},
    },
    transaction::{QueryGivenEntry, QueryGivenRow, QueryGivenRows},
};

use crate::query::{CellType, GivenInput};

pub(crate) fn read_csv_rows(
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
            let cell = record
                .get(col)
                .ok_or_else(|| format!("CSV row {} missing column {} for input '${}'", row_idx + 1, col, input.name))?;
            entries.push(
                parse_cell(cell, input, null_values)
                    .map_err(|err| format!("CSV row {} column '${}': {err}", row_idx + 1, input.name))?,
            );
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
        CellType::DatetimeTz => {
            return Err(format!("datetime-tz inputs (e.g. '${}') are not yet supported by the loader", input.name));
        }
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
