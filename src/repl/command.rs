/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    borrow::Cow,
    error::Error,
    fmt,
    fmt::{Debug, Display, Formatter},
    rc::Rc,
};

use rustyline::{
    completion::{extract_word, Completer},
    highlight::Highlighter,
    hint::Hinter,
};

use crate::repl::{line_reader::LineReaderHidden, ReplResult};

pub(crate) trait Command<Context> {
    // single-word token
    fn token(&self) -> &'static str;

    // recursively try to complete either this command, or any subcommand, based on the input
    // where the input was already matched against & excludes any parent commands
    fn compute_completions(&self, input: &str) -> Vec<String>;

    fn is_complete_command(&self, input: &str) -> bool;

    // execute this command with the provided input
    fn execute(&self, context: &mut Context, input: &str) -> ReplResult;

    fn usage_description(&self) -> Box<dyn Iterator<Item = (String, &'static str)> + '_>;
}

pub(crate) type CommandExecutor<Context> = fn(&mut Context, &[String]) -> ReplResult;

pub(crate) struct CommandOption<Context> {
    token: &'static str,
    description: &'static str,
    arguments: Vec<CommandInput>,
    executor: CommandExecutor<Context>,
}

impl<Context> CommandOption<Context> {
    pub(crate) fn new(token: &'static str, description: &'static str, executor: CommandExecutor<Context>) -> Self {
        Self::new_with_inputs(token, description, vec![], executor)
    }

    pub(crate) fn new_with_input(
        token: &'static str,
        description: &'static str,
        arguments: CommandInput,
        executor: CommandExecutor<Context>,
    ) -> Self {
        Self::new_with_inputs(token, description, vec![arguments], executor)
    }

    pub(crate) fn new_with_inputs(
        token: &'static str,
        description: &'static str,
        arguments: Vec<CommandInput>,
        executor: CommandExecutor<Context>,
    ) -> Self {
        Self { token, description, arguments, executor }
    }
}

impl<Context> Command<Context> for CommandOption<Context> {
    fn token(&self) -> &'static str {
        self.token
    }

    fn compute_completions(&self, input: &str) -> Vec<String> {
        if input.ends_with(char::is_whitespace) {
            return Vec::with_capacity(0);
        }
        let mut inputs = input.trim_start().split_whitespace();
        let command = match inputs.next() {
            None => return Vec::with_capacity(0),
            Some(command) => command,
        };
        let remaining = input.trim_start().strip_prefix(command).unwrap();
        if self.token == command {
            match inputs.enumerate().last() {
                None => {
                    // no more inputs are available
                    Vec::with_capacity(0)
                }
                Some((last_arg_index, last_arg)) => {
                    if last_arg_index < self.arguments.len() {
                        self.arguments[last_arg_index].completions(last_arg)
                    } else {
                        Vec::with_capacity(0)
                    }
                }
            }
        } else if self.token.starts_with(command) && remaining.trim().is_empty() {
            vec![self.token.to_owned()]
        } else {
            Vec::with_capacity(0)
        }
    }

    fn is_complete_command(&self, input: &str) -> bool {
        let mut inputs = input.trim_start().split_whitespace().peekable();
        let command = match inputs.next() {
            None => return false,
            Some(command) => command,
        };
        self.token == command
    }

    fn execute<'a>(&'a self, context: &mut Context, mut input: &'a str) -> ReplResult {
        let mut arguments: Vec<String> = Vec::new();
        for (index, argument) in self.arguments.iter().enumerate() {
            let (arg_value, remaining_input) = match argument.get(input) {
                None => {
                    if argument.is_hidden() {
                        (argument.get_as_hidden().unwrap(), input)
                    } else {
                        return Err(Box::new(ReplError {
                            message: format!("Missing argument {}: {}", index + 1, argument.usage),
                        }));
                    }
                }
                Some((arg_value, remaining_input)) => (arg_value.to_owned(), remaining_input),
            };
            arguments.push(arg_value);
            input = remaining_input;
        }
        if !input.trim().is_empty() {
            return Err(Box::new(ReplError { message: format!("Unexpected extra arguments: {}", input) }));
        }
        (self.executor)(context, &arguments)
    }

    fn usage_description(&self) -> Box<dyn Iterator<Item = (String, &'static str)> + '_> {
        let mut usage = format!("{}", self.token);
        for arg in &self.arguments {
            usage = format!("{} <{}>", usage, arg.usage());
        }
        Box::new([(usage, self.description)].into_iter())
    }
}

pub(crate) type InputReaderFn = for<'a> fn(&'a str) -> Option<(&'a str, &'a str)>;
pub(crate) type HiddenReaderFn = for<'a> fn(&'a str) -> Option<(&'a str, &'a str)>;
// since we can't pass the context in through RustyLine's completion/hinting system, we have to hack around it
// this type lets us construct a closure capturing whatever we want
pub(crate) type InputCompleterFn = dyn for<'a> Fn(&'a str) -> Vec<String>;

pub(crate) struct CommandInput {
    usage: &'static str,
    reader: InputReaderFn,
    hidden_reader: Option<HiddenReaderFn>,
    completer: Option<Box<InputCompleterFn>>,
}

impl CommandInput {
    pub(crate) fn new(
        usage: &'static str,
        reader: InputReaderFn,
        hidden_reader: Option<HiddenReaderFn>,
        completer: Option<Box<InputCompleterFn>>,
    ) -> Self {
        Self { usage, reader, hidden_reader, completer }
    }

    fn get<'a>(&self, input: &'a str) -> Option<(&'a str, &'a str)> {
        (self.reader)(input)
    }

    fn is_hidden(&self) -> bool {
        self.hidden_reader.is_some()
    }

    fn get_as_hidden(&self) -> Result<String, ReplError> {
        Ok(LineReaderHidden::new().readline(&format!("{}: ", self.usage)))
    }

    // Return completions that are longer than the input
    fn completions(&self, input: &str) -> Vec<String> {
        let input = match self.get(input) {
            None => return Vec::with_capacity(0),
            Some((input, _)) => input,
        };
        match &self.completer {
            None => Vec::with_capacity(0),
            Some(completer) => completer(input).into_iter().filter(|completion| completion != input).collect(),
        }
    }

    fn usage(&self) -> String {
        if self.hidden_reader.is_some() {
            format!("{} (enter in hidden input)", self.usage)
        } else {
            self.usage.to_owned()
        }
    }
}

pub(crate) fn get_word(input: &str) -> Option<(&str, &str)> {
    if input.is_empty() {
        None
    } else {
        Some(input.trim_start().split_once(char::is_whitespace).unwrap_or((input, "")))
    }
}

pub(crate) fn get_multiline_query(input: &str) -> Option<(&str, &str)> {
    let mut previous_newline: usize = 0;
    while let Some(newline_pos) = input.find("\n") {
        // let before = &input[0..newline_pos];
        // let after = &input[newline_pos..];
        let line_before = &input[previous_newline..newline_pos];
        if line_before.trim().is_empty() {
            return (current_query_range
        }

    }


    let mut lines = input.lines();
    let last_line = match lines.next() {
        None => return None,
        Some(line) => line,
    };
    if last_line.trim().is_empty() {

    }

    Some((input, ""))
}

pub(crate) struct CommandDefault<Context> {
    description: &'static str,
    reader: CommandInput,
    executor: CommandExecutor<Context>,
}

impl<Context> CommandDefault<Context> {
    pub(crate) fn new(description: &'static str, reader: CommandInput, executor: CommandExecutor<Context>) -> Self {
        Self { description, reader, executor }
    }

    fn execute(&self, context: &mut Context, input: &str) -> ReplResult {
        let (argument, remainder) = match self.reader.get(input) {
            None => {
                if self.reader.is_hidden() {
                    (self.reader.get_as_hidden().unwrap(), input)
                } else {
                    return Err(Box::new(ReplError {
                        message: format!("'{}' could not parse from input: {}", self.reader.usage(), input),
                    }));
                }
            }
            Some((argument, remainder)) => (argument.to_owned(), remainder),
        };
        if !remainder.trim().is_empty() {
            return Err(Box::new(ReplError {
                message: format!("Unexpected extra inputs for {}: {}", self.description, remainder),
            }));
        }
        (self.executor)(context, &[argument])
    }

    fn is_complete_command(&self, input: &str) -> bool {
        self.reader.get(input)
    }

    fn usage_description(&self) -> (String, &'static str) {
        ("*".to_owned(), self.description)
    }
}

pub(crate) struct Subcommands<Context> {
    token: &'static str,
    subcommands: Vec<Rc<dyn Command<Context>>>,
    default: Option<Rc<CommandDefault<Context>>>,
}

impl<Context> Subcommands<Context> {
    pub(crate) fn new(token: &'static str) -> Self {
        Self { token, subcommands: Vec::new(), default: None }
    }

    pub(crate) fn add(mut self, command: impl Command<Context> + 'static) -> Self {
        if self.subcommands.iter().any(|cmd| cmd.token() == command.token()) {
            panic!("Duplicate subcommands with token: {}", command.token());
        }
        self.subcommands.push(Rc::new(command));
        self
    }

    pub(crate) fn add_default(mut self, command: CommandDefault<Context>) -> Self {
        self.default = Some(Rc::new(command));
        self
    }
}

impl<Context> Command<Context> for Subcommands<Context> {
    fn token(&self) -> &'static str {
        self.token
    }

    fn compute_completions(&self, input: &str) -> Vec<String> {
        if input.ends_with(char::is_whitespace) {
            return Vec::with_capacity(0);
        }
        if self.token.is_empty() {
            return self.subcommands.iter().flat_map(|cmd| cmd.compute_completions(input)).collect();
        }

        let command = input.trim_start().split_whitespace().next().unwrap();
        let remaining_input = input.trim_start().strip_prefix(command).unwrap();
        if self.token == command {
            if remaining_input.starts_with(char::is_whitespace) {
                self.subcommands.iter().flat_map(|cmd| cmd.compute_completions(remaining_input.trim_start())).collect()
            } else {
                Vec::with_capacity(0)
            }
        } else if self.token.starts_with(command) && remaining_input.trim().is_empty() {
            vec![self.token.to_owned()]
        } else {
            Vec::with_capacity(0)
        }
    }

    fn is_complete_command(&self, input: &str) -> bool {
        if self.token.is_empty() {
            return self.subcommands.iter().any(|cmd| cmd.is_complete_command(input));
        }

        let command = input.trim_start().split_whitespace().next().unwrap();
        if self.token == command {
            let remaining_input = input.trim_start().strip_prefix(command).unwrap();
            if remaining_input.starts_with(char::is_whitespace) {
                self.subcommands.iter().any(|cmd| cmd.is_complete_command(remaining_input.trim_start()))
            } else {
                false
            }
        } else if let Some(default) = self.default.as_ref() {
            default.is_complete_command(input)
        } else {
            false
        }
    }

    fn execute(&self, context: &mut Context, input: &str) -> ReplResult {
        let (command, remainder) = match get_word(input) {
            None => {
                return Err(Box::new(ReplError {
                    message: format!(
                        "Failed to read {} command from input {}, please type 'help' to see the help menu.",
                        self.token, input
                    ),
                }));
            }
            Some((command, remainder)) => (command.trim(), remainder),
        };
        for subcommand in &self.subcommands {
            if command == subcommand.token() {
                return subcommand.execute(context, remainder.trim_start());
            }
        }
        if let Some(default) = &self.default {
            default.execute(context, input)
        } else {
            Err(Box::new(ReplError {
                message: format!("Unrecognised command: {}, please type 'help' to see the help menu.", input),
            }))
        }
    }

    fn usage_description(&self) -> Box<dyn Iterator<Item = (String, &'static str)> + '_> {
        Box::new(
            self.subcommands
                .iter()
                .rev()
                .flat_map(|command| {
                    command.usage_description().map(|(usage, description)| {
                        if self.token().is_empty() {
                            (usage, description)
                        } else {
                            (format!("{} {}", self.token, usage), description)
                        }
                    })
                })
                .chain(self.default.iter().map(|default| default.usage_description())),
        )
    }
}

impl<Context> Clone for Subcommands<Context> {
    fn clone(&self) -> Self {
        Self { token: self.token, subcommands: self.subcommands.clone(), default: self.default.clone() }
    }
}

pub(crate) trait CommandDefinitions: Highlighter + Hinter + Completer {
    fn is_complete_command(&self, input: &str) -> bool;
}

impl<Context> CommandDefinitions for Subcommands<Context> {
    fn is_complete_command(&self, input: &str) -> bool {
        Command::is_complete_command(self, input)
    }
}

impl<Context> Completer for Subcommands<Context> {
    type Candidate = String;

    fn complete(
        &self,
        line: &str,
        pos: usize,
        _ctx: &rustyline::Context<'_>,
    ) -> rustyline::Result<(usize, Vec<Self::Candidate>)> {
        if line.trim().is_empty() {
            Ok((0, Vec::with_capacity(0)))
        } else {
            let (last_word_boundary, _) = extract_word(line, pos, None, char::is_whitespace);
            Ok((last_word_boundary, self.compute_completions(line)))
        }
    }
}

impl<Context> Hinter for Subcommands<Context> {
    type Hint = String;

    fn hint(&self, line: &str, pos: usize, _ctx: &rustyline::Context<'_>) -> Option<Self::Hint> {
        let (_, candidates) = self.complete(line, pos, _ctx).ok()?;
        let (_, last_word) = extract_word(line, pos, None, char::is_whitespace);

        if candidates.len() == 1 {
            let candidate = candidates.into_iter().next().unwrap();
            let hint = candidate[last_word.len()..].to_owned();
            Some(hint)
        } else {
            None
        }
    }
}

impl<Context> Highlighter for Subcommands<Context> {
    fn highlight_hint<'h>(&self, hint: &'h str) -> Cow<'h, str> {
        Cow::Owned(format!("\x1b[37m{}\x1b[0m", hint))
    }
}

pub(crate) struct ReplError {
    pub(crate) message: String,
}

impl Debug for ReplError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        fmt::Display::fmt(self, f)
    }
}

impl Display for ReplError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl Error for ReplError {}
