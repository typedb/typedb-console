/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{collections::HashMap, fs::File, io::BufReader, str::FromStr};

use chrono::{NaiveDate, NaiveDateTime};
use csv::{Reader, StringRecord};
use typedb_driver::{
    concept::{
        Value,
        value::{Decimal, Duration},
    },
    transaction::{QueryGivenEntry, QueryGivenRow},
};

use crate::query::{CellType, GivenInput};

pub(crate) struct CsvLoader {
    reader: Reader<BufReader<File>>,
    headers: Option<StringRecord>,
    column_indices: Vec<usize>,
    expected_columns: usize,
    inputs: Vec<GivenInput>,
    null_values: Vec<String>,
    rows_read: usize,
    row_limit: usize,
    file_size: u64,
}

pub(crate) struct BatchOutcome {
    pub(crate) rows: Vec<QueryGivenRow>,
    pub(crate) records: Vec<StringRecord>,
    pub(crate) row_numbers: Vec<usize>,
    pub(crate) rows_attempted: usize,
    pub(crate) rejected: Vec<RowRejection>,
}

pub(crate) struct RowRejection {
    pub(crate) row_number: usize,
    pub(crate) record: Option<StringRecord>,
    pub(crate) message: String,
}

impl CsvLoader {
    pub(crate) fn open(
        path: &str,
        has_header: bool,
        inputs: Vec<GivenInput>,
        null_values: Vec<String>,
        max_rows: Option<usize>,
    ) -> Result<Self, String> {
        let file = File::open(path).map_err(|err| format!("opening CSV: {err}"))?;
        let file_size = file.metadata().map_err(|err| format!("reading CSV metadata: {err}"))?.len();
        let mut reader = csv::ReaderBuilder::new()
            .has_headers(has_header)
            .flexible(true)
            .from_reader(BufReader::new(file));

        let (headers, column_indices) = if has_header {
            let headers = reader.headers().map_err(|err| format!("reading CSV headers: {err}"))?.clone();
            let header_index: HashMap<&str, usize> =
                headers.iter().enumerate().map(|(idx, name)| (name, idx)).collect();
            let indices = inputs
                .iter()
                .map(|input| {
                    header_index
                        .get(input.name.as_str())
                        .copied()
                        .ok_or_else(|| format!("CSV header missing column '{}' required by query", input.name))
                })
                .collect::<Result<Vec<_>, _>>()?;
            (Some(headers), indices)
        } else {
            (None, (0..inputs.len()).collect())
        };

        let expected_columns = headers.as_ref().map(|h| h.len()).unwrap_or(inputs.len());
        Ok(Self {
            reader,
            headers,
            column_indices,
            expected_columns,
            inputs,
            null_values,
            rows_read: 0,
            row_limit: max_rows.unwrap_or(usize::MAX),
            file_size,
        })
    }

    pub(crate) fn file_size(&self) -> u64 {
        self.file_size
    }

    pub(crate) fn bytes_position(&self) -> u64 {
        self.reader.position().byte()
    }

    pub(crate) fn headers(&self) -> Option<&StringRecord> {
        self.headers.as_ref()
    }

    pub(crate) fn next_batch(&mut self, batch_size: usize) -> Option<BatchOutcome> {
        if self.rows_read >= self.row_limit {
            return None;
        }
        let mut rows = Vec::with_capacity(batch_size);
        let mut records = Vec::with_capacity(batch_size);
        let mut row_numbers = Vec::with_capacity(batch_size);
        let mut rejected = Vec::new();
        let mut attempted = 0usize;
        while attempted < batch_size && self.rows_read < self.row_limit {
            let mut record = StringRecord::new();
            match self.reader.read_record(&mut record) {
                Ok(true) => {}
                Ok(false) => break,
                Err(err) => {
                    self.rows_read += 1;
                    attempted += 1;
                    rejected.push(RowRejection {
                        row_number: self.rows_read,
                        record: None,
                        message: format!("CSV: {err}"),
                    });
                    continue;
                }
            }
            self.rows_read += 1;
            attempted += 1;
            let row_number = self.rows_read;
            if record.len() != self.expected_columns {
                let actual = record.len();
                rejected.push(RowRejection {
                    row_number,
                    record: Some(record),
                    message: format!("expected {} columns, got {}", self.expected_columns, actual),
                });
                continue;
            }
            match self.parse_row(&record) {
                Ok(row) => {
                    rows.push(row);
                    records.push(record);
                    row_numbers.push(row_number);
                }
                Err(message) => rejected.push(RowRejection { row_number, record: Some(record), message }),
            }
        }
        if attempted == 0 {
            None
        } else {
            Some(BatchOutcome { rows, records, row_numbers, rows_attempted: attempted, rejected })
        }
    }

    fn parse_row(&self, record: &StringRecord) -> Result<QueryGivenRow, String> {
        let mut entries = Vec::with_capacity(self.inputs.len());
        for (input, &col) in self.inputs.iter().zip(&self.column_indices) {
            let cell = record
                .get(col)
                .ok_or_else(|| format!("missing column {} for input '${}'", col, input.name))?;
            entries.push(parse_cell(cell, input, &self.null_values).map_err(|err| format!("column '${}': {err}", input.name))?);
        }
        Ok(QueryGivenRow(entries))
    }
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
