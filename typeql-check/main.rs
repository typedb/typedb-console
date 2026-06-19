/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    io::{self, Read},
    process::ExitCode,
};

fn main() -> ExitCode {
    let input = match std::env::args().nth(1) {
        Some(arg) => arg,
        None => {
            let mut buf = String::new();
            if let Err(e) = io::stdin().read_to_string(&mut buf) {
                eprintln!("failed to read stdin: {e}");
                return ExitCode::from(2);
            }
            buf
        }
    };
    match typeql::parse_query(&input) {
        Ok(_) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("{e}");
            ExitCode::FAILURE
        }
    }
}
