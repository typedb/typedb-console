/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    env,
    env::temp_dir,
    error::Error,
    fmt::{Debug, Display, Formatter},
    fs::File,
    io,
    io::BufRead,
    path::{Path, PathBuf},
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
        database_create, database_delete, database_export, database_import, database_list, database_schema,
        transaction_close, transaction_commit, transaction_query, transaction_read, transaction_rollback,
        transaction_schema, transaction_source, transaction_write, user_create, user_delete, user_list,
        user_update_password,
    },
    repl::{
        command::{get_word, parse_one_query, CommandInput, CommandLeaf, Subcommand},
        line_reader::LineReaderHidden,
        Repl, ReplContext,
    },
    runtime::BackgroundRuntime,
};

mod cli;
mod completions;
mod constants;
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

#[derive(Debug, Copy, Clone)]
enum ExitCode {
    Success = 0,
    GeneralError = 1,
    CommandError = 2,
    ConnectionError = 3,
    UserInputError = 4,
    QueryError = 5,
}

fn exit_with_error(err: &(dyn std::error::Error + 'static)) -> ! {
    use crate::repl::command::ReplError;
    if let Some(repl_err) = err.downcast_ref::<ReplError>() {
        eprintln!("Error: {}", repl_err);
        exit(ExitCode::UserInputError as i32);
    } else if let Some(io_err) = err.downcast_ref::<io::Error>() {
        eprintln!("I/O Error: {}", io_err);
        exit(ExitCode::GeneralError as i32);
    } else if let Some(driver_err) = err.downcast_ref::<typedb_driver::Error>() {
        eprintln!("TypeDB Error: {}", driver_err);
        exit(ExitCode::QueryError as i32);
    } else if let Some(command_error) = err.downcast_ref::<CommandError>() {
        eprintln!("Command Error: {}", command_error);
        exit(ExitCode::CommandError as i32);
    } else {
        eprintln!("Error: {}", err);
        exit(ExitCode::GeneralError as i32);
    }
}

struct ConsoleContext {
    invocation_dir: PathBuf,
    repl_stack: Vec<Rc<Repl<ConsoleContext>>>,
    background_runtime: BackgroundRuntime,
    driver: Arc<TypeDBDriver>,
    transaction: Option<(Transaction, bool)>,
    script_dir: Option<String>,
}

impl ConsoleContext {
    fn convert_path(&self, path: &str) -> PathBuf {
        let path = Path::new(path);
        if !path.is_absolute() {
            match self.script_dir.as_ref() {
                None => self.invocation_dir.join(path),
                Some(dir) => PathBuf::from(dir).join(path),
            }
        } else {
            path.to_path_buf()
        }
    }

    fn has_changes(&self) -> bool {
        self.transaction.as_ref().is_some_and(|(_, has_writes)| *has_writes)
    }
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
        exit(ExitCode::Success as i32);
    }
    if args.password.is_none() {
        args.password = Some(LineReaderHidden::new().readline(&format!("password for '{}': ", args.username)));
    }
    if !args.diagnostics_disable {
        init_diagnostics()
    }
    if !args.tls_disabled && !args.address.starts_with("https:") {
        eprintln!(
            "\
            TLS connections can only be enabled when connecting to HTTPS endpoints, for example using 'https://<ip>:port'. \
            Please modify the address, or disable TLS (--tls-disabled). WARNING: this will send passwords over plaintext!\
        "
        );
        exit(ExitCode::UserInputError as i32);
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
            eprintln!("Failed to create driver connection to server. {}", err);
            if !args.tls_disabled {
                eprintln!("Verify that the server is also configured with TLS encryption.");
            }
            exit(ExitCode::ConnectionError as i32);
        }
    };

    let repl = entry_repl(driver.clone(), runtime.clone());
    let invocation_dir = PathBuf::from(env::current_dir().unwrap());
    let mut context = ConsoleContext {
        invocation_dir,
        repl_stack: vec![Rc::new(repl)],
        background_runtime: runtime,
        transaction: None,
        script_dir: None,
        driver,
    };

    if !args.command.is_empty() && !args.script.is_empty() {
        eprintln!("Error: Cannot specify both commands and files");
        exit(ExitCode::UserInputError as i32);
    } else if !args.command.is_empty() {
        if let Err(err) = execute_command_list(&mut context, &args.command) {
            exit_with_error(&*err);
        }
    } else if !args.script.is_empty() {
        if let Err(err) = execute_scripts(&mut context, &args.script) {
            exit_with_error(&*err);
        }
    } else {
        execute_interactive(&mut context);
    }
}

fn execute_scripts(context: &mut ConsoleContext, files: &[String]) -> Result<(), Box<dyn Error>> {
    for file_path in files {
        let path = context.convert_path(file_path);
        if let Ok(file) = File::open(&file_path) {
            execute_script(context, path, io::BufReader::new(file).lines())
        } else {
            return Err(Box::new(io::Error::new(
                io::ErrorKind::NotFound,
                format!("Error opening file: {}", path.to_string_lossy()),
            )));
        }
    }
    Ok(())
}

fn execute_script(
    context: &mut ConsoleContext,
    file_path: PathBuf,
    inputs: impl Iterator<Item = Result<String, io::Error>>,
) {
    let mut combined_input = String::new();
    context.script_dir = Some(file_path.parent().unwrap().to_string_lossy().to_string());
    for (index, input) in inputs.enumerate() {
        match input {
            Ok(line) => {
                combined_input.push('\n');
                combined_input.push_str(&line);
            }
            Err(_) => {
                println!("### Error reading file '{}' line: {}", file_path.to_string_lossy(), index + 1);
                return;
            }
        }
    }
    // we could choose to implement this as line-by-line instead of as an interactive-compatible script
    let _ = execute_commands(context, &combined_input, false, true);
    context.script_dir = None;
}

fn execute_command_list(context: &mut ConsoleContext, commands: &[String]) -> Result<(), Box<dyn Error>> {
    for command in commands {
        if let Err(err) = execute_commands(context, command, true, true) {
            eprintln!("### Stopped executing at command: {}", command);
            return Err(Box::new(err));
        }
    }
    Ok(())
}

fn execute_interactive(context: &mut ConsoleContext) {
    println!("\nWelcome to TypeDB Console!\n");
    while !context.repl_stack.is_empty() {
        let repl_index = context.repl_stack.len() - 1;
        let current_repl = context.repl_stack[repl_index].clone();
        let (result, interrupt_input_empty) = current_repl.get_input(context.has_changes());
        match result {
            Ok(input) => {
                if !input.trim().is_empty() {
                    let _ = execute_commands(context, &input, false, false);
                } else {
                    continue;
                }
            }
            Err(ReadlineError::Interrupted) | Err(ReadlineError::Eof) => {
                let exit_once = interrupt_input_empty.unwrap_or(true);
                if exit_once && context.repl_stack.len() == repl_index + 1 {
                    // TODO: extra way to eliminate the current repl...
                    //  ideally, every command would signal with a new Repl or to pop one off, so stack manipulation is these control loops
                    let last = context.repl_stack.pop();
                    last.unwrap().finished(context);
                } else if !exit_once {
                    // do nothing
                } else {
                    // this is unexpected... quit
                    exit(ExitCode::GeneralError as i32);
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
) -> Result<(), CommandError> {
    let mut multiple_commands = None;
    while !context.repl_stack.is_empty() && !input.trim().is_empty() {
        let repl_index = context.repl_stack.len() - 1;
        let current_repl = context.repl_stack[repl_index].clone();

        input = match current_repl.match_first_command(input, coerce_each_command_to_one_line) {
            Ok(None) => {
                let message = format!("Unrecognised command: {}", input);
                eprintln!("{}", message);
                return Err(CommandError { message });
            }
            Ok(Some((command, arguments, next_command_index))) => {
                let command_string = &input[0..next_command_index];
                if multiple_commands.is_none() && !input[next_command_index..].trim().is_empty() {
                    multiple_commands = Some(true);
                }

                if must_log_command || multiple_commands.is_some_and(|b| b) {
                    eprintln!("{} {}", "+".repeat(repl_index + 1), command_string.trim());
                }
                match command.execute(context, arguments) {
                    Ok(_) => &input[next_command_index..],
                    Err(err) => {
                        let message = format!("Error executing command: '{}'\n{}", command_string.trim(), err);
                        eprintln!("{}", message);
                        return Err(CommandError { message });
                    }
                }
            }
            Err(err) => {
                eprintln!("{}", err);
                return Err(CommandError { message: err.to_string() });
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
        ))
        .add(CommandLeaf::new_with_input(
            "schema",
            "Retrieve the TypeQL representation of a database's schema.",
            CommandInput::new("db", get_word, None, Some(database_name_completer_fn(driver.clone(), runtime.clone()))),
            database_schema,
        ))
        .add(CommandLeaf::new_with_inputs(
            "import",
            "Create a database with the given name based on another previously exported database.",
            vec![
                CommandInput::new("db", get_word, None, None),
                CommandInput::new("schema file path", get_word, None, None),
                CommandInput::new("data file path", get_word, None, None),
            ],
            database_import,
        ))
        .add(CommandLeaf::new_with_inputs(
            "export",
            "Export a database into a schema definition and a data files.",
            vec![
                CommandInput::new(
                    "db",
                    get_word,
                    None,
                    Some(database_name_completer_fn(driver.clone(), runtime.clone())),
                ),
                CommandInput::new("schema file path", get_word, None, None),
                CommandInput::new("data file path", get_word, None, None),
            ],
            database_export,
        ));

    let user_commands = Subcommand::new("user")
        .add(CommandLeaf::new("list", "List users.", user_list))
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
            "Synchronously execute a file containing a sequence of TypeQL queries with full validation. Queries can be explicitly ended with 'end;' if required. Path may be absolute or relative to the invoking script (if there is one) otherwise relative to the current working directory.",
            CommandInput::new("file", get_word, None, Some(Box::new(file_completer))),
            transaction_source,
        ))
        // default: no token
        .add(CommandLeaf::new_with_input(
            "",
            "Execute query string.",
            CommandInput::new("query", parse_one_query, None, None),
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

struct CommandError {
    message: String,
}

impl Debug for CommandError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        Display::fmt(self, f)
    }
}

impl Display for CommandError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl Error for CommandError {}
