/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    fs::{File, OpenOptions},
    io::{BufWriter, Write},
    path::{Path, PathBuf},
};

use csv::StringRecord;

pub(crate) struct RejectsWriter {
    csv_path: PathBuf,
    log_path: PathBuf,
    headers: Option<StringRecord>,
    append: bool,
    csv_writer: Option<csv::Writer<File>>,
    log_writer: Option<BufWriter<File>>,
    written: usize,
}

impl RejectsWriter {
    pub(crate) fn new(csv_path: PathBuf, log_path: PathBuf, headers: Option<StringRecord>) -> Self {
        Self { csv_path, log_path, headers, append: false, csv_writer: None, log_writer: None, written: 0 }
    }

    /// Constructs a writer that opens both files in append mode, preserving prior content from an
    /// earlier run. The CSV header is not re-written when the file already exists.
    pub(crate) fn new_append(csv_path: PathBuf, log_path: PathBuf, headers: Option<StringRecord>) -> Self {
        Self { csv_path, log_path, headers, append: true, csv_writer: None, log_writer: None, written: 0 }
    }

    pub(crate) fn record_row(
        &mut self,
        row_number: usize,
        record: Option<&StringRecord>,
        message: &str,
    ) -> Result<(), String> {
        self.ensure_open()?;
        if let Some(record) = record {
            self.csv_writer
                .as_mut()
                .unwrap()
                .write_record(record)
                .map_err(|err| format!("writing rejected row to '{}': {err}", self.csv_path.display()))?;
        }
        writeln!(self.log_writer.as_mut().unwrap(), "row {row_number}: {message}")
            .map_err(|err| format!("writing rejection log to '{}': {err}", self.log_path.display()))?;
        self.written += 1;
        self.flush()
    }

    pub(crate) fn record_batch_failure(
        &mut self,
        row_numbers: &[usize],
        records: &[StringRecord],
        batch_idx: usize,
        message: &str,
    ) -> Result<(), String> {
        if records.is_empty() {
            return Ok(());
        }
        self.ensure_open()?;
        let csv_writer = self.csv_writer.as_mut().unwrap();
        for record in records {
            csv_writer
                .write_record(record)
                .map_err(|err| format!("writing rejected row to '{}': {err}", self.csv_path.display()))?;
        }
        let first = *row_numbers.first().unwrap();
        let last = *row_numbers.last().unwrap();
        let label = if first == last { format!("row {first}") } else { format!("rows {first}-{last}") };
        writeln!(self.log_writer.as_mut().unwrap(), "{label}, batch {batch_idx}: {message}")
            .map_err(|err| format!("writing rejection log to '{}': {err}", self.log_path.display()))?;
        self.written += records.len();
        self.flush()
    }

    pub(crate) fn flush(&mut self) -> Result<(), String> {
        if let Some(writer) = self.csv_writer.as_mut() {
            writer.flush().map_err(|err| format!("flushing rejects CSV: {err}"))?;
        }
        if let Some(writer) = self.log_writer.as_mut() {
            writer.flush().map_err(|err| format!("flushing rejects log: {err}"))?;
        }
        Ok(())
    }

    pub(crate) fn was_written(&self) -> bool {
        self.written > 0
    }

    pub(crate) fn csv_path(&self) -> &Path {
        &self.csv_path
    }

    pub(crate) fn log_path(&self) -> &Path {
        &self.log_path
    }

    fn ensure_open(&mut self) -> Result<(), String> {
        if self.csv_writer.is_none() {
            let already_exists = self.append && self.csv_path.exists();
            let file = if self.append {
                OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(&self.csv_path)
                    .map_err(|err| format!("opening rejects CSV '{}': {err}", self.csv_path.display()))?
            } else {
                File::create(&self.csv_path)
                    .map_err(|err| format!("creating rejects CSV '{}': {err}", self.csv_path.display()))?
            };
            let mut writer = csv::WriterBuilder::new().flexible(true).from_writer(file);
            if !already_exists {
                if let Some(headers) = &self.headers {
                    writer
                        .write_record(headers)
                        .map_err(|err| format!("writing rejects CSV header: {err}"))?;
                }
            }
            self.csv_writer = Some(writer);
        }
        if self.log_writer.is_none() {
            let file = if self.append {
                OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(&self.log_path)
                    .map_err(|err| format!("opening rejects log '{}': {err}", self.log_path.display()))?
            } else {
                File::create(&self.log_path)
                    .map_err(|err| format!("creating rejects log '{}': {err}", self.log_path.display()))?
            };
            self.log_writer = Some(BufWriter::new(file));
        }
        Ok(())
    }
}

pub(crate) fn default_rejects_path(data_path: &str, suffix: &str) -> PathBuf {
    let data = Path::new(data_path);
    let stem = data.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_else(|| "data".to_owned());
    let filename = format!("{stem}-rejects.{suffix}");
    match data.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent.join(filename),
        _ => PathBuf::from(filename),
    }
}
