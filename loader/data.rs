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
    params::Params,
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
    pub(crate) fn open_for_load(params: &Params, inputs: Vec<GivenSpec>, state: &Checkpoint) -> Self {
        if state.watermark_bytes > 0 {
            Self::resume_at(
                &params.data,
                params.header,
                inputs,
                params.null_values.clone(),
                params.max_rows.map(|m| m.saturating_sub(state.watermark * params.batch_rows)),
                state.watermark_bytes,
            )
            .unwrap_or_else(|err| fatal(format!("failed to resume data file '{}': {err}", params.data)))
        } else {
            Self::open(&params.data, params.header, inputs, params.null_values.clone(), params.max_rows)
                .unwrap_or_else(|err| fatal(format!("failed to open data file '{}': {err}", params.data)))
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

#[cfg(test)]
mod tests {
    use chrono::NaiveDate;

    use super::*;

    fn spec(cell_type: CellType, optional: bool) -> GivenSpec {
        GivenSpec { name: "x".to_owned(), cell_type, optional }
    }

    fn entry_value(entry: QueryGivenEntry) -> Value {
        match entry {
            QueryGivenEntry::Value(v) => v,
            other => panic!("expected Value, got {other:?}"),
        }
    }

    // ---------- parse_bool ----------

    #[test]
    fn parse_bool_accepts_canonical_forms() {
        assert_eq!(parse_bool("true").unwrap(), true);
        assert_eq!(parse_bool("false").unwrap(), false);
    }

    #[test]
    fn parse_bool_is_case_insensitive() {
        assert_eq!(parse_bool("TRUE").unwrap(), true);
        assert_eq!(parse_bool("False").unwrap(), false);
        assert_eq!(parse_bool("TrUe").unwrap(), true);
    }

    #[test]
    fn parse_bool_rejects_other_values() {
        assert!(parse_bool("").is_err());
        assert!(parse_bool("yes").is_err());
        assert!(parse_bool("1").is_err());
        assert!(parse_bool(" true").is_err()); // whitespace not stripped
    }

    // ---------- parse_decimal ----------

    #[test]
    fn parse_decimal_zero() {
        let d = parse_decimal("0").unwrap();
        assert_eq!(d, parse_decimal("0.0").unwrap());
        assert_eq!(d, parse_decimal("-0").unwrap());
    }

    #[test]
    fn parse_decimal_signs_round_trip() {
        // Different ways to spell the same value should be equal.
        assert_eq!(parse_decimal("1.5").unwrap(), parse_decimal("+1.5").unwrap());
        assert_ne!(parse_decimal("1.5").unwrap(), parse_decimal("-1.5").unwrap());
        assert_eq!(parse_decimal("1.5").unwrap(), parse_decimal("1.50").unwrap());
        assert_eq!(parse_decimal("1.5").unwrap(), parse_decimal("01.5").unwrap());
    }

    #[test]
    fn parse_decimal_allows_missing_integer_or_fractional_part() {
        assert_eq!(parse_decimal(".5").unwrap(), parse_decimal("0.5").unwrap());
        assert_eq!(parse_decimal("5.").unwrap(), parse_decimal("5").unwrap());
    }

    #[test]
    fn parse_decimal_rejects_garbage() {
        assert!(parse_decimal("").is_err());
        assert!(parse_decimal(".").is_err()); // empty integer AND empty fractional
        assert!(parse_decimal("-").is_err());
        assert!(parse_decimal("abc").is_err());
        assert!(parse_decimal("1.2.3").is_err()); // split_once eats first '.', rest "2.3" fails to parse
    }

    #[test]
    fn parse_decimal_rejects_excess_fractional_digits() {
        // Max representable fractional precision is FRACTIONAL_PART_DENOMINATOR.ilog10() digits.
        let limit = Decimal::FRACTIONAL_PART_DENOMINATOR.ilog10() as usize;
        let too_many = format!("0.{}", "1".repeat(limit + 1));
        assert!(parse_decimal(&too_many).is_err());
        // At-limit must succeed.
        let at_limit = format!("0.{}", "1".repeat(limit));
        assert!(parse_decimal(&at_limit).is_ok());
    }

    // ---------- parse_naive_datetime ----------

    #[test]
    fn parse_naive_datetime_accepts_all_supported_formats() {
        let expected = NaiveDate::from_ymd_opt(2024, 1, 15).unwrap().and_hms_opt(12, 30, 45).unwrap();
        assert_eq!(parse_naive_datetime("2024-01-15T12:30:45").unwrap(), expected);
        assert_eq!(parse_naive_datetime("2024-01-15 12:30:45").unwrap(), expected);
        // Fractional seconds preserved.
        let with_frac = parse_naive_datetime("2024-01-15T12:30:45.123").unwrap();
        assert_eq!(with_frac.date(), expected.date());
    }

    #[test]
    fn parse_naive_datetime_rejects_malformed() {
        assert!(parse_naive_datetime("").is_err());
        assert!(parse_naive_datetime("2024-01-15").is_err()); // missing time
        assert!(parse_naive_datetime("not a date").is_err());
        assert!(parse_naive_datetime("2024-13-01T00:00:00").is_err()); // bad month
    }

    // ---------- parse_fixed_offset ----------

    #[test]
    fn parse_fixed_offset_two_digit_form() {
        assert_eq!(parse_fixed_offset("+00").unwrap(), FixedOffset::east_opt(0).unwrap());
        assert_eq!(parse_fixed_offset("+05").unwrap(), FixedOffset::east_opt(5 * 3600).unwrap());
        assert_eq!(parse_fixed_offset("-05").unwrap(), FixedOffset::east_opt(-5 * 3600).unwrap());
    }

    #[test]
    fn parse_fixed_offset_colon_and_compact_forms_agree() {
        assert_eq!(parse_fixed_offset("+02:30").unwrap(), parse_fixed_offset("+0230").unwrap());
        assert_eq!(parse_fixed_offset("-05:00").unwrap(), parse_fixed_offset("-0500").unwrap());
        assert_eq!(parse_fixed_offset("+05:00").unwrap(), parse_fixed_offset("+05").unwrap());
    }

    #[test]
    fn parse_fixed_offset_rejects_out_of_range() {
        assert!(parse_fixed_offset("+24:00").is_err()); // hours
        assert!(parse_fixed_offset("+02:60").is_err()); // minutes
    }

    #[test]
    fn parse_fixed_offset_rejects_bad_syntax() {
        assert!(parse_fixed_offset("").is_err());
        assert!(parse_fixed_offset("02:00").is_err()); // missing sign
        assert!(parse_fixed_offset("+2:00").is_err()); // one-digit hour
        assert!(parse_fixed_offset("+02-30").is_err()); // wrong separator
        assert!(parse_fixed_offset("garbage").is_err());
    }

    // ---------- split_trailing_offset ----------

    #[test]
    fn split_trailing_offset_handles_z() {
        let (dt, offset) = split_trailing_offset("2024-01-15T12:30:45Z").unwrap();
        assert_eq!(dt, "2024-01-15T12:30:45");
        assert_eq!(offset, FixedOffset::east_opt(0).unwrap());
    }

    #[test]
    fn split_trailing_offset_finds_offset_after_date_hyphens() {
        // The date portion contains hyphens, which must not be mistaken for the offset sign.
        let (dt, offset) = split_trailing_offset("2024-01-15T12:30:45-05:00").unwrap();
        assert_eq!(dt, "2024-01-15T12:30:45");
        assert_eq!(offset, FixedOffset::east_opt(-5 * 3600).unwrap());

        let (dt, offset) = split_trailing_offset("2024-01-15T12:30:45+02:30").unwrap();
        assert_eq!(dt, "2024-01-15T12:30:45");
        assert_eq!(offset, FixedOffset::east_opt(2 * 3600 + 30 * 60).unwrap());
    }

    #[test]
    fn split_trailing_offset_rejects_when_short_or_zoneless() {
        assert!(split_trailing_offset("").is_err());
        assert!(split_trailing_offset("2024-01-15").is_err()); // exactly 10 chars
        assert!(split_trailing_offset("2024-01-15T12:30:45").is_err()); // no Z, no ±
    }

    // ---------- parse_datetime_tz_naive ----------

    #[test]
    fn parse_datetime_tz_naive_accepts_t_and_space_with_seconds_or_fractions() {
        let expected = NaiveDate::from_ymd_opt(2024, 1, 15).unwrap().and_hms_opt(12, 30, 45).unwrap();
        assert_eq!(parse_datetime_tz_naive("2024-01-15T12:30:45").unwrap(), expected);
        assert_eq!(parse_datetime_tz_naive("2024-01-15 12:30:45").unwrap(), expected);
        assert!(parse_datetime_tz_naive("2024-01-15T12:30:45.123").is_ok());
        assert!(parse_datetime_tz_naive("2024-01-15 12:30:45.123").is_ok());
    }

    #[test]
    fn parse_datetime_tz_naive_accepts_minute_precision() {
        let expected = NaiveDate::from_ymd_opt(2024, 1, 15).unwrap().and_hms_opt(12, 30, 0).unwrap();
        assert_eq!(parse_datetime_tz_naive("2024-01-15T12:30").unwrap(), expected);
        assert_eq!(parse_datetime_tz_naive("2024-01-15 12:30").unwrap(), expected);
    }

    // ---------- parse_datetime_tz ----------

    fn ymd_hms(y: i32, m: u32, d: u32, h: u32, mi: u32, s: u32) -> NaiveDateTime {
        NaiveDate::from_ymd_opt(y, m, d).unwrap().and_hms_opt(h, mi, s).unwrap()
    }

    #[test]
    fn parse_datetime_tz_z_yields_utc() {
        let dt = parse_datetime_tz("2024-01-15T12:30:45Z").unwrap();
        // No offset adjustment: local == UTC.
        assert_eq!(dt.naive_utc(), ymd_hms(2024, 1, 15, 12, 30, 45));
    }

    #[test]
    fn parse_datetime_tz_fixed_offsets_convert_to_utc() {
        // +02:00: 12:30:45 local is 10:30:45 UTC.
        let plus_two = parse_datetime_tz("2024-01-15T12:30:45+02:00").unwrap();
        assert_eq!(plus_two.naive_utc(), ymd_hms(2024, 1, 15, 10, 30, 45));

        // -05:00: 12:30:45 local is 17:30:45 UTC.
        let minus_five = parse_datetime_tz("2024-01-15T12:30:45-05:00").unwrap();
        assert_eq!(minus_five.naive_utc(), ymd_hms(2024, 1, 15, 17, 30, 45));

        // Compact form (+0200) must produce the same instant as colon form (+02:00).
        let compact = parse_datetime_tz("2024-01-15T12:30:45+0200").unwrap();
        assert_eq!(compact.naive_utc(), plus_two.naive_utc());
    }

    #[test]
    fn parse_datetime_tz_iana_zone_converts_to_utc_using_zone_rules() {
        // London in winter is GMT (UTC+0): local == UTC.
        let winter = parse_datetime_tz("2024-01-15T12:30:45 Europe/London").unwrap();
        assert_eq!(winter.naive_utc(), ymd_hms(2024, 1, 15, 12, 30, 45));

        // London in summer is BST (UTC+1): 12:30:45 BST is 11:30:45 UTC. This catches any
        // accidental treatment of the zone as a static offset.
        let summer = parse_datetime_tz("2024-07-15T12:30:45 Europe/London").unwrap();
        assert_eq!(summer.naive_utc(), ymd_hms(2024, 7, 15, 11, 30, 45));

        // Kolkata is IST (UTC+5:30) year-round: 12:30:45 IST is 07:00:45 UTC. This in particular
        // exercises a non-hour-aligned offset that can't be confused with any fixed-offset spelling.
        let kolkata = parse_datetime_tz("2024-01-15T12:30:45 Asia/Kolkata").unwrap();
        assert_eq!(kolkata.naive_utc(), ymd_hms(2024, 1, 15, 7, 0, 45));
    }

    #[test]
    fn parse_datetime_tz_iana_and_equivalent_fixed_offset_agree() {
        // Same local time, equivalent zone vs fixed offset: same UTC instant.
        let iana = parse_datetime_tz("2024-01-15T12:30:45 Europe/London").unwrap();
        let fixed = parse_datetime_tz("2024-01-15T12:30:45+00:00").unwrap();
        assert_eq!(iana.naive_utc(), fixed.naive_utc());
    }

    #[test]
    fn parse_datetime_tz_rejects_missing_zone() {
        assert!(parse_datetime_tz("2024-01-15T12:30:45").is_err());
    }

    #[test]
    fn parse_datetime_tz_rejects_unknown_iana_zone() {
        // " Asia/London" — suffix isn't a known IANA tz, falls through to fixed-offset
        // parsing on the full string, which then fails to find a + or -.
        assert!(parse_datetime_tz("2024-01-15T12:30:45 Asia/London").is_err());
    }

    // ---------- parse_cell: null handling ----------

    #[test]
    fn parse_cell_default_null_is_empty_string() {
        // With no explicit --null-values, the empty string is the null sentinel.
        let s = spec(CellType::Integer, true);
        assert!(matches!(parse_cell("", &s, &[]).unwrap(), QueryGivenEntry::Empty));
        // Non-empty string is NOT null even when null_values is empty.
        assert!(matches!(parse_cell("42", &s, &[]).unwrap(), QueryGivenEntry::Value(_)));
    }

    #[test]
    fn parse_cell_explicit_null_values_replaces_default() {
        // Once --null-values is provided, the empty string is no longer special.
        let s = spec(CellType::Integer, true);
        let nulls = vec!["NA".to_owned(), "NULL".to_owned()];
        assert!(matches!(parse_cell("NA", &s, &nulls).unwrap(), QueryGivenEntry::Empty));
        assert!(matches!(parse_cell("NULL", &s, &nulls).unwrap(), QueryGivenEntry::Empty));
        // Empty string is now treated as a value attempt, which fails to parse as integer.
        assert!(parse_cell("", &s, &nulls).is_err());
    }

    #[test]
    fn parse_cell_null_in_non_optional_column_is_error() {
        let s = spec(CellType::String, false);
        let err = parse_cell("", &s, &[]).unwrap_err();
        assert!(err.contains("null"), "expected null-related error, got: {err}");
    }

    #[test]
    fn parse_cell_dispatches_to_typed_parsers() {
        // Smoke test: each type at least parses something non-null successfully.
        assert!(parse_cell("true", &spec(CellType::Boolean, false), &[]).is_ok());
        assert!(parse_cell("42", &spec(CellType::Integer, false), &[]).is_ok());
        assert!(parse_cell("3.14", &spec(CellType::Double, false), &[]).is_ok());
        assert!(parse_cell("1.5", &spec(CellType::Decimal, false), &[]).is_ok());
        assert!(parse_cell("hello", &spec(CellType::String, false), &[]).is_ok());
        assert!(parse_cell("2024-01-15", &spec(CellType::Date, false), &[]).is_ok());
        assert!(parse_cell("2024-01-15T12:30:45", &spec(CellType::Datetime, false), &[]).is_ok());
        assert!(parse_cell("2024-01-15T12:30:45Z", &spec(CellType::DatetimeTz, false), &[]).is_ok());
        assert!(parse_cell("P1Y", &spec(CellType::Duration, false), &[]).is_ok());
    }

    #[test]
    fn parse_cell_string_passes_through_verbatim() {
        // The string cell type must NOT trim or interpret quotes.
        let s = spec(CellType::String, false);
        let value = entry_value(parse_cell("  hello  ", &s, &[]).unwrap());
        assert!(matches!(value, Value::String(ref s) if s == "  hello  "), "unexpected value: {value:?}");
    }

    #[test]
    fn parse_cell_propagates_typed_parse_errors_with_column_context() {
        // The wrapping with "column '$x': ..." happens in parse_row, not parse_cell — so the
        // raw error from parse_cell should NOT mention the column name itself.
        let err = parse_cell("not_a_number", &spec(CellType::Integer, false), &[]).unwrap_err();
        assert!(err.contains("invalid integer"), "got: {err}");
    }
}

