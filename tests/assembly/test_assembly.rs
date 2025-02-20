/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{error::Error, io, process::Child, thread::sleep, time::Duration};

use typedb_binary_runner::runner::TypeDBBinaryRunner;

const DATABASE_NAME: &str = "assembly-test-db";

const TYPEDB_SERVER_ARCHIVE_VAR: &str = "TYPEDB_SERVER_ARCHIVE";
const TYPEDB_CONSOLE_ARCHIVE_VAR: &str = "TYPEDB_CONSOLE_ARCHIVE";

const TYPEDB_SERVER_SUBCOMMAND: &str = "server";
const TYPEDB_CONSOLE_SUBCOMMAND: &str = "console";

#[test]
fn test_console() -> Result<(), Box<dyn Error>> {
    // can't drop server_runner, or it will delete the temp dir
    let (_server_runner, mut server_process) = run_typedb_server();

    let console_runner = TypeDBBinaryRunner::new(TYPEDB_CONSOLE_ARCHIVE_VAR, TYPEDB_CONSOLE_SUBCOMMAND)
        .expect("Failed to create console binary runner");
    let db_create_command = format!("database create {}", DATABASE_NAME);
    let args = vec![
        "--address",
        "localhost:1730",
        "--command",
        &db_create_command,
        "--username",
        "admin",
        "--password",
        "password",
        "--tls-disabled",
    ];
    sleep(Duration::from_secs(1));
    let mut console_process: Child = console_runner.run(&args).expect("Failed to spawn child console process.");
    let status = console_process.wait().expect("Error waiting for console to finish").code().unwrap_or(-1);
    if status != 0 {
        panic!("Console command returned non-zero exit status: {}", status);
    }
    server_process.kill().unwrap();
    Ok(())
}

fn run_typedb_server() -> (TypeDBBinaryRunner, Child) {
    let runner = TypeDBBinaryRunner::new(TYPEDB_SERVER_ARCHIVE_VAR, TYPEDB_SERVER_SUBCOMMAND)
        .expect("Failed to create server binary runner");
    let args: Vec<String> = vec!["--server.address".to_owned(), "0.0.0.0:1730".to_owned()];
    let child: io::Result<Child> = runner.run(&args);
    (runner, child.expect("Failed to spawn child server process."))
}
