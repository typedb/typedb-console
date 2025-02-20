/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::{borrow::Cow, path::PathBuf};

use rustyline::{
    completion::Completer,
    highlight::Highlighter,
    hint::Hinter,
    history::FileHistory,
    validate::{ValidationContext, ValidationResult, Validator},
    Cmd, CompletionType, ConditionalEventHandler, Config, Editor, Event, EventHandler, Helper, KeyEvent, Movement,
    RepeatCount,
};

use crate::repl::command::CommandDefinitions;

pub(crate) struct RustylineReader<H: Helper> {
    history_file: PathBuf,
    editor: Editor<H, FileHistory>,
}

impl<H: CommandDefinitions> RustylineReader<EditorHelper<H>> {
    pub(crate) fn new(command_helper: H, history_file: PathBuf, multiline: bool) -> Self {
        let mut builder = Config::builder().completion_type(CompletionType::Circular).auto_add_history(true);
        let config = builder.build();
        let history = FileHistory::new();

        let mut editor = Editor::with_history(config, history).unwrap(); // TODO unwrap

        let helper = EditorHelper { command_definitions: command_helper, multiline };

        editor.set_helper(Some(helper));
        editor.bind_sequence(
            Event::from(KeyEvent::ctrl('c')),
            EventHandler::Conditional(Box::new(InterruptIfEmptyElseClear {})),
        );
        let _ = editor.load_history(&history_file);
        Self { editor, history_file }
    }

    pub(crate) fn readline(&mut self, prompt: &str) -> rustyline::Result<String> {
        match self.editor.readline(prompt) {
            Ok(line) => {
                let _ = self.editor.append_history(&self.history_file);
                Ok(line)
            }
            Err(err) => Err(err),
        }
    }
}

pub(crate) struct EditorHelper<H> {
    command_definitions: H,
    multiline: bool,
}

impl<H: CommandDefinitions> Helper for EditorHelper<H> {}

impl<H: CommandDefinitions> Completer for EditorHelper<H> {
    type Candidate = H::Candidate;

    fn complete(
        &self,
        line: &str,
        pos: usize,
        ctx: &rustyline::Context<'_>,
    ) -> rustyline::Result<(usize, Vec<Self::Candidate>)> {
        self.command_definitions.complete(line, pos, ctx)
    }
}

impl<H: CommandDefinitions> Hinter for EditorHelper<H> {
    type Hint = H::Hint;

    fn hint(&self, line: &str, pos: usize, ctx: &rustyline::Context<'_>) -> Option<Self::Hint> {
        self.command_definitions.hint(line, pos, ctx)
    }
}

impl<H: CommandDefinitions> Highlighter for EditorHelper<H> {
    fn highlight_hint<'h>(&self, hint: &'h str) -> Cow<'h, str> {
        self.command_definitions.highlight_hint(hint)
    }
}

impl<H: CommandDefinitions> Validator for EditorHelper<H> {
    fn validate(&self, ctx: &mut ValidationContext) -> rustyline::Result<ValidationResult> {
        if self.multiline {
            let validator = MultilineValidator { definitions: &self.command_definitions };
            validator.validate(ctx)
        } else {
            Ok(ValidationResult::Valid(None))
        }
    }
}

struct InterruptIfEmptyElseClear {}

impl ConditionalEventHandler for InterruptIfEmptyElseClear {
    fn handle(&self, _evt: &Event, _n: RepeatCount, _positive: bool, ctx: &rustyline::EventContext) -> Option<Cmd> {
        if ctx.line().is_empty() {
            Some(Cmd::Interrupt)
        } else {
            Some(Cmd::Kill(Movement::BeginningOfBuffer))
        }
    }
}

struct MultilineValidator<'a, D: CommandDefinitions> {
    definitions: &'a D,
}

impl<'a, D: CommandDefinitions> Validator for MultilineValidator<'a, D> {
    fn validate(&self, ctx: &mut ValidationContext) -> rustyline::Result<ValidationResult> {
        /*
        RustLine trims off the last newline by the time we get here. As a result, when we have input:
        match  [newline]

        We only see 'match' without the newline. However, we can assume there is a newline at the end by the time we get here.
        As a result, when we want to validate a double-newline entry the user, we just have to check if the last character is newline!
        */
        if ctx.input().trim_matches(|c: char| c.is_whitespace() && c != '\n').ends_with("\n")
            || self.definitions.is_complete_command(ctx.input())
        {
            Ok(ValidationResult::Valid(None))
        } else {
            Ok(ValidationResult::Incomplete)
        }
    }
}

pub(crate) struct LineReaderHidden {}

impl LineReaderHidden {
    pub(crate) fn new() -> Self {
        Self {}
    }

    pub(crate) fn readline(&mut self, prompt: &str) -> String {
        rpassword::prompt_password(prompt).unwrap()
    }
}
