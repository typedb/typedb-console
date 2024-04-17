/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console.command;

import com.vaticle.typedb.common.collection.Either;
import com.vaticle.typedb.common.collection.Pair;
import com.vaticle.typedb.console.common.Utils;
import com.vaticle.typedb.console.common.exception.TypeDBConsoleException;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.vaticle.typedb.common.collection.Collections.pair;
import static com.vaticle.typedb.common.collection.Collections.set;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_CLEAR_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_CLOSE_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_COMMIT_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_EXIT_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_HELP_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_OPTIONAL_ARG;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_ROLLBACK_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_SOURCE_ARGS;

public interface TransactionREPLCommand {

    default boolean isExit() {
        return false;
    }

    default TransactionREPLCommand.Exit asExit() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isHelp() {
        return false;
    }

    default TransactionREPLCommand.Help asHelp() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isClear() {
        return false;
    }

    default TransactionREPLCommand.Clear asClear() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isCommit() {
        return false;
    }

    default TransactionREPLCommand.Commit asCommit() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isRollback() {
        return false;
    }

    default TransactionREPLCommand.Rollback asRollback() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isClose() {
        return false;
    }

    default TransactionREPLCommand.Close asClose() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isSource() {
        return false;
    }

    default TransactionREPLCommand.Source asSource() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isQuery() {
        return false;
    }

    default TransactionREPLCommand.Query asQuery() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    class Exit implements TransactionREPLCommand {

        private static final String token = "exit";
        private static final String helpCommand = token;
        private static final String description = "Exit console";
        private static final int args = 0;

        @Override
        public boolean isExit() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Exit asExit() {
            return this;
        }
    }

    class Help implements TransactionREPLCommand {

        private static final String token = "help";
        private static final String helpCommand = token;
        private static final String description = "Print this help menu";
        private static final int args = 0;

        @Override
        public boolean isHelp() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Help asHelp() {
            return this;
        }
    }

    class Clear implements TransactionREPLCommand {

        private static final String token = "clear";
        private static final String helpCommand = token;
        private static final String description = "Clear console screen";
        private static final int args = 0;

        @Override
        public boolean isClear() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Clear asClear() {
            return this;
        }
    }

    class Commit implements TransactionREPLCommand {

        private static final String token = "commit";
        private static final String helpCommand = token;
        private static final String description = "Commit the transaction changes and close transaction";
        private static final int args = 0;

        @Override
        public boolean isCommit() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Commit asCommit() {
            return this;
        }
    }

    class Rollback implements TransactionREPLCommand {

        private static final String token = "rollback";
        private static final String helpCommand = token;
        private static final String description = "Rollback the transaction to the beginning state";
        private static final int args = 0;

        @Override
        public boolean isRollback() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Rollback asRollback() {
            return this;
        }
    }

    class Close implements TransactionREPLCommand {

        private static final String token = "close";
        private static final String helpCommand = token;
        private static final String description = "Close the transaction without committing changes";
        private static final int args = 0;

        @Override
        public boolean isClose() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Close asClose() {
            return this;
        }
    }

    class Source implements TransactionREPLCommand {

        private static final String token = "source";
        private static final String helpCommand = token + " <file> [--print-answers]";
        private static final String description = "Run TypeQL queries in file.";
        private static final Set<String> optionalArgs = set("--print-answers");
        private static final int mandatoryArgs = 1;

        private final String file;
        private final boolean printAnswers;

        public Source(String file, boolean printAnswers) {
            this.file = file;
            this.printAnswers = printAnswers;
        }

        public String file() {
            return file;
        }

        public boolean printAnswers() {
            return printAnswers;
        }

        @Override
        public boolean isSource() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Source asSource() {
            return this;
        }
    }

    class Query implements TransactionREPLCommand {

        private static final String helpCommand = "<query>";
        private static final String description = "Run TypeQL query";

        private final String query;

        public Query(String query) {
            this.query = query;
        }

        public String query() {
            return query;
        }

        @Override
        public boolean isQuery() {
            return true;
        }

        @Override
        public TransactionREPLCommand.Query asQuery() {
            return this;
        }
    }

    static String createHelpMenu() {
        List<Pair<String, String>> menu = Arrays.asList(
                pair(TransactionREPLCommand.Query.helpCommand, TransactionREPLCommand.Query.description),
                pair(TransactionREPLCommand.Source.helpCommand, TransactionREPLCommand.Source.description),
                pair(TransactionREPLCommand.Commit.helpCommand, TransactionREPLCommand.Commit.description),
                pair(TransactionREPLCommand.Rollback.helpCommand, TransactionREPLCommand.Rollback.description),
                pair(TransactionREPLCommand.Close.helpCommand, TransactionREPLCommand.Close.description),
                pair(TransactionREPLCommand.Help.helpCommand, TransactionREPLCommand.Help.description),
                pair(TransactionREPLCommand.Clear.helpCommand, TransactionREPLCommand.Clear.description),
                pair(TransactionREPLCommand.Exit.helpCommand, TransactionREPLCommand.Exit.description)
        );
        return Utils.createHelpMenu(menu);
    }

    static Either<TransactionREPLCommand, String> readCommand(LineReader reader, String prompt) throws InterruptedException {
        String line = Utils.readNonEmptyLine(reader, prompt);
        Either<TransactionREPLCommand, String> command = readCommand(line);
        if (command.isSecond()) return command;
        else if (command.first().isQuery()) {
            String query = readMultilineQuery(reader, prompt, command.first().asQuery().query());
            Query multiLine = new Query(query);
            reader.getHistory().add(multiLine.query().trim());
            command = Either.first(multiLine);
        } else reader.getHistory().add(line.trim());
        return command;
    }

    static Either<TransactionREPLCommand, String> readCommand(String line) {
        TransactionREPLCommand command;
        String[] tokens = Utils.splitLineByWhitespace(line);
        if (tokens[0].equals(Exit.token)) {
            if (tokens.length - 1 != Exit.args) return Either.second(INVALID_EXIT_ARGS.message(Exit.args, tokens.length - 1));
            command = new Exit();
        } else if (tokens[0].equals(Help.token)) {
            if (tokens.length - 1 != Help.args) return Either.second(INVALID_HELP_ARGS.message(Help.args, tokens.length - 1));
            command = new Help();
        } else if (tokens[0].equals(Clear.token)) {
            if (tokens.length - 1 != Clear.args) return Either.second(INVALID_CLEAR_ARGS.message(Clear.args, tokens.length - 1));
            command = new Clear();
        } else if (tokens[0].equals(Commit.token)) {
            if (tokens.length - 1 != Commit.args) return Either.second(INVALID_COMMIT_ARGS.message(Commit.args, tokens.length - 1));
            command = new Commit();
        } else if (tokens[0].equals(Rollback.token)) {
            if (tokens.length - 1 != Rollback.args) return Either.second(INVALID_ROLLBACK_ARGS.message(Rollback.args, tokens.length - 1));
            command = new Rollback();
        } else if (tokens[0].equals(Close.token)) {
            if (tokens.length - 1 != Close.args) return Either.second(INVALID_CLOSE_ARGS.message(Close.args, tokens.length - 1));
            command = new Close();
        } else if (tokens[0].equals(Source.token)) {
            int args = tokens.length - 1;
            boolean printAnswers = false;
            if (args < Source.mandatoryArgs || args > Source.mandatoryArgs + Source.optionalArgs.size()) {
                return Either.second(INVALID_SOURCE_ARGS.message(Source.mandatoryArgs, Source.optionalArgs.size(), args));
            } else if (tokens.length == 3) {
                String printAnswersArg = tokens[2];
                if (!Source.optionalArgs.contains(printAnswersArg)) {
                    return Either.second(INVALID_OPTIONAL_ARG.message(Source.token, printAnswersArg));
                } else printAnswers = true;
            }
            String file = tokens[1];
            command = new Source(file, printAnswers);
        } else {
            command = new Query(line);
        }
        return Either.first(command);
    }

    static String readMultilineQuery(LineReader reader, String prompt, String firstQueryLine) {
        List<String> queryLines = new ArrayList<>();
        queryLines.add(firstQueryLine);
        while (true) {
            String queryPrompt = Utils.getContinuationPrompt(prompt);
            String queryLine;
            try {
                queryLine = Utils.readLineWithoutHistory(reader, queryPrompt);
            } catch (UserInterruptException | EndOfFileException e) {
                break;
            }
            if (queryLine.trim().isEmpty()) {
                break;
            } else {
                queryLines.add(queryLine);
            }
        }
        return String.join("\n", queryLines);
    }
}
