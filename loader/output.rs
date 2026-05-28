/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    fs,
    path::{Path, PathBuf},
};

use crate::{ExitCode, checkpoint::CheckpointWriter, cli::Args, fatal, fatal_with, params::Params};

pub(crate) const CHECKPOINT_FILENAME: &str = "checkpoint.json";
pub(crate) const REJECTS_CSV_FILENAME: &str = "rejects.csv";
pub(crate) const REJECTS_LOG_FILENAME: &str = "rejects.log";

/// The paths and writers the loader writes to during a run. The checkpoint writer is `None`
/// when `--no-checkpoint` was passed; the rejects paths are always populated and only get
/// touched if rows actually get rejected.
pub(crate) struct OutputConfiguration {
    pub rejects_csv: PathBuf,
    pub rejects_log: PathBuf,
    pub checkpoint_path: Option<PathBuf>,
}

/// Resolves the output directory, creates it, and prepares the checkpoint writer. Exits if
/// `--no-checkpoint` is absent and a checkpoint already exists in the chosen directory (the
/// user must then choose to `--resume`, `--output-dir`, or `--no-checkpoint`).
pub(crate) fn prepare_output(args: &Args, params: &Params) -> OutputConfiguration {
    let output_dir: PathBuf = if let Some(dir) = args.resume.as_deref() {
        PathBuf::from(dir)
    } else if let Some(dir) = args.output_dir.as_deref() {
        PathBuf::from(dir)
    } else {
        default_output_dir(&params.data)
    };
    fs::create_dir_all(&output_dir)
        .unwrap_or_else(|err| fatal(format!("failed to create output directory '{}': {err}", output_dir.display())));

    OutputConfiguration {
        rejects_csv: output_dir.join(REJECTS_CSV_FILENAME),
        rejects_log: output_dir.join(REJECTS_LOG_FILENAME),
        checkpoint_path: if args.no_checkpoint { None } else { Some(output_dir.join(CHECKPOINT_FILENAME)) },
    }
}

/// Builds the default output directory next to the data file: `loader_<data-stem>_progress`.
/// Falls back to `loader_data_progress` in the current directory if the data path has no stem
/// or parent.
fn default_output_dir(data_path: &str) -> PathBuf {
    let data = Path::new(data_path);
    let stem = data.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_else(|| "data".to_owned());
    let dirname = format!("loader_{stem}_progress");
    match data.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent.join(dirname),
        _ => PathBuf::from(dirname),
    }
}
