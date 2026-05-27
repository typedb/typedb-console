/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{collections::HashMap, fs::File, io::BufReader, str::FromStr};

use chrono::{DateTime, FixedOffset, NaiveDate, NaiveDateTime, TimeZone as ChronoTimeZone};
use chrono_tz::Tz;
use csv::{Reader, StringRecord};
use typedb_driver::{
    concept::{
        Value,
        value::{Decimal, Duration, TimeZone},
    },
    transaction::{QueryGivenEntry, QueryGivenRow},
};

use crate::{
    checkpoint::Checkpoint,
    fatal,
    params::ResolvedParams,
    query::{CellType, GivenSpec},
};

pub(crate) struct CsvLoader {
    reader: Reader<BufReader<File>>,
    headers: Option<StringRecord>,
    column_indices: Vec<usize>,
    expected_columns: usize,
    inputs: Vec<GivenSpec>,
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
    /// First CSV record of this batch as a sequence of raw cell strings, for checkpoint display.
    pub(crate) first_row: Option<Vec<String>>,
    /// Byte position immediately after the last record of this batch.
    pub(crate) byte_end: u64,
}

pub(crate) struct RowRejection {
    pub(crate) row_number: usize,
    pub(crate) record: Option<StringRecord>,
    pub(crate) message: String,
}

impl CsvLoader {
    /// Opens (or resumes) the data file based on the checkpoint's byte watermark. Exits on
    /// failure with a message that distinguishes a fresh open from a resume attempt.
    pub(crate) fn open_for_load(resolved: &ResolvedParams, inputs: Vec<GivenSpec>, state: &Checkpoint) -> Self {
        if state.watermark_bytes > 0 {
            Self::resume_at(
                &resolved.data,
                resolved.header,
                inputs,
                resolved.null_values.clone(),
                resolved.max_rows.map(|m| m.saturating_sub(state.watermark * resolved.batch_rows)),
                state.watermark_bytes,
            )
            .unwrap_or_else(|err| fatal(format!("failed to resume data file '{}': {err}", resolved.data)))
        } else {
            Self::open(&resolved.data, resolved.header, inputs, resolved.null_values.clone(), resolved.max_rows)
                .unwrap_or_else(|err| fatal(format!("failed to open data file '{}': {err}", resolved.data)))
        }
    }

    fn open(
        path: &str,
        has_header: bool,
        inputs: Vec<GivenSpec>,
        null_values: Vec<String>,
        max_rows: Option<usize>,
    ) -> Result<Self, String> {
        Self::new(path, has_header, inputs, null_values, max_rows, None)
    }

    /// Opens the CSV and seeks to `byte_offset` after consuming the header (if any). The byte
    /// position is taken to be a record boundary in the underlying file (typically a stored
    /// `byte_end` from a previous batch).
    fn resume_at(
        path: &str,
        has_header: bool,
        inputs: Vec<GivenSpec>,
        null_values: Vec<String>,
        max_rows: Option<usize>,
        byte_offset: u64,
    ) -> Result<Self, String> {
        Self::new(path, has_header, inputs, null_values, max_rows, Some(byte_offset))
    }

    fn new(
        path: &str,
        has_header: bool,
        inputs: Vec<GivenSpec>,
        null_values: Vec<String>,
        max_rows: Option<usize>,
        seek_to: Option<u64>,
    ) -> Result<Self, String> {
        let file = File::open(path).map_err(|err| format!("opening CSV: {err}"))?;
        let file_size = file.metadata().map_err(|err| format!("reading CSV metadata: {err}"))?.len();
        let mut reader =
            csv::ReaderBuilder::new().has_headers(has_header).flexible(true).from_reader(BufReader::new(file));

        let (headers, column_indices) = if has_header {
            let headers = reader.headers().map_err(|err| format!("reading CSV headers: {err}"))?.clone();
            let header_index: HashMap<&str, usize> =
                headers.iter().enumerate().map(|(index, name)| (name, index)).collect();
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

        if let Some(offset) = seek_to {
            if offset > 0 {
                let mut pos = csv::Position::new();
                pos.set_byte(offset);
                reader.seek(pos).map_err(|err| format!("seeking CSV to byte {offset}: {err}"))?;
            }
        }

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
        let mut first_row: Option<Vec<String>> = None;
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
            if first_row.is_none() {
                first_row = Some(record.iter().map(|s| s.to_owned()).collect());
            }
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
            let byte_end = self.bytes_position();
            Some(BatchOutcome { rows, records, row_numbers, rows_attempted: attempted, rejected, first_row, byte_end })
        }
    }

    fn parse_row(&self, record: &StringRecord) -> Result<QueryGivenRow, String> {
        let mut entries = Vec::with_capacity(self.inputs.len());
        for (input, &col) in self.inputs.iter().zip(&self.column_indices) {
            let cell = record.get(col).ok_or_else(|| format!("missing column {} for input '${}'", col, input.name))?;
            entries.push(
                parse_cell(cell, input, &self.null_values).map_err(|err| format!("column '${}': {err}", input.name))?,
            );
        }
        Ok(QueryGivenRow(entries))
    }
}

fn parse_cell(cell: &str, input: &GivenSpec, null_values: &[String]) -> Result<QueryGivenEntry, String> {
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
        CellType::DatetimeTz => Value::DatetimeTZ(parse_datetime_tz(cell)?),
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

/// Parses a datetime-tz literal: the datetime portion follows
/// ISO-8601 with optional fractional seconds, and the zone is either an ISO-8601 UTC offset
/// (`Z`, `±HH`, `±HH:MM`, or `±HHMM`, attached without a space) or an IANA TZ identifier
/// (`Europe/London`, `Asia/Kolkata`, ...) separated from the datetime by a single space.
fn parse_datetime_tz(cell: &str) -> Result<DateTime<TimeZone>, String> {
    if let Some((dt_str, suffix)) = cell.rsplit_once(' ') {
        if let Ok(tz) = suffix.parse::<Tz>() {
            let naive = parse_datetime_tz_naive(dt_str)?;
            return TimeZone::IANA(tz)
                .from_local_datetime(&naive)
                .earliest()
                .ok_or_else(|| format!("local time '{naive}' does not exist in IANA zone '{suffix}'"));
        }
    }
    let (dt_str, offset) = split_trailing_offset(cell)?;
    let naive = parse_datetime_tz_naive(dt_str)?;
    TimeZone::Fixed(offset)
        .from_local_datetime(&naive)
        .earliest()
        .ok_or_else(|| format!("invalid local time '{naive}' with offset {offset}"))
}

fn parse_datetime_tz_naive(s: &str) -> Result<NaiveDateTime, String> {
    NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S%.f")
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S"))
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M"))
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S%.f"))
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S"))
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M"))
        .map_err(|err| format!("invalid datetime (expected ISO-8601): {err}"))
}

/// Splits a datetime-tz cell into the datetime portion and a fixed offset. Searches for the offset
/// marker (`Z` or a `+`/`-` after the date) so that the `-` separators inside the date portion are
/// not mistaken for a sign.
fn split_trailing_offset(cell: &str) -> Result<(&str, FixedOffset), String> {
    if let Some(dt_str) = cell.strip_suffix('Z') {
        return Ok((dt_str, FixedOffset::east_opt(0).unwrap()));
    }
    if cell.len() <= 10 {
        return Err(format!("datetime-tz too short: '{cell}'"));
    }
    let after_date = &cell[10..];
    let sign_in_after = after_date
        .rfind(|c: char| c == '+' || c == '-')
        .ok_or_else(|| format!("datetime-tz missing zone (expected Z, ±HH[:MM], ±HHMM, or ' <IANA>'): '{cell}'"))?;
    let split = 10 + sign_in_after;
    Ok((&cell[..split], parse_fixed_offset(&cell[split..])?))
}

fn parse_fixed_offset(s: &str) -> Result<FixedOffset, String> {
    let bytes = s.as_bytes();
    if bytes.len() < 3 {
        return Err(format!("invalid offset '{s}' (expected ±HH, ±HH:MM, or ±HHMM)"));
    }
    let sign: i32 = match bytes[0] {
        b'+' => 1,
        b'-' => -1,
        _ => return Err(format!("invalid offset '{s}' (must start with + or -)")),
    };
    let body = &s[1..];
    let (hh, mm) = match body.len() {
        2 => (body, "00"),
        4 => (&body[..2], &body[2..]),
        5 if body.as_bytes()[2] == b':' => (&body[..2], &body[3..]),
        _ => return Err(format!("invalid offset '{s}' (expected ±HH, ±HH:MM, or ±HHMM)")),
    };
    let h: i32 = hh.parse().map_err(|_| format!("invalid offset hours in '{s}'"))?;
    let m: i32 = mm.parse().map_err(|_| format!("invalid offset minutes in '{s}'"))?;
    if !(0..=23).contains(&h) || !(0..=59).contains(&m) {
        return Err(format!("offset out of range: '{s}'"));
    }
    FixedOffset::east_opt(sign * (h * 3600 + m * 60)).ok_or_else(|| format!("offset out of range: '{s}'"))
}
