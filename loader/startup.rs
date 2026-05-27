/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    path::Path,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
};

use crate::{
    ExitCode,
    checkpoint::Checkpoint,
    cli::Args,
    fatal, fatal_with,
    output::CHECKPOINT_FILENAME,
};

/// Installs a Ctrl+C handler. First interrupt flips the returned flag so the main loop can
/// drain in-flight batches and write a final checkpoint; second interrupt force-exits with 130.
pub(crate) fn install_shutdown_handler() -> Arc<AtomicBool> {
    let shutdown = Arc::new(AtomicBool::new(false));
    let shutdown_signal = shutdown.clone();
    tokio::spawn(async move {
        if tokio::signal::ctrl_c().await.is_err() {
            return;
        }
        shutdown_signal.store(true, Ordering::SeqCst);
        eprintln!("\nInterrupt received; finishing in-flight batches. Press Ctrl+C again to force exit.");
        if tokio::signal::ctrl_c().await.is_err() {
            return;
        }
        eprintln!("Force-exiting.");
        std::process::exit(130);
    });
    shutdown
}

/// Loads the prior checkpoint from --resume. Exits on conflict with --output-dir
/// (the resume directory IS the output directory) or on unreadable checkpoint.
pub(crate) fn load_resume_checkpoint(args: &Args) -> Option<Checkpoint> {
    if args.resume.is_some() && args.output_dir.is_some() {
        fatal_with(
            ExitCode::UserInputError,
            "--output-dir cannot be combined with --resume; the resume directory is the output directory",
        );
    }
    args.resume.as_deref().map(|dir| {
        let path = Path::new(dir).join(CHECKPOINT_FILENAME);
        Checkpoint::load(&path).unwrap_or_else(|err| fatal(err))
    })
}

/// Returns the password supplied via --password, falling back to an interactive prompt that
/// echoes the username so the user knows which credential is being requested.
pub(crate) fn prompt_password_if_missing(args: &Args, username: &str) -> String {
    args.password
        .clone()
        .unwrap_or_else(|| rpassword::prompt_password(format!("password for '{username}': ")).unwrap())
}
