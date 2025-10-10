/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    env, io,
    path::PathBuf,
    process::{Child, Command},
};

use tempdir::TempDir;

use crate::util::{get_archive_file, unarchive};

pub struct TypeDBBinaryRunner {
    temp_dir: TempDir, // retain to prevent deletion of the directory
    distribution: PathBuf,
    subcommand: String,
}

impl TypeDBBinaryRunner {
    pub fn new(archive_env_var: &str, typedb_subcommand: &str) -> io::Result<Self> {
        println!("Constructing runner for {}", typedb_subcommand);
        println!("Extracting {} distribution archive.", typedb_subcommand);

        let (temp_dir, distribution) = unarchive(get_archive_file(archive_env_var).unwrap()).unwrap();
        println!("{} distribution extracted, at: {:?}", typedb_subcommand, distribution);

        println!("{} runner constructed", typedb_subcommand);

        Ok(TypeDBBinaryRunner { temp_dir, distribution, subcommand: typedb_subcommand.to_owned() })
    }

    pub fn run(&self, options: &[impl AsRef<str>]) -> io::Result<Child> {
        let typedb_command = self.typedb_command(&self.distribution);
        let mut cmd = Command::new(typedb_command.join(" "));

        // Add options to command
        let mut args = options.iter().map(|s| s.as_ref().to_owned()).collect::<Vec<_>>();
        args.insert(0, self.subcommand.clone());
        cmd.args(args);

        // Execute and wait for the process
        let child = cmd.stdout(std::process::Stdio::inherit()).stderr(std::process::Stdio::inherit()).spawn()?;

        Ok(child)
    }

    fn typedb_command(&self, distribution_path: &PathBuf) -> Vec<String> {
        let mut command = vec![distribution_path.to_string_lossy().to_string()];
        if !env::consts::OS.to_lowercase().contains("win") {
            command[0].push_str("/typedb");
        } else {
            command[0].push_str("\\cmd.exe");
            command.extend(vec!["/c".to_string(), "typedb.bat".to_string()]);
        }
        command
    }

    pub fn distribution_path(&self) -> &PathBuf {
        &self.distribution
    }
}
