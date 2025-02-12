/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


use std::env;
use std::error::Error;
use std::fs;
use std::path::PathBuf;
use std::process::Command;

use tempdir::TempDir;

const TAR_GZ: &str = ".tar.gz";
const ZIP: &str = ".zip";

pub fn get_archive_file(archive_env_var: &str) -> Result<PathBuf, Box<dyn Error>> {
    let archive_file: String = env::var(archive_env_var)?;
    Ok(PathBuf::from(archive_file))
}

pub fn unarchive(archive: PathBuf) -> Result<(TempDir, PathBuf), Box<dyn Error>> {
    let runner_dir = TempDir::new("unarchived")?;
    let current_dir = env::current_dir()?;

    match archive.to_str() {
        Some(path) if path.ends_with(TAR_GZ) => {
            Command::new("tar")
                .args(&["-xf", path, "-C", runner_dir.path().to_str().unwrap()])
                .current_dir(&current_dir)
                .output()?;
        }
        Some(path) if path.ends_with(ZIP) => {
            Command::new("unzip")
                .args(&["-q", path, "-d", runner_dir.path().to_str().unwrap()])
                .current_dir(&current_dir)
                .output()?;
        }
        _ => return Err(format!("The distribution archive format must be either {} or {}",
                               TAR_GZ, ZIP).into()),
    }

    // Get the first (and only) directory entry
    let extracted_dir = fs::read_dir(runner_dir.path())?
        .next()
        .ok_or("No files found in extracted directory")??.path();

    Ok((runner_dir, extracted_dir))
}
