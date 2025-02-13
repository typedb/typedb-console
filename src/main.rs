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
    ops::{ControlFlow, ControlFlow::Continue},
    path::Path,
    process::exit,
    rc::Rc,
    sync::Arc,
};

use clap::Parser;
use home::home_dir;
use sentry::ClientOptions;
use typedb_driver::{Credentials, DriverOptions, Transaction, TransactionType, TypeDBDriver};
use ControlFlow::Break;

use crate::{
    cli::Args,
    completions::{database_name_completer_fn, file_completer},
    operations::{
        database_create, database_delete, database_list, transaction_close, transaction_commit, transaction_query,
        transaction_read, transaction_rollback, transaction_schema, transaction_source, transaction_write, user_create,
        user_delete, user_update_password,
    },
    repl::{
        command::{get_all, get_word, CommandDefault, CommandInput, CommandOption, Subcommands},
        line_reader::LineReaderHidden,
        Repl, ReplContext, ReplResult,
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
const MULTILINE_INPUT_SYMBOL: &'static str = "\\";
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
    let driver = match runtime.run(TypeDBDriver::new_core(
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

    if !args.command.is_empty() && !args.file.is_empty() {
        println!("Error: Cannot specify both commands and files");
        exit(1);
    } else if !args.command.is_empty() {
        execute_commands(&mut context, &args.command);
    } else if !args.file.is_empty() {
        execute_files(&mut context, &args.file);
    } else {
        execute_interactive(&mut context);
    }
}

fn execute_files(context: &mut ConsoleContext, files: &[String]) {
    for file_path in files {
        if let Ok(file) = File::open(&file_path) {
            execute_file(context, &file_path, io::BufReader::new(file).lines())
        } else {
            println!("Error opening file: {}", file_path);
            exit(1);
        }
    }
}

fn execute_file(context: &mut ConsoleContext, file: &str, inputs: impl Iterator<Item = Result<String, io::Error>>) {
    let inputs = inputs.enumerate();
    let mut current: Vec<String> = Vec::new();
    for (index, input) in inputs {
        match input {
            Ok(mut input) => {
                if input.ends_with(&MULTILINE_INPUT_SYMBOL) {
                    input.truncate(input.len() - 1);
                    current.push(input);
                } else {
                    current.push(input);
                    let input = current.join("\n");
                    if let Err(_) = execute_one(context, &input) {
                        println!("### Stopped executing file '{}' at line: {}", file, index + 1);
                        return;
                    }
                    current.clear();
                }
            }
            Err(_) => {
                println!("### Error reading file '{}' line: {}", file, index + 1);
                return;
            }
        }
    }
}

fn execute_commands(context: &mut ConsoleContext, commands: &[String]) {
    for command in commands {
        if let Err(_) = execute_one(context, command) {
            println!("### Stopped executing at command: {}", command);
            exit(1);
        }
    }
}

fn execute_one(context: &mut ConsoleContext, input: &str) -> ReplResult {
    let current_repl = match context.repl_stack.last() {
        None => {
            println!("Console session has finished.");
            return Err(Box::new(EmptyError {}));
        }
        Some(repl) => repl.clone(),
    };
    println!("{}{}", &current_repl.prompt(), input);
    match current_repl.execute_once(context, input) {
        Ok(_) => Ok(()),
        Err(err) => {
            println!("{}", err);
            Err(err)
        }
    }
}

fn execute_interactive(context: &mut ConsoleContext) {
    while !context.repl_stack.is_empty() {
        let repl_index = context.repl_stack.len() - 1;
        let current_repl = context.repl_stack[repl_index].clone();
        match current_repl.interactive_once(context) {
            Continue(repl_result) => {
                if let Err(err) = repl_result {
                    println!("{}", err);
                }
            }
            Break(_) => {
                if context.repl_stack.len() == repl_index + 1 {
                    // TODO: extra way to eliminate the current repl...
                    //  ideally, every command would signal with a new Repl or to pop one off, so stack manipulation is these control loops
                    let last = context.repl_stack.pop();
                    last.unwrap().finished(context);
                } else {
                    // this is unexpected... quit
                    exit(0)
                }
            }
        }
    }
}

fn entry_repl(driver: Arc<TypeDBDriver>, runtime: BackgroundRuntime) -> Repl<ConsoleContext> {
    let database_commands = Subcommands::new("database")
        .add(CommandOption::new("list", "List databases on the server.", database_list))
        .add(CommandOption::new_with_input(
            "create",
            "Create a new database with the given name.",
            CommandInput::new("db", get_word, None, None),
            database_create,
        ))
        .add(CommandOption::new_with_input(
            "delete",
            "Delete the database with the given name.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            database_delete,
        ));

    let user_commands = Subcommands::new("user")
        .add(CommandOption::new_with_inputs(
            "create",
            "Create new user.",
            vec![
                CommandInput::new("name", get_word, None, None),
                CommandInput::new("password", get_word, Some(get_word), None),
            ],
            user_create,
        ))
        .add(CommandOption::new_with_input(
            "delete",
            "Delete existing user.",
            CommandInput::new("name", get_word, None, None),
            user_delete,
        ))
        .add(CommandOption::new_with_inputs(
            "update-password",
            "Set existing user's password.",
            vec![
                CommandInput::new("name", get_word, None, None),
                CommandInput::new("new password", get_word, Some(get_word), None),
            ],
            user_update_password,
        ));

    let transaction_commands = Subcommands::new("transaction")
        .add(CommandOption::new_with_input(
            "read",
            "Open read transaction.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            transaction_read,
        ))
        .add(CommandOption::new_with_input(
            "write",
            "Open write transaction.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            transaction_write,
        ))
        .add(CommandOption::new_with_input(
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
        .add(CommandOption::new(
            "commit",
            "Commit the current transaction.",
            transaction_commit,
        ))
        .add(CommandOption::new(
            "rollback",
            "Roll back the current transaction to the initial snapshot state.",
            transaction_rollback,
        ))
        .add(CommandOption::new(
            "close",
            "Close the current transaction.",
            transaction_close,
        ))
        .add(CommandOption::new_with_input(
            "source",
            "Execute a file containing a sequence of TypeQL queries. Queries may be split over multiple lines using backslash ('\\')",
            CommandInput::new("file", get_word, None, Some(Box::new(file_completer))),
            transaction_source,
        ))
        .add_default(CommandDefault::new(
            "Execute query string.",
            CommandInput::new("query", get_all, None, None),
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
