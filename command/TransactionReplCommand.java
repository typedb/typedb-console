/*
 * Copyright (C) 2021 Vaticle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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

import static com.vaticle.typedb.common.collection.Collections.pair;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_CLEAR_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_CLOSE_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_COMMIT_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_EXIT_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_HELP_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_ROLLBACK_ARGS;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.TransactionRepl.INVALID_SOURCE_ARGS;

public interface TransactionReplCommand {

    default boolean isExit() {
        return false;
    }

    default TransactionReplCommand.Exit asExit() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isHelp() {
        return false;
    }

    default TransactionReplCommand.Help asHelp() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isClear() {
        return false;
    }

    default TransactionReplCommand.Clear asClear() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isCommit() {
        return false;
    }

    default TransactionReplCommand.Commit asCommit() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isRollback() {
        return false;
    }

    default TransactionReplCommand.Rollback asRollback() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isClose() {
        return false;
    }

    default TransactionReplCommand.Close asClose() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isSource() {
        return false;
    }

    default TransactionReplCommand.Source asSource() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isQuery() {
        return false;
    }

    default TransactionReplCommand.Query asQuery() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    class Exit implements TransactionReplCommand {

        private static String token = "exit";
        private static String helpCommand = token;
        private static String description = "Exit console";
        private static int args = 0;

        @Override
        public boolean isExit() {
            return true;
        }

        @Override
        public TransactionReplCommand.Exit asExit() {
            return this;
        }
    }

    class Help implements TransactionReplCommand {

        private static String token = "help";
        private static String helpCommand = token;
        private static String description = "Print this help menu";
        private static int args = 0;

        @Override
        public boolean isHelp() {
            return true;
        }

        @Override
        public TransactionReplCommand.Help asHelp() {
            return this;
        }
    }

    class Clear implements TransactionReplCommand {

        private static String token = "clear";
        private static String helpCommand = token;
        private static String description = "Clear console screen";
        private static int args = 0;

        @Override
        public boolean isClear() {
            return true;
        }

        @Override
        public TransactionReplCommand.Clear asClear() {
            return this;
        }
    }

    class Commit implements TransactionReplCommand {

        private static String token = "commit";
        private static String helpCommand = token;
        private static String description = "Commit the transaction changes and close transaction";
        private static int args = 0;

        @Override
        public boolean isCommit() {
            return true;
        }

        @Override
        public TransactionReplCommand.Commit asCommit() {
            return this;
        }
    }

    class Rollback implements TransactionReplCommand {

        private static String token = "rollback";
        private static String helpCommand = token;
        private static String description = "Rollback the transaction to the beginning state";
        private static int args = 0;

        @Override
        public boolean isRollback() {
            return true;
        }

        @Override
        public TransactionReplCommand.Rollback asRollback() {
            return this;
        }
    }

    class Close implements TransactionReplCommand {

        private static String token = "close";
        private static String helpCommand = token;
        private static String description = "Close the transaction without committing changes";
        private static int args = 0;

        @Override
        public boolean isClose() {
            return true;
        }

        @Override
        public TransactionReplCommand.Close asClose() {
            return this;
        }
    }

    class Source implements TransactionReplCommand {

        private static String token = "source";
        private static String helpCommand = token + " <file>";
        private static String description = "Run TypeQL queries in file";
        private static int args = 1;

        private final String file;

        public Source(String file) {
            this.file = file;
        }

        public String file() {
            return file;
        }

        @Override
        public boolean isSource() {
            return true;
        }

        @Override
        public TransactionReplCommand.Source asSource() {
            return this;
        }
    }

    class Query implements TransactionReplCommand {

        private static String helpCommand = "<query>";
        private static String description = "Run TypeQL query";

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
        public TransactionReplCommand.Query asQuery() {
            return this;
        }
    }

    static String getHelpMenu() {
        List<Pair<String, String>> menu = Arrays.asList(
                pair(TransactionReplCommand.Query.helpCommand, TransactionReplCommand.Query.description),
                pair(TransactionReplCommand.Source.helpCommand, TransactionReplCommand.Source.description),
                pair(TransactionReplCommand.Commit.helpCommand, TransactionReplCommand.Commit.description),
                pair(TransactionReplCommand.Rollback.helpCommand, TransactionReplCommand.Rollback.description),
                pair(TransactionReplCommand.Close.helpCommand, TransactionReplCommand.Close.description),
                pair(TransactionReplCommand.Help.helpCommand, TransactionReplCommand.Help.description),
                pair(TransactionReplCommand.Clear.helpCommand, TransactionReplCommand.Clear.description),
                pair(TransactionReplCommand.Exit.helpCommand, TransactionReplCommand.Exit.description)
        );
        return Utils.buildHelpMenu(menu);
    }

    static Either<TransactionReplCommand, String> getCommand(LineReader reader, String prompt) throws InterruptedException {
        String line = Utils.readNonEmptyLine(reader, prompt);
        Either<TransactionReplCommand, String> command = getCommand(line);
        if (command.isSecond()) return command;
        else if (command.first().isQuery()) {
            String query = readMultilineQuery(reader, prompt, command.first().asQuery().query());
            Query multiLine = new Query(query);
            reader.getHistory().add(multiLine.query().trim());
            command = Either.first(multiLine);
        } else reader.getHistory().add(line.trim());
        return command;
    }

    static Either<TransactionReplCommand, String> getCommand(String line) {
        TransactionReplCommand command;
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
            if (tokens.length - 1 != Source.args) return Either.second(INVALID_SOURCE_ARGS.message(Source.args, tokens.length - 1));
            String file = tokens[1];
            command = new Source(file);
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
