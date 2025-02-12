/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{error::Error, fs::File, io::BufRead, path::Path, rc::Rc};

use futures::stream::StreamExt;
use typedb_driver::{
    answer::{QueryAnswer, QueryType},
    TransactionType,
};

use crate::{
    printer::{print_document, print_row},
    repl::{command::ReplError, ReplResult},
    transaction_repl, ConsoleContext, MULTILINE_INPUT_SYMBOL,
};

pub(crate) fn database_list(context: &mut ConsoleContext, _input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let databases = context
        .background_runtime
        .run(async move { driver.databases().all().await })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    for db in databases {
        println!("{}", db.name());
    }
    Ok(())
}

pub(crate) fn database_create(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let db_name = input[0].clone();
    context
        .background_runtime
        .run(async move { driver.databases().create(db_name).await })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    println!("Successfully created database.");
    Ok(())
}

pub(crate) fn database_delete(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let db_name = input[0].clone();
    context
        .background_runtime
        .run(async move {
            let db = driver.databases().get(db_name).await?;
            db.delete().await
        })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    println!("Successfully deleted database.");
    Ok(())
}

pub(crate) fn user_create(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let username = input[0].clone();
    let password = input[1].clone();
    context
        .background_runtime
        .run(async move { driver.users().create(username, password).await })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    println!("Successfully created user.");
    Ok(())
}

pub(crate) fn user_delete(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let username = input[0].clone();
    context.background_runtime.run(async move {
        let user =
            match driver.users().get(username.clone()).await.map_err(|err| Box::new(err) as Box<dyn Error + Send>)? {
                None => {
                    Err(Box::new(ReplError { message: format!("User {} not found.", username) })
                        as Box<dyn Error + Send>)?
                }
                Some(user) => user,
            };
        user.delete().await.map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
        Ok(())
    })?;
    println!("Successfully deleted user.");
    Ok(())
}

pub(crate) fn user_update_password(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let username = input[0].clone();
    let new_password = input[1].clone();
    context.background_runtime.run(async move {
        let user =
            match driver.users().get(username.clone()).await.map_err(|err| Box::new(err) as Box<dyn Error + Send>)? {
                None => {
                    Err(Box::new(ReplError { message: format!("User {} not found.", username) })
                        as Box<dyn Error + Send>)?
                }
                Some(user) => user,
            };
        user.update_password(new_password).await.map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
        Ok(())
    })?;
    println!("Successfully updated user password.");
    Ok(())
}

pub(crate) fn transaction_read(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let db_name = &input[0];
    let db_name_owned = db_name.clone();
    let transaction = context
        .background_runtime
        .run(async move { driver.transaction(db_name_owned, TransactionType::Read).await })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    context.transaction = Some(transaction);
    let repl = transaction_repl(db_name, TransactionType::Read);
    context.repl_stack.push(Rc::new(repl));
    Ok(())
}

pub(crate) fn transaction_write(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let db_name = &input[0];
    let db_name_owned = db_name.clone();
    let transaction = context
        .background_runtime
        .run(async move { driver.transaction(db_name_owned, TransactionType::Write).await })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    context.transaction = Some(transaction);
    let repl = transaction_repl(db_name, TransactionType::Write);
    context.repl_stack.push(Rc::new(repl));
    Ok(())
}

pub(crate) fn transaction_schema(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let driver = context.driver.clone();
    let db_name = &input[0];
    let db_name_owned = db_name.clone();
    let transaction = context
        .background_runtime
        .run(async move { driver.transaction(db_name_owned, TransactionType::Schema).await })
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    context.transaction = Some(transaction);
    let repl = transaction_repl(db_name, TransactionType::Schema);
    context.repl_stack.push(Rc::new(repl));
    Ok(())
}

pub(crate) fn transaction_commit(context: &mut ConsoleContext, _input: &[String]) -> ReplResult {
    context
        .background_runtime
        .run(context.transaction.take().unwrap().commit())
        .map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    println!("Successfully committed transaction.");
    context.repl_stack.pop();
    Ok(())
}

pub(crate) fn transaction_close(context: &mut ConsoleContext, _input: &[String]) -> ReplResult {
    let transaction = context.transaction.take().unwrap(); // drop
    let message = match transaction.type_() {
        TransactionType::Read => "Transaction closed",
        TransactionType::Write | TransactionType::Schema => "Transaction closed without committing changes.",
    };
    context.repl_stack.pop();
    println!("{}", message);
    Ok(())
}

pub(crate) fn transaction_rollback(context: &mut ConsoleContext, _input: &[String]) -> ReplResult {
    let transaction = context.transaction.take().unwrap();
    let (transaction, result) = context.background_runtime.run(async move {
        let result = transaction.rollback().await;
        (transaction, result)
    });
    match result {
        Ok(_) => {
            context.transaction = Some(transaction);
            println!("Transaction changes rolled back.");
            Ok(())
        }
        Err(err) => {
            // drop transaction, end repl
            context.repl_stack.pop();
            Err(Box::new(err))
        }
    }
}

pub(crate) fn transaction_source(context: &mut ConsoleContext, input: &[String]) -> ReplResult {
    let file_str = &input[0];
    let path = Path::new(file_str);
    if !path.exists() {
        return Err(Box::new(ReplError { message: format!("File not found: {}", file_str) }) as Box<dyn Error + Send>);
    } else if path.is_dir() {
        return Err(
            Box::new(ReplError { message: format!("Path must be a file: {}", file_str) }) as Box<dyn Error + Send>
        );
    }

    let file = File::open(path).map_err(|err| Box::new(err) as Box<dyn Error + Send>)?;
    let lines = std::io::BufReader::new(file).lines();

    let mut query_count = 0;
    let mut current: Vec<String> = Vec::new();
    for (index, input) in lines.enumerate() {
        match input {
            Ok(mut input) => {
                if input.trim().is_empty() {
                    continue;
                } else if input.ends_with(&MULTILINE_INPUT_SYMBOL) {
                    input.truncate(input.len() - 1);
                    current.push(input);
                } else {
                    current.push(input);
                    let query = current.join("\n");
                    if let Err(err) = execute_query(context, query, false) {
                        return Err(Box::new(ReplError {
                            message: format!(
                                "{}\n### Stopped executing sourced file '{}' at line: {}",
                                err.message(),
                                file_str,
                                index + 1
                            ),
                        }) as Box<dyn Error + Send>);
                    }
                    current.clear();
                    query_count += 1;
                }
            }
            Err(_) => {
                return Err(Box::new(ReplError {
                    message: format!("Error reading file '{}' at line: {}", file_str, index + 1),
                }) as Box<dyn Error + Send>);
            }
        }
    }
    println!("Successfully executed {} queries.", query_count);
    Ok(())
}

pub(crate) fn transaction_query(context: &mut ConsoleContext, input: &[impl AsRef<str>]) -> ReplResult {
    let query = input[0].as_ref().to_owned();
    execute_query(context, query, true).map_err(|err| Box::new(err) as Box<dyn Error + Send>)
}

const MESSAGE_QUERY_TEMPLATE: &'static str = "<QUERY>";
const QUERY_COMPILATION_SUCCESS: &'static str = "Finished <QUERY> query validation and compilation...";
const QUERY_WRITE_SUCCESS: &'static str = "Finished writes...";
const QUERY_STREAMING_ROWS: &'static str = "Streaming answers...";
const QUERY_STREAMING_DOCUMENTS: &'static str = "Streaming documents...";

fn query_type_str(query_type: QueryType) -> &'static str {
    match query_type {
        QueryType::ReadQuery => "read",
        QueryType::WriteQuery => "write",
        QueryType::SchemaQuery => "schema",
    }
}

fn execute_query(context: &mut ConsoleContext, query: String, logging: bool) -> Result<(), typedb_driver::Error> {
    let transaction = context.transaction.take().expect("Transaction query run without active transaction.");
    let (transaction, result) = context.background_runtime.run(async move {
        let result = transaction.query(query).await;
        // note: print results in the async block so we don't have to collect first
        match result {
            Ok(answer) => {
                match answer {
                    QueryAnswer::Ok(query_type) => {
                        if logging {
                            println!("Finished {} query.", query_type_str(query_type));
                        }
                        (transaction, Ok(()))
                    }
                    QueryAnswer::ConceptRowStream(header, mut rows_stream) => {
                        if logging {
                            println!(
                                "{}",
                                QUERY_COMPILATION_SUCCESS
                                    .replace(MESSAGE_QUERY_TEMPLATE, query_type_str(header.query_type))
                            );
                            if matches!(header.query_type, QueryType::WriteQuery) {
                                println!("{}", QUERY_WRITE_SUCCESS);
                            }
                            println!("{}", QUERY_STREAMING_ROWS);
                        }
                        let mut is_first = true;
                        while let Some(result) = rows_stream.next().await {
                            match result {
                                Ok(row) => {
                                    if logging {
                                        print_row(row, is_first);
                                    }
                                    is_first = false;
                                }
                                Err(err) => return (transaction, Err(err)),
                            }
                        }
                        (transaction, Ok(()))
                    }
                    QueryAnswer::ConceptDocumentStream(header, mut documents_stream) => {
                        if logging {
                            println!(
                                "{}",
                                QUERY_COMPILATION_SUCCESS
                                    .replace(MESSAGE_QUERY_TEMPLATE, query_type_str(header.query_type))
                            );
                            if matches!(header.query_type, QueryType::WriteQuery) {
                                println!("{}", QUERY_WRITE_SUCCESS);
                            }
                            println!("{}", QUERY_STREAMING_DOCUMENTS);
                        }

                        while let Some(result) = documents_stream.next().await {
                            match result {
                                Ok(document) => {
                                    if logging {
                                        print_document(document);
                                    }
                                }
                                // Note: we don't necessarily have to terminate the transaction when we get an error
                                // but the signalling isn't in place to do this canonically either!
                                Err(err) => return (transaction, Err(err)),
                            }
                        }
                        (transaction, Ok(()))
                    }
                }
            }
            Err(err) => (transaction, Err(err)),
        }
    });
    if !transaction.is_open() {
        // drop transaction
        // TODO: would be better to return a repl END type. In other places, return repl START(repl)
        context.repl_stack.pop();
    } else {
        context.transaction = Some(transaction);
    };
    result
}
