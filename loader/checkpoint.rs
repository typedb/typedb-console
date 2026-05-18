/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    collections::BTreeMap,
    fs::{self, File},
    io::{BufReader, Read, Write},
    path::{Path, PathBuf},
};

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

pub(crate) const CHECKPOINT_VERSION: u32 = 2;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub(crate) struct CheckpointParams {
    pub query: String,
    pub database: String,
    pub data: String,
    pub header: bool,
    pub null_values: Vec<String>,
    pub max_rows: Option<usize>,
    pub batch_rows: usize,
    pub parallel_batches: usize,
    pub rejects_file: Option<String>,
    pub rejects_log: Option<String>,
    pub stop_on_error: bool,
    pub max_rejects: Option<usize>,
    pub schema_file: Option<String>,
    pub create_db: bool,
    pub addresses: String,
    pub username: String,
    pub tls_disabled: bool,
    pub tls_root_ca: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct InFlightBatch {
    pub batch_idx: usize,
    pub byte_end: u64,
    pub first_row: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct CompletedBatch {
    pub batch_idx: usize,
    pub byte_end: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct Checkpoint {
    pub version: u32,
    pub params: CheckpointParams,
    pub query_hash: String,
    pub data_hash: String,
    pub schema_hash: String,
    pub watermark: usize,
    pub watermark_bytes: u64,
    pub completed_above_watermark: Vec<CompletedBatch>,
    pub in_flight: Vec<InFlightBatch>,
}

impl Checkpoint {
    pub(crate) fn load(path: &Path) -> Result<Self, String> {
        let file = File::open(path).map_err(|err| format!("opening checkpoint '{}': {err}", path.display()))?;
        let checkpoint: Checkpoint = serde_json::from_reader(BufReader::new(file))
            .map_err(|err| format!("parsing checkpoint '{}': {err}", path.display()))?;
        if checkpoint.version != CHECKPOINT_VERSION {
            return Err(format!(
                "checkpoint '{}' is version {} but loader expects version {}",
                path.display(),
                checkpoint.version,
                CHECKPOINT_VERSION
            ));
        }
        Ok(checkpoint)
    }
}

pub(crate) struct CheckpointWriter {
    path: PathBuf,
}

impl CheckpointWriter {
    pub(crate) fn new(path: PathBuf) -> Self {
        Self { path }
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    /// Writes the checkpoint atomically: serialise to a sibling temp file, fsync, rename over the
    /// target. A crash mid-write leaves the previous good checkpoint in place.
    pub(crate) fn write(&self, checkpoint: &Checkpoint) -> Result<(), String> {
        let parent = self.path.parent().unwrap_or_else(|| Path::new("."));
        let temp_name = format!(
            ".{}.tmp.{}",
            self.path.file_name().and_then(|s| s.to_str()).unwrap_or("checkpoint"),
            std::process::id()
        );
        let temp_path = parent.join(temp_name);
        let payload = serde_json::to_vec_pretty(checkpoint)
            .map_err(|err| format!("serialising checkpoint: {err}"))?;
        {
            let mut file = File::create(&temp_path)
                .map_err(|err| format!("creating checkpoint temp file '{}': {err}", temp_path.display()))?;
            file.write_all(&payload)
                .map_err(|err| format!("writing checkpoint temp file '{}': {err}", temp_path.display()))?;
            file.sync_all()
                .map_err(|err| format!("fsync checkpoint temp file '{}': {err}", temp_path.display()))?;
        }
        fs::rename(&temp_path, &self.path).map_err(|err| {
            let _ = fs::remove_file(&temp_path);
            format!("renaming checkpoint into place at '{}': {err}", self.path.display())
        })?;
        Ok(())
    }
}

/// Tracks committed and in-flight batches and maintains a watermark = the highest batch index
/// such that every batch <= it has finished. Non-consecutive completions are parked in
/// `completed_above_watermark` until the watermark catches up.
pub(crate) struct CheckpointState {
    params: CheckpointParams,
    query_hash: String,
    data_hash: String,
    schema_hash: String,
    watermark: usize,
    watermark_bytes: u64,
    completed_above_watermark: BTreeMap<usize, u64>,
    in_flight: BTreeMap<usize, InFlightBatch>,
}

impl CheckpointState {
    pub(crate) fn new(
        params: CheckpointParams,
        query_hash: String,
        data_hash: String,
        schema_hash: String,
    ) -> Self {
        Self {
            params,
            query_hash,
            data_hash,
            schema_hash,
            watermark: 0,
            watermark_bytes: 0,
            completed_above_watermark: BTreeMap::new(),
            in_flight: BTreeMap::new(),
        }
    }

    pub(crate) fn from_checkpoint(checkpoint: Checkpoint) -> Self {
        let mut completed = BTreeMap::new();
        for c in checkpoint.completed_above_watermark {
            completed.insert(c.batch_idx, c.byte_end);
        }
        let mut in_flight = BTreeMap::new();
        for b in checkpoint.in_flight {
            in_flight.insert(b.batch_idx, b);
        }
        Self {
            params: checkpoint.params,
            query_hash: checkpoint.query_hash,
            data_hash: checkpoint.data_hash,
            schema_hash: checkpoint.schema_hash,
            watermark: checkpoint.watermark,
            watermark_bytes: checkpoint.watermark_bytes,
            completed_above_watermark: completed,
            in_flight,
        }
    }

    pub(crate) fn watermark(&self) -> usize {
        self.watermark
    }

    pub(crate) fn watermark_bytes(&self) -> u64 {
        self.watermark_bytes
    }

    pub(crate) fn completed_above_watermark(&self) -> &BTreeMap<usize, u64> {
        &self.completed_above_watermark
    }

    pub(crate) fn set_hashes(&mut self, query_hash: String, data_hash: String, schema_hash: String) {
        self.query_hash = query_hash;
        self.data_hash = data_hash;
        self.schema_hash = schema_hash;
    }

    pub(crate) fn record_dispatch(&mut self, batch: InFlightBatch) {
        self.in_flight.insert(batch.batch_idx, batch);
    }

    /// Records that a batch has finished (either committed or rejected). Returns true if the
    /// in-flight set was changed. The byte_end is taken from the prior dispatch record.
    pub(crate) fn record_finish(&mut self, batch_idx: usize) -> bool {
        let Some(batch) = self.in_flight.remove(&batch_idx) else {
            return false;
        };
        let byte_end = batch.byte_end;
        if batch_idx == self.watermark + 1 {
            self.watermark = batch_idx;
            self.watermark_bytes = byte_end;
            while let Some(&next_end) = self.completed_above_watermark.get(&(self.watermark + 1)) {
                self.watermark += 1;
                self.watermark_bytes = next_end;
                self.completed_above_watermark.remove(&self.watermark);
            }
        } else {
            self.completed_above_watermark.insert(batch_idx, byte_end);
        }
        true
    }

    /// Drops an in-flight entry without advancing the watermark. Used on resume when the user
    /// chooses to treat an in-flight batch as already committed.
    pub(crate) fn mark_in_flight_as_skipped(&mut self, batch_idx: usize) {
        if let Some(batch) = self.in_flight.remove(&batch_idx) {
            let byte_end = batch.byte_end;
            if batch_idx == self.watermark + 1 {
                self.watermark = batch_idx;
                self.watermark_bytes = byte_end;
                while let Some(&next_end) = self.completed_above_watermark.get(&(self.watermark + 1)) {
                    self.watermark += 1;
                    self.watermark_bytes = next_end;
                    self.completed_above_watermark.remove(&self.watermark);
                }
            } else {
                self.completed_above_watermark.insert(batch_idx, byte_end);
            }
        }
    }

    pub(crate) fn snapshot(&self) -> Checkpoint {
        Checkpoint {
            version: CHECKPOINT_VERSION,
            params: self.params.clone(),
            query_hash: self.query_hash.clone(),
            data_hash: self.data_hash.clone(),
            schema_hash: self.schema_hash.clone(),
            watermark: self.watermark,
            watermark_bytes: self.watermark_bytes,
            completed_above_watermark: self
                .completed_above_watermark
                .iter()
                .map(|(&batch_idx, &byte_end)| CompletedBatch { batch_idx, byte_end })
                .collect(),
            in_flight: self.in_flight.values().cloned().collect(),
        }
    }
}

pub(crate) fn hash_file(path: &Path) -> Result<String, String> {
    let file = File::open(path).map_err(|err| format!("opening '{}' for hashing: {err}", path.display()))?;
    let mut reader = BufReader::new(file);
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = reader.read(&mut buf).map_err(|err| format!("reading '{}' for hashing: {err}", path.display()))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(format!("sha256:{:x}", hasher.finalize()))
}

pub(crate) fn hash_string(s: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(s.as_bytes());
    format!("sha256:{:x}", hasher.finalize())
}

pub(crate) fn default_checkpoint_path(data_path: &str) -> PathBuf {
    let data = Path::new(data_path);
    let stem = data.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_else(|| "data".to_owned());
    let filename = format!("{stem}-checkpoint.json");
    match data.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent.join(filename),
        _ => PathBuf::from(filename),
    }
}
