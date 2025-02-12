/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    error::Error,
    ops::{
        ControlFlow,
        ControlFlow::{Break, Continue},
    },
    path::PathBuf,
    process::exit,
};

use rustyline::error::ReadlineError;

use crate::repl::{
    command::{Command, CommandDefault, CommandOption, Subcommands},
    line_reader::RustylineReader,
};

pub(crate) mod command;
pub(crate) mod line_reader;

pub(crate) trait ReplContext: Sized {
    fn current_repl(&self) -> &Repl<Self>;
}

pub(crate) type ReplResult = Result<(), Box<dyn Error + Send>>;

pub(crate) struct Repl<Context> {
    prompt: String,
    commands: Subcommands<Context>,
    history_file: PathBuf,
    multiline_input: bool,
}

impl<Context: ReplContext + 'static> Repl<Context> {
    const HELP: &'static str = "help";
    const EXIT: &'static str = "exit";

    pub(crate) fn new(prompt: String, history_file: PathBuf, multiline_input: bool) -> Self {
        let subcommands = Subcommands::new("")
            .add(CommandOption::new(Self::EXIT, "Exit", do_exit))
            .add(CommandOption::new(Self::HELP, "Print help menu", help_menu));
        Self { prompt, commands: subcommands, history_file, multiline_input }
    }

    pub(crate) fn add(mut self, command: impl Command<Context> + 'static) -> Self {
        self.commands = self.commands.add(command);
        self
    }

    pub(crate) fn add_default(mut self, command: CommandDefault<Context>) -> Self {
        self.commands = self.commands.add_default(command);
        self
    }

    pub(crate) fn interactive_once(&self, context: &mut Context) -> ControlFlow<(), ReplResult> {
        let mut editor = RustylineReader::new(self.commands.clone(), self.history_file.clone(), self.multiline_input);
        match editor.readline(&self.prompt) {
            Ok(line) => {
                if !line.trim().is_empty() {
                    Continue(self.execute_once(context, &line))
                } else {
                    Continue(Ok(()))
                }
            }
            Err(ReadlineError::Interrupted) | Err(ReadlineError::Eof) => Break(()),
            Err(err) => Continue(Err(Box::new(err))),
        }
    }

    pub(crate) fn execute_once(&self, context: &mut Context, line: &str) -> ReplResult {
        self.commands.execute(context, line)
    }

    pub(crate) fn help(&self) -> String {
        let usages_descriptions: Vec<(String, &'static str)> = self.commands.usage_description().collect();

        let widest_usage = usages_descriptions.iter().map(|(usage, _)| usage.len()).max().unwrap_or(0);
        let usage_width = widest_usage + 4;
        usages_descriptions
            .iter()
            .map(|(usage, description)| format!("{:<width$}{}", usage, description, width = usage_width))
            .collect::<Vec<_>>()
            .join("\n")
    }

    pub(crate) fn prompt(&self) -> &str {
        &self.prompt
    }
}

fn help_menu<Context: ReplContext + 'static>(context: &mut Context, _input: &[String]) -> ReplResult {
    println!("{}", context.current_repl().help());
    Ok(())
}

fn do_exit<Context: ReplContext + 'static>(_context: &mut Context, _input: &[String]) -> ReplResult {
    exit(0);
}
