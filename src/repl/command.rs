/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{
    borrow::Cow,
    cmp::Ordering,
    collections::BTreeSet,
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

use crate::repl::{line_reader::LineReaderHidden, ReplContext};

pub(crate) trait Command<Context> {
    fn token(&self) -> &CommandToken;

    // recursively try to complete either this command, or any subcommand, based on the input
    // where the input was already matched against & excludes any parent commands
    fn compute_completions(&self, input: &str) -> Vec<String>;

    fn match_first<'a>(
        &self,
        input: &'a str,
        coerce_to_one_line: bool,
    ) -> Result<Option<(&dyn ExecutableCommand<Context>, Vec<String>, usize)>, Box<dyn Error + Send>>;

    fn usage_description(&self) -> Box<dyn Iterator<Item = (String, &'static str)> + '_>;
}

impl<Context> Ord for dyn Command<Context> {
    fn cmp(&self, other: &Self) -> Ordering {
        self.token().cmp(other.token())
    }
}

impl<Context> PartialOrd for dyn Command<Context> {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(Ord::cmp(self, other))
    }
}

impl<Context> Eq for dyn Command<Context> {}

impl<Context> PartialEq for dyn Command<Context> {
    fn eq(&self, other: &Self) -> bool {
        self.token() == other.token()
    }
}

pub(crate) trait ExecutableCommand<Context>: Command<Context> {
    // execute this command with the provided input
    fn execute(&self, context: &mut Context, args: Vec<String>) -> CommandResult;
}

pub(crate) type CommandExecutor<Context> = fn(&mut Context, &[String]) -> CommandResult;
pub(crate) type CommandResult = Result<(), Box<dyn Error + Send>>;

pub(crate) struct Subcommand<Context> {
    token: CommandToken,
    subcommands: BTreeSet<Rc<dyn Command<Context>>>,
}

impl<Context> Subcommand<Context> {
    pub(crate) fn new(token: impl Into<CommandToken>) -> Self {
        Self { token: token.into(), subcommands: BTreeSet::new() }
    }

    pub(crate) fn add(mut self, command: impl Command<Context> + 'static) -> Self {
        if self.subcommands.iter().any(|cmd| cmd.token() == command.token()) {
            panic!("Duplicate subcommands with token: {}", command.token());
        }
        self.subcommands.insert(Rc::new(command));
        self
    }
}

impl<Context: ReplContext> Command<Context> for Subcommand<Context> {
    fn token(&self) -> &CommandToken {
        &self.token
    }

    fn compute_completions(&self, input: &str) -> Vec<String> {
        if input.ends_with(char::is_whitespace) {
            return Vec::with_capacity(0);
        }
        if let Some((_, remaining, _)) = self.token.match_(input) {
            if remaining.starts_with(char::is_whitespace) || remaining == input {
                self.subcommands.iter().flat_map(|cmd| cmd.compute_completions(remaining.trim_start())).collect()
            } else {
                Vec::with_capacity(0)
            }
        } else if self.token.completes(input) {
            vec![self.token.token.to_owned()]
        } else {
            Vec::with_capacity(0)
        }
    }

    fn match_first<'a>(
        &self,
        input: &'a str,
        coerce_to_one_line: bool,
    ) -> Result<Option<(&dyn ExecutableCommand<Context>, Vec<String>, usize)>, Box<dyn Error + Send>> {
        match self.token.match_(input) {
            None => Ok(None),
            Some((_token, remaining, token_end_index)) => {
                // rev forces longest match first
                for subcommand in self.subcommands.iter().rev() {
                    match subcommand.match_first(remaining, coerce_to_one_line)? {
                        None => continue,
                        Some((command, remaining_after_subcommand, command_end_index)) => {
                            // since we only reveal the substring to the subcommand
                            // we need to extend the index by whatever we removed from the start
                            return Ok(Some((
                                command,
                                remaining_after_subcommand,
                                token_end_index + command_end_index,
                            )));
                        }
                    }
                }
                Err(Box::new(ReplError {
                    message: format!(
                        "Unrecognised '{}' subcommand: '{}', please type 'help' to see the help menu.",
                        self.token,
                        remaining.trim()
                    ),
                }))
            }
        }
    }

    fn usage_description(&self) -> Box<dyn Iterator<Item = (String, &'static str)> + '_> {
        Box::new(self.subcommands.iter().flat_map(|command| {
            command.usage_description().map(|(usage, description)| {
                if self.token().token.is_empty() {
                    (usage, description)
                } else {
                    (format!("{} {}", self.token, usage), description)
                }
            })
        }))
    }
}

impl<Context> Clone for Subcommand<Context> {
    fn clone(&self) -> Self {
        Self { token: self.token, subcommands: self.subcommands.clone() }
    }
}

pub(crate) struct CommandLeaf<Context> {
    token: CommandToken,
    description: &'static str,
    arguments: Vec<CommandInput>,
    executor: CommandExecutor<Context>,
}

impl<Context> CommandLeaf<Context> {
    pub(crate) fn new(
        token: impl Into<CommandToken>,
        description: &'static str,
        executor: CommandExecutor<Context>,
    ) -> Self {
        Self::new_with_inputs(token, description, vec![], executor)
    }

    pub(crate) fn new_with_input(
        token: impl Into<CommandToken>,
        description: &'static str,
        arguments: CommandInput,
        executor: CommandExecutor<Context>,
    ) -> Self {
        Self::new_with_inputs(token, description, vec![arguments], executor)
    }

    pub(crate) fn new_with_inputs(
        token: impl Into<CommandToken>,
        description: &'static str,
        arguments: Vec<CommandInput>,
        executor: CommandExecutor<Context>,
    ) -> Self {
        Self { token: token.into(), description, arguments, executor }
    }
}

impl<Context: ReplContext> Command<Context> for CommandLeaf<Context> {
    fn token(&self) -> &CommandToken {
        &self.token
    }

    fn compute_completions(&self, input: &str) -> Vec<String> {
        if input.ends_with(char::is_whitespace) {
            return Vec::with_capacity(0);
        }
        if let Some((_, remaining, _)) = self.token.match_(input) {
            let args = remaining.trim_start().split_whitespace();
            match args.enumerate().last() {
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
        } else if self.token.completes(input) {
            vec![self.token.token.to_owned()]
        } else {
            Vec::with_capacity(0)
        }
    }

    fn match_first<'a>(
        &self,
        input: &'a str,
        coerce_to_one_line: bool,
    ) -> Result<Option<(&dyn ExecutableCommand<Context>, Vec<String>, usize)>, Box<dyn Error + Send>> {
        match self.token.match_(input) {
            Some((_token, mut remaining, token_end_index)) => {
                let mut parsed_args: Vec<String> = Vec::new();
                let mut command_end_index = token_end_index;
                for (index, argument) in self.arguments.iter().enumerate() {
                    let (arg_value, remaining_input) = match argument.read_end_index_from(remaining, coerce_to_one_line)
                    {
                        Some(end_index) => {
                            // if accepted 0 inputs, even if it is a match, we should not accept this command
                            if remaining[0..end_index].is_empty() {
                                return Ok(None);
                            }
                            command_end_index += end_index;
                            (remaining[0..end_index].trim().to_owned(), &remaining[end_index..])
                        }
                        None => {
                            if argument.is_hidden() {
                                (argument.request_hidden()?, remaining)
                            } else {
                                return Err(Box::new(ReplError {
                                    message: format!("Missing argument {}: {}", index + 1, argument.usage),
                                }));
                            }
                        }
                    };
                    parsed_args.push(arg_value);
                    remaining = remaining_input;
                }
                Ok(Some((self as &dyn ExecutableCommand<Context>, parsed_args, command_end_index)))
            }
            None => Ok(None),
        }
    }

    fn usage_description(&self) -> Box<dyn Iterator<Item = (String, &'static str)> + '_> {
        let mut usage = format!("{}", self.token);
        for arg in &self.arguments {
            usage = format!("{} <{}>", usage, arg.usage());
        }
        Box::new([(usage, self.description)].into_iter())
    }
}

impl<Context: ReplContext> ExecutableCommand<Context> for CommandLeaf<Context> {
    fn execute(&self, context: &mut Context, args: Vec<String>) -> CommandResult {
        (self.executor)(context, &args)
    }
}

pub(crate) type InputReaderFn = for<'a> fn(&'a str, bool) -> Option<usize>;
// since we can't pass the context in through RustyLine's completion/hinting system, we have to hack around it
// this type lets us construct a closure capturing whatever we want
pub(crate) type InputCompleterFn = dyn for<'a> Fn(&'a str) -> Vec<String>;

pub(crate) struct CommandInput {
    usage: &'static str,
    reader: InputReaderFn,
    hidden_reader: Option<InputReaderFn>,
    completer: Option<Box<InputCompleterFn>>,
}

impl CommandInput {
    pub(crate) fn new(
        usage: &'static str,
        reader: InputReaderFn,
        hidden_reader: Option<InputReaderFn>,
        completer: Option<Box<InputCompleterFn>>,
    ) -> Self {
        Self { usage, reader, hidden_reader, completer }
    }

    fn read_end_index_from<'a>(&self, input: &'a str, coerce_to_one_line: bool) -> Option<usize> {
        (self.reader)(input, coerce_to_one_line)
    }

    fn is_hidden(&self) -> bool {
        self.hidden_reader.is_some()
    }

    fn request_hidden(&self) -> Result<String, Box<dyn Error + Send>> {
        match self.hidden_reader.as_ref() {
            Some(reader) => {
                let string = LineReaderHidden::new().readline(&format!("{}: ", self.usage));
                let input_end = match reader(&string, true) {
                    None => {
                        return Err(Box::new(ReplError {
                            message: format!("Could not read input for '{}'", self.usage),
                        }))
                    }
                    Some(end) => end,
                };
                Ok(string[0..input_end].to_owned())
            }
            None => Err(Box::new(ReplError {
                message: format!(
                    "{} cannot be requested as a hidden parameter and must be entered as part of the command.",
                    self.usage
                ),
            })),
        }
    }

    // Return completions that are longer than the input
    fn completions(&self, input: &str) -> Vec<String> {
        let input = match self.read_end_index_from(input, true) {
            None => return Vec::with_capacity(0),
            Some(index) => &input[0..index],
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

pub(crate) fn get_word(input: &str, _coerce_to_one_line: bool) -> Option<usize> {
    if input.trim().is_empty() {
        None
    } else {
        let after_starting_whitespace = input.find(|c: char| !c.is_whitespace()).unwrap_or(0);
        match input[after_starting_whitespace..].find(char::is_whitespace) {
            None => Some(input.len()),
            Some(pos) => Some(after_starting_whitespace + pos),
        }
    }
}

pub(crate) fn get_to_empty_line(mut input: &str, coerce_to_one_line: bool) -> Option<usize> {
    if coerce_to_one_line {
        Some(input.len())
    } else {
        const PATTERN: &str = "\n";
        let mut pos = 0;
        while let Some(newline_pos) = input.find(PATTERN) {
            let next_newline_pos = match input[newline_pos + 1..].find(PATTERN) {
                None => return None,
                Some(next_newline_pos) => newline_pos + 1 + next_newline_pos,
            };
            pos += newline_pos;
            if input[newline_pos..next_newline_pos].trim().is_empty() {
                return Some(pos);
            }
            input = &input[newline_pos + 1..];
            pos += 1;
        }
        None
    }
}

#[derive(Copy, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct CommandToken {
    token: &'static str,
}

impl CommandToken {
    fn new(token: &'static str) -> Self {
        Self { token }
    }

    fn match_<'a>(&self, input: &'a str) -> Option<(&'a str, &'a str, usize)> {
        match input.find(self.token) {
            None => None,
            Some(pos) => {
                if (&input[0..pos]).trim_matches(char::is_whitespace).is_empty() {
                    let end = pos + self.token.len();
                    Some((&input[0..end], &input[end..], end))
                } else {
                    None
                }
            }
        }
    }

    fn completes(&self, input: &str) -> bool {
        self.token.starts_with(input.trim())
    }
}

impl Into<CommandToken> for &'static str {
    fn into(self) -> CommandToken {
        CommandToken::new(self)
    }
}

impl Display for CommandToken {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.token)
    }
}

pub(crate) trait CommandDefinitions: Highlighter + Hinter + Completer {
    fn is_complete_command(&self, input: &str) -> bool;
}

impl<Context: ReplContext> CommandDefinitions for Subcommand<Context> {
    fn is_complete_command(&self, input: &str) -> bool {
        matches!(Command::match_first(self, input, false), Ok(Some(_)))
    }
}

impl<Context: ReplContext> Completer for Subcommand<Context> {
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

impl<Context: ReplContext> Hinter for Subcommand<Context> {
    type Hint = String;

    fn hint(&self, line: &str, pos: usize, _ctx: &rustyline::Context<'_>) -> Option<Self::Hint> {
        let (_, candidates) = self.complete(line, pos, _ctx).ok()?;
        let (_, last_word) = extract_word(line, pos, None, char::is_whitespace);

        if candidates.len() == 1 {
            let candidate = candidates.into_iter().next().unwrap();
            if candidate.len() < last_word.len() {
                None
            } else {
                Some(candidate[last_word.len()..].to_owned())
            }
        } else {
            None
        }
    }
}

impl<Context: ReplContext> Highlighter for Subcommand<Context> {
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
