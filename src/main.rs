/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    env::temp_dir,
    error::Error,
    fmt::{Debug, Display, Formatter},
    fs::File,
    io,
    io::BufRead,
    path::Path,
    process::exit,
    rc::Rc,
    sync::Arc,
};

use clap::Parser;
use home::home_dir;
use rustyline::error::ReadlineError;
use sentry::ClientOptions;
use typedb_driver::{Credentials, DriverOptions, Transaction, TransactionType, TypeDBDriver};

use crate::{
    cli::Args,
    completions::{database_name_completer_fn, file_completer},
    operations::{
        database_create, database_delete, database_list, transaction_close, transaction_commit, transaction_query,
        transaction_read, transaction_rollback, transaction_schema, transaction_source, transaction_write, user_create,
        user_delete, user_update_password,
    },
    repl::{
        command::{get_to_empty_line, get_word, CommandInput, CommandLeaf, Subcommand},
        line_reader::LineReaderHidden,
        Repl, ReplContext,
    },
    runtime::BackgroundRuntime,
};

mod cli;
mod completions;
mod operations;
mod printer;
mod repl;
mod runtime;

pub const VERSION: &str = include_str!("../VERSION");

const PROMPT: &'static str = ">> ";
const ENTRY_REPL_HISTORY: &'static str = ".typedb_console_repl_history";
const TRANSACTION_REPL_HISTORY: &'static str = "typedb_console_transaction_repl_history";
const DIAGNOSTICS_REPORTING_URI: &'static str =
    "https://7f0ccb67b03abfccbacd7369d1f4ac6b@o4506315929812992.ingest.sentry.io/4506355433537536";

struct ConsoleContext {
    repl_stack: Vec<Rc<Repl<ConsoleContext>>>,
    background_runtime: BackgroundRuntime,
    driver: Arc<TypeDBDriver>,
    transaction: Option<Transaction>,
}

impl ReplContext for ConsoleContext {
    fn current_repl(&self) -> &Repl<Self> {
        self.repl_stack.last().unwrap()
    }
}

fn main() {
    let mut args = Args::parse();
    if args.version {
        println!("{}", VERSION);
        exit(0);
    }
    if args.password.is_none() {
        args.password = Some(LineReaderHidden::new().readline(&format!("password for '{}': ", args.username)));
    }
    if !args.diagnostics_disable {
        init_diagnostics()
    }
    if !args.tls_disabled && !args.address.starts_with("https:") {
        println!(
            "\
            TLS connections can only be enabled when connecting to HTTPS endpoints, for example using 'https://<ip>:port'. \
            Please modify the address, or disable TLS (WARNING: this will send passwords over plaintext!).\
        "
        );
        exit(1);
    }
    let runtime = BackgroundRuntime::new();
    let tls_root_ca_path = args.tls_root_ca.as_ref().map(|value| Path::new(value));
    let driver = match runtime.run(TypeDBDriver::new(
        args.address,
        Credentials::new(&args.username, args.password.as_ref().unwrap()),
        DriverOptions::new(!args.tls_disabled, tls_root_ca_path).unwrap(),
    )) {
        Ok(driver) => Arc::new(driver),
        Err(err) => {
            println!("Failed to create driver connection to server. {}", err);
            if !args.tls_disabled {
                println!("Verify that the server is also configured with TLS encryption.");
            }
            exit(1);
        }
    };

    let repl = entry_repl(driver.clone(), runtime.clone());
    let mut context =
        ConsoleContext { repl_stack: vec![Rc::new(repl)], background_runtime: runtime, transaction: None, driver };

    if !args.command.is_empty() && !args.script.is_empty() {
        println!("Error: Cannot specify both commands and files");
        exit(1);
    } else if !args.command.is_empty() {
        execute_command_list(&mut context, &args.command);
    } else if !args.script.is_empty() {
        execute_scripts(&mut context, &args.script);
    } else {
        execute_interactive(&mut context);
    }
}

fn execute_scripts(context: &mut ConsoleContext, files: &[String]) {
    for file_path in files {
        if let Ok(file) = File::open(&file_path) {
            execute_script(context, &file_path, io::BufReader::new(file).lines())
        } else {
            println!("Error opening file: {}", file_path);
            exit(1);
        }
    }
}

fn execute_script(context: &mut ConsoleContext, file: &str, inputs: impl Iterator<Item = Result<String, io::Error>>) {
    let mut combined_input = String::new();
    for (index, input) in inputs.enumerate() {
        match input {
            Ok(line) => {
                combined_input.push('\n');
                combined_input.push_str(&line);
            }
            Err(_) => {
                println!("### Error reading file '{}' line: {}", file, index + 1);
                return;
            }
        }
    }
    // we could choose to implement this as line-by-line instead of as an interactive-compatible script
    let _ = execute_commands(context, &combined_input, false, true);
}

fn execute_command_list(context: &mut ConsoleContext, commands: &[String]) {
    for command in commands {
        if let Err(_) = execute_commands(context, command, true, true) {
            println!("### Stopped executing at command: {}", command);
            exit(1);
        }
    }
}

fn execute_interactive(context: &mut ConsoleContext) {
    println!("\nWelcome to TypeDB Console!\n");
    while !context.repl_stack.is_empty() {
        let repl_index = context.repl_stack.len() - 1;
        let current_repl = context.repl_stack[repl_index].clone();
        let result = current_repl.get_input();
        match result {
            Ok(input) => {
                if !input.trim().is_empty() {
                    // the execute_all will drive the error handling and printing
                    let _ = execute_commands(context, &input, false, false);
                } else {
                    continue;
                }
            }
            Err(ReadlineError::Interrupted) | Err(ReadlineError::Eof) => {
                if context.repl_stack.len() == repl_index + 1 {
                    // TODO: extra way to eliminate the current repl...
                    //  ideally, every command would signal with a new Repl or to pop one off, so stack manipulation is these control loops
                    let last = context.repl_stack.pop();
                    last.unwrap().finished(context);
                } else {
                    // this is unexpected... quit
                    exit(1)
                }
            }
            Err(err) => {
                println!("{}", err);
            }
        }
    }
}

fn execute_commands(
    context: &mut ConsoleContext,
    mut input: &str,
    coerce_each_command_to_one_line: bool,
    must_log_command: bool,
) -> Result<(), EmptyError> {
    let mut multiple_commands = None;
    while !context.repl_stack.is_empty() && !input.trim().is_empty() {
        let repl_index = context.repl_stack.len() - 1;
        let current_repl = context.repl_stack[repl_index].clone();

        input = match current_repl.match_first_command(input, coerce_each_command_to_one_line) {
            Ok(None) => {
                println!("Unrecognised command: {}", input);
                return Err(EmptyError {});
            }
            Ok(Some((command, arguments, next_command_index))) => {
                let command_string = &input[0..next_command_index];
                if multiple_commands.is_none() && !input[next_command_index..].trim().is_empty() {
                    multiple_commands = Some(true);
                }

                if must_log_command || multiple_commands.is_some_and(|b| b) {
                    println!("{} {}", "+".repeat(repl_index + 1), command_string.trim());
                }
                match command.execute(context, arguments) {
                    Ok(_) => &input[next_command_index..],
                    Err(err) => {
                        println!("Error executing command: '{}'\n{}", command_string.trim(), err);
                        return Err(EmptyError {});
                    }
                }
            }
            Err(err) => {
                println!("{}", err);
                return Err(EmptyError {});
            }
        };
        input = input.trim_start();
    }
    Ok(())
}

fn entry_repl(driver: Arc<TypeDBDriver>, runtime: BackgroundRuntime) -> Repl<ConsoleContext> {
    let database_commands = Subcommand::new("database")
        .add(CommandLeaf::new("list", "List databases on the server.", database_list))
        .add(CommandLeaf::new_with_input(
            "create",
            "Create a new database with the given name.",
            CommandInput::new("db", get_word, None, None),
            database_create,
        ))
        .add(CommandLeaf::new_with_input(
            "delete",
            "Delete the database with the given name.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            database_delete,
        ));

    let user_commands = Subcommand::new("user")
        .add(CommandLeaf::new_with_inputs(
            "create",
            "Create new user.",
            vec![
                CommandInput::new("name", get_word, None, None),
                CommandInput::new("password", get_word, Some(get_word), None),
            ],
            user_create,
        ))
        .add(CommandLeaf::new_with_input(
            "delete",
            "Delete existing user.",
            CommandInput::new("name", get_word, None, None),
            user_delete,
        ))
        .add(CommandLeaf::new_with_inputs(
            "update-password",
            "Set existing user's password.",
            vec![
                CommandInput::new("name", get_word, None, None),
                CommandInput::new("new password", get_word, Some(get_word), None),
            ],
            user_update_password,
        ));

    let transaction_commands = Subcommand::new("transaction")
        .add(CommandLeaf::new_with_input(
            "read",
            "Open read transaction.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            transaction_read,
        ))
        .add(CommandLeaf::new_with_input(
            "write",
            "Open write transaction.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            transaction_write,
        ))
        .add(CommandLeaf::new_with_input(
            "schema",
            "Open schema transaction.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            transaction_schema,
        ));

    let history_path = home_dir().unwrap_or_else(|| temp_dir()).join(ENTRY_REPL_HISTORY);

    let repl = Repl::new(PROMPT.to_owned(), history_path, false, None)
        .add(database_commands)
        .add(user_commands)
        .add(transaction_commands);

    repl
}

fn transaction_repl(database: &str, transaction_type: TransactionType) -> Repl<ConsoleContext> {
    let db_prompt = format!("{}::{}{}", database, transaction_type_str(transaction_type), PROMPT);
    let history_path = home_dir().unwrap_or_else(|| temp_dir()).join(TRANSACTION_REPL_HISTORY);
    let repl = Repl::new(db_prompt, history_path, true, Some(on_transaction_repl_finished))
        .add(CommandLeaf::new(
            "commit",
            "Commit the current transaction.",
            transaction_commit,
        ))
        .add(CommandLeaf::new(
            "rollback",
            "Roll back the current transaction to the initial snapshot state.",
            transaction_rollback,
        ))
        .add(CommandLeaf::new(
            "close",
            "Close the current transaction.",
            transaction_close,
        ))
        .add(CommandLeaf::new_with_input(
            "source",
            "Execute a file containing a sequence of TypeQL queries. Queries may be split over multiple lines using backslash ('\\')",
            CommandInput::new("file", get_word, None, Some(Box::new(file_completer))),
            transaction_source,
        ))
        // default: no token
        .add(CommandLeaf::new_with_input(
            "",
            "Execute query string.",
            CommandInput::new("query", get_to_empty_line, None, None),
            transaction_query,
        ));
    repl
}

fn on_transaction_repl_finished(context: &mut ConsoleContext) {
    context.transaction.take(); // drop
}

fn transaction_type_str(transaction_type: TransactionType) -> &'static str {
    match transaction_type {
        TransactionType::Read => "read",
        TransactionType::Write => "write",
        TransactionType::Schema => "schema",
    }
}

fn init_diagnostics() {
    let _ = sentry::init((
        DIAGNOSTICS_REPORTING_URI,
        ClientOptions { release: Some(VERSION.into()), ..Default::default() },
    ));
}

struct EmptyError {}

impl Debug for EmptyError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        Display::fmt(self, f)
    }
}

impl Display for EmptyError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "")
    }
}

impl Error for EmptyError {}
