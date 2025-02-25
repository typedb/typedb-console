/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{error::Error, path::PathBuf, process::exit};

use rustyline::{history::MemHistory, Config};

use crate::repl::{
    command::{Command, CommandLeaf, CommandResult, ExecutableCommand, Subcommand},
    line_reader::RustylineReader,
};

pub(crate) mod command;
pub(crate) mod line_reader;

pub(crate) trait ReplContext: Sized {
    fn current_repl(&self) -> &Repl<Self>;
}

pub(crate) type ReplResult<'a> = Result<Option<&'a str>, Box<dyn Error + Send>>;

pub(crate) struct Repl<Context> {
    prompt: String,
    commands: Subcommand<Context>,
    history_file: PathBuf,
    multiline_input: bool,
    on_finish: Option<fn(&mut Context) -> ()>,
}

impl<Context: ReplContext + 'static> Repl<Context> {
    const HELP: &'static str = "help";
    const EXIT: &'static str = "exit";
    const CLEAR: &'static str = "clear";

    pub(crate) fn new(
        prompt: String,
        history_file: PathBuf,
        multiline_input: bool,
        on_finish: Option<fn(&mut Context) -> ()>,
    ) -> Self {
        let subcommands = Subcommand::new("")
            .add(CommandLeaf::new(Self::EXIT, "Exit", do_exit))
            .add(CommandLeaf::new(Self::HELP, "Print help menu", help_menu))
            .add(CommandLeaf::new(Self::CLEAR, "Clear the console.", clear));
        Self { prompt, commands: subcommands, history_file, multiline_input, on_finish }
    }

    pub(crate) fn add(mut self, command: impl Command<Context> + 'static) -> Self {
        self.commands = self.commands.add(command);
        self
    }

    pub(crate) fn get_input(&self) -> rustyline::Result<String> {
        let mut editor = RustylineReader::new(self.commands.clone(), self.history_file.clone(), self.multiline_input);
        editor.readline(&self.prompt)
    }

    pub(crate) fn match_first_command<'a>(
        &self,
        input: &'a str,
        coerce_to_one_line: bool,
    ) -> Result<Option<(&dyn ExecutableCommand<Context>, Vec<String>, usize)>, Box<dyn Error + Send>> {
        self.commands.match_first(input, coerce_to_one_line)
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

    pub(crate) fn finished(&self, context: &mut Context) {
        if let Some(on_finish) = self.on_finish {
            on_finish(context)
        }
    }
}

fn clear<Context: ReplContext + 'static>(_context: &mut Context, _input: &[String]) -> CommandResult {
    let mut editor: rustyline::Editor<(), _> =
        rustyline::Editor::with_history(Config::default(), MemHistory::new()).unwrap();
    let _ = editor.clear_screen();
    Ok(())
}

fn help_menu<Context: ReplContext + 'static>(context: &mut Context, _input: &[String]) -> CommandResult {
    println!("{}", context.current_repl().help());
    Ok(())
}

fn do_exit<Context: ReplContext + 'static>(_context: &mut Context, _input: &[String]) -> CommandResult {
    exit(0);
}
