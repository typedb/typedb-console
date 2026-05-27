/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    fs::{self, File},
    io::{BufReader, Read, Write},
    path::{Path, PathBuf},
};

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

pub(crate) const CHECKPOINT_VERSION: u32 = 1;

/// Hashes captured at the start of a run for integrity-checking on resume. All three are taken
/// together: a run either has all hashes or none (when checkpointing is disabled).
///
/// Fields are flattened into the parent struct on the wire so the JSON layout is unchanged from
/// when these lived directly on `Checkpoint`.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub(crate) struct Hashes {
    #[serde(rename = "query_hash")]
    pub query: String,
    #[serde(rename = "data_hash")]
    pub data: String,
    #[serde(rename = "schema_hash")]
    pub schema: String,
}

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
    pub batch_index: usize,
    pub byte_end: u64,
    pub first_row: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct CompletedBatch {
    pub batch_index: usize,
    pub byte_end: u64,
}

/// Tracks committed and in-flight batches and maintains a watermark = the highest batch index
/// such that every batch <= it has finished. Non-consecutive completions are parked in
/// `completed_above_watermark` until the watermark catches up.
///
/// Sole owner is the main task; mutated and serialised from one thread, so the vectors stay
/// small (bounded by `parallel_batches`) and linear scans are cheap.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct Checkpoint {
    pub version: u32,
    pub params: CheckpointParams,
    #[serde(flatten)]
    pub hashes: Hashes,
    pub watermark: usize,
    pub watermark_bytes: u64,
    pub completed_above_watermark: Vec<CompletedBatch>,
    pub in_flight: Vec<InFlightBatch>,
}

impl Checkpoint {
    pub(crate) fn new(params: CheckpointParams, hashes: Hashes) -> Self {
        Self {
            version: CHECKPOINT_VERSION,
            params,
            hashes,
            watermark: 0,
            watermark_bytes: 0,
            completed_above_watermark: Vec::new(),
            in_flight: Vec::new(),
        }
    }

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

    pub(crate) fn set_hashes(&mut self, hashes: Hashes) {
        self.hashes = hashes;
    }

    pub(crate) fn record_dispatch(&mut self, batch: InFlightBatch) {
        self.in_flight.push(batch);
    }

    /// Records that a batch has finished (either committed or rejected). The `byte_end` is taken
    /// from the prior dispatch record. If the finished batch is contiguous with the watermark,
    /// the watermark slides forward, also absorbing any previously-parked completions.
    pub(crate) fn record_finish(&mut self, batch_index: usize) {
        let Some(byte_end) = take_in_flight(&mut self.in_flight, batch_index) else {
            return;
        };
        self.absorb_finished_batch(batch_index, byte_end);
    }

    /// Drops an in-flight entry without dispatching it. Used on resume when the user chooses to
    /// treat an in-flight batch as already committed. Advances the watermark just like a
    /// normal completion would.
    pub(crate) fn mark_in_flight_as_skipped(&mut self, batch_index: usize) {
        if let Some(byte_end) = take_in_flight(&mut self.in_flight, batch_index) {
            self.absorb_finished_batch(batch_index, byte_end);
        }
    }

    fn absorb_finished_batch(&mut self, batch_index: usize, byte_end: u64) {
        if batch_index == self.watermark + 1 {
            self.watermark = batch_index;
            self.watermark_bytes = byte_end;
            while let Some(byte_end) = take_completed(&mut self.completed_above_watermark, self.watermark + 1) {
                self.watermark += 1;
                self.watermark_bytes = byte_end;
            }
        } else {
            self.completed_above_watermark.push(CompletedBatch { batch_index, byte_end });
        }
    }
}

fn take_in_flight(in_flight: &mut Vec<InFlightBatch>, batch_index: usize) -> Option<u64> {
    let pos = in_flight.iter().position(|b| b.batch_index == batch_index)?;
    Some(in_flight.swap_remove(pos).byte_end)
}

fn take_completed(completed: &mut Vec<CompletedBatch>, batch_index: usize) -> Option<u64> {
    let pos = completed.iter().position(|c| c.batch_index == batch_index)?;
    Some(completed.swap_remove(pos).byte_end)
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
        let payload = serde_json::to_vec_pretty(checkpoint).map_err(|err| format!("serialising checkpoint: {err}"))?;
        {
            let mut file = File::create(&temp_path)
                .map_err(|err| format!("creating checkpoint temp file '{}': {err}", temp_path.display()))?;
            file.write_all(&payload)
                .map_err(|err| format!("writing checkpoint temp file '{}': {err}", temp_path.display()))?;
            file.sync_all().map_err(|err| format!("fsync checkpoint temp file '{}': {err}", temp_path.display()))?;
        }
        fs::rename(&temp_path, &self.path).map_err(|err| {
            let _ = fs::remove_file(&temp_path);
            format!("renaming checkpoint into place at '{}': {err}", self.path.display())
        })?;
        Ok(())
    }
}

/// How many bytes of the data file to hash for the checkpoint integrity check. Hashing the whole
/// file would dominate startup time on multi-GB inputs; the prefix plus the file's total length
/// is enough to catch any realistic accidental change (truncation, prepend, header edit, swap).
pub(crate) const HASH_PREFIX_BYTES: u64 = 64 * 1024 * 1024;

/// Hashes the first `HASH_PREFIX_BYTES` of the file and combines the result with the file's total
/// length so that two files sharing a prefix but differing in size produce different hashes.
pub(crate) fn hash_file(path: &Path) -> Result<String, String> {
    let file = File::open(path).map_err(|err| format!("opening '{}' for hashing: {err}", path.display()))?;
    let total_size = file.metadata().map_err(|err| format!("reading '{}' metadata: {err}", path.display()))?.len();
    let mut reader = BufReader::new(file).take(HASH_PREFIX_BYTES);
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = reader.read(&mut buf).map_err(|err| format!("reading '{}' for hashing: {err}", path.display()))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(format!("sha256:{total_size}:{:x}", hasher.finalize()))
}

pub(crate) fn hash_string(s: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(s.as_bytes());
    format!("sha256:{:x}", hasher.finalize())
}

pub(crate) fn default_checkpoint_path(data_path: &str) -> PathBuf {
    sibling_path(data_path, "checkpoint.json")
}

/// Builds a path next to `data_path` whose filename is `<data-stem>-<suffix>`. Falls back to
/// a bare `data-<suffix>` if the data path has no stem or parent.
pub(crate) fn sibling_path(data_path: &str, suffix: &str) -> PathBuf {
    let data = Path::new(data_path);
    let stem = data.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_else(|| "data".to_owned());
    let filename = format!("{stem}-{suffix}");
    match data.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent.join(filename),
        _ => PathBuf::from(filename),
    }
}
