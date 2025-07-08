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
use std::collections::HashMap;
use clap::Parser;
use home::home_dir;
use rustyline::error::ReadlineError;
use sentry::ClientOptions;
use typedb_driver::{Addresses, Credentials, DriverOptions, Transaction, TransactionType, TypeDBDriver};

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
use crate::operations::{replica_deregister, replica_list, replica_primary, replica_register, server_version};

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
        exit(0);
    }
    if args.password.is_none() {
        args.password = Some(LineReaderHidden::new().readline(&format!("password for '{}': ", args.username)));
    }
    if !args.diagnostics_disabled {
        init_diagnostics()
    }
    let address_info = parse_addresses(&args);
    if !args.tls_disabled && !address_info.only_https {
        println!(
            "\
            TLS connections can only be enabled when connecting to HTTPS endpoints, for example using 'https://<ip>:port'. \
            Please modify the address, or disable TLS (--tls-disabled). WARNING: this will send passwords over plaintext!\
        "
        );
        exit(1);
    }
    let runtime = BackgroundRuntime::new();
    let tls_root_ca_path = args.tls_root_ca.as_ref().map(|value| Path::new(value));
    let driver_options = DriverOptions::new().use_replication(!args.replication_disabled).is_tls_enabled(!args.tls_disabled).tls_root_ca(tls_root_ca_path).unwrap();
    let driver = match runtime.run(TypeDBDriver::new(
        address_info.addresses,
        Credentials::new(&args.username, args.password.as_ref().unwrap()),
        driver_options,
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
        let path = context.convert_path(file_path);
        if let Ok(file) = File::open(&file_path) {
            execute_script(context, path, io::BufReader::new(file).lines())
        } else {
            println!("Error opening file: {}", path.to_string_lossy());
            exit(1);
        }
    }
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
    let server_commands = Subcommand::new("server")
        .add(CommandLeaf::new("version", "Retrieve server version.", server_version));
    
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

    let replica_commands = Subcommand::new("replica")
        .add(CommandLeaf::new("list", "List replicas.", replica_list))
        .add(CommandLeaf::new("primary", "Get current primary replica.", replica_primary))
        .add(CommandLeaf::new_with_inputs(
            "register",
            "Register new replica.",
            vec![
                CommandInput::new("replica id", get_word, None, None),
                CommandInput::new("address", get_word, None, None),
            ],
            replica_register,
        ))
        .add(CommandLeaf::new_with_input(
            "deregister",
            "Deregister existing replica.",
            CommandInput::new("replica id", get_word, None, None),
            replica_deregister,
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
        .add(server_commands)
        .add(database_commands)
        .add(user_commands)
        .add(replica_commands)
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

struct AddressInfo {
    only_https: bool,
    addresses: Addresses,
}

fn parse_addresses(args: &Args) -> AddressInfo {
    if let Some(address) = &args.address {
        AddressInfo {only_https: is_https_address(address), addresses: Addresses::try_from_address_str(address).unwrap() }
    } else if let Some(addresses) = &args.addresses {
        let split = addresses.split(',').map(str::to_string).collect::<Vec<_>>();
        println!("Split: {split:?}");
        let only_https = split.iter().all(|address| is_https_address(address));
        AddressInfo {only_https, addresses: Addresses::try_from_addresses_str(split).unwrap() }
    } else if let Some(translation) = &args.address_translation {
        let mut map = HashMap::new();
        let mut only_https = true;
        for pair in translation.split(',') {
            let (public_address, private_address) = pair
                .split_once('=')
                .unwrap_or_else(|| panic!("Invalid address pair: {pair}. Must be of form public=private"));
            only_https = only_https && is_https_address(public_address);
            map.insert(public_address.to_string(), private_address.to_string());
        }
        println!("Translation map:: {map:?}");
        AddressInfo {only_https, addresses: Addresses::try_from_translation_str(map).unwrap() }
    } else {
        panic!("At least one of --address, --addresses, or --address-translation must be provided.");
    }
}

fn is_https_address(address: &str) -> bool {
    address.starts_with("https:")
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
