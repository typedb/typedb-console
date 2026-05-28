/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    collections::HashSet,
    io::{self, BufRead, Write},
};

use crate::checkpoint::InFlightBatch;

enum InFlightMode {
    ReprocessAll,
    SkipAll,
    DecideEach,
}

/// Asks the user how to handle batches the previous run dispatched but never confirmed as
/// committed. Returns the batch indices the user chose to skip (treat as already committed);
/// any index not in the set should be reprocessed.
pub(crate) fn resolve_in_flight_skips(in_flight: &[InFlightBatch]) -> HashSet<usize> {
    if in_flight.is_empty() {
        return HashSet::new();
    }
    eprintln!("\nThe checkpoint records {} in-flight batch(es) from the previous run.", in_flight.len());
    eprintln!(
        "These batches were dispatched but never confirmed as committed. Verify them manually against the database before deciding."
    );
    for batch in in_flight {
        eprintln!("  - batch {} (first row: {})", batch.batch_index, format_first_row(&batch.first_row));
    }
    eprintln!(
        "\nOptions: [a]ll = reprocess all, [s]kip all = treat as already committed, [d]ecide each (default: all)"
    );
    let mode = loop {
        let choice = prompt("Choose action").trim().to_ascii_lowercase();
        match choice.as_str() {
            "" | "a" | "all" => break InFlightMode::ReprocessAll,
            "s" | "skip" | "skip all" => break InFlightMode::SkipAll,
            "d" | "each" | "decide" => break InFlightMode::DecideEach,
            other => eprintln!("Unknown choice '{other}'. Please enter 'a', 's', or 'd'."),
        }
    };
    match mode {
        InFlightMode::ReprocessAll => HashSet::new(),
        InFlightMode::SkipAll => in_flight.iter().map(|b| b.batch_index).collect(),
        InFlightMode::DecideEach => in_flight
            .iter()
            .filter(|batch| {
                let q = format!(
                    "Reprocess batch {} (first row: {})?",
                    batch.batch_index,
                    format_first_row(&batch.first_row)
                );
                !confirm(&q)
            })
            .map(|batch| batch.batch_index)
            .collect(),
    }
}

fn format_first_row(row: &[String]) -> String {
    if row.is_empty() { "<empty>".to_owned() } else { row.join(",") }
}

pub(crate) fn confirm(question: &str) -> bool {
    loop {
        let answer = prompt(&format!("{question} [y/N]")).trim().to_ascii_lowercase();
        match answer.as_str() {
            "y" | "yes" => return true,
            "" | "n" | "no" => return false,
            other => eprintln!("Please answer 'y' or 'n' (got '{other}')."),
        }
    }
}

fn prompt(message: &str) -> String {
    eprint!("{message}: ");
    let _ = io::stderr().flush();
    let mut line = String::new();
    let stdin = io::stdin();
    let mut handle = stdin.lock();
    let _ = handle.read_line(&mut line);
    line
}
