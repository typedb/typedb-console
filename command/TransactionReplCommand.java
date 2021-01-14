/*
 * Copyright (C) 2021 Grakn Labs
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

package grakn.console;

import grakn.common.collection.Pair;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static grakn.common.collection.Collections.pair;
import static grakn.console.ErrorMessage.Internal.ILLEGAL_CAST;

public interface TransactionReplCommand {

    default boolean isExit() {
        return false;
    }

    default TransactionReplCommand.Exit asExit() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isHelp() {
        return false;
    }

    default TransactionReplCommand.Help asHelp() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isClear() {
        return false;
    }

    default TransactionReplCommand.Clear asClear() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isCommit() {
        return false;
    }

    default TransactionReplCommand.Commit asCommit() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isRollback() {
        return false;
    }

    default TransactionReplCommand.Rollback asRollback() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isClose() {
        return false;
    }

    default TransactionReplCommand.Close asClose() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isSource() {
        return false;
    }

    default TransactionReplCommand.Source asSource() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isQuery() {
        return false;
    }

    default TransactionReplCommand.Query asQuery() {
        throw new grakn.console.GraknConsoleException(ILLEGAL_CAST);
    }

    class Exit implements TransactionReplCommand {

        private static String token = "exit";
        private static String helpCommand = token;
        private static String description = "Exit console";

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
        private static String description = "Run Graql queries in file";

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
        private static String description = "Run Graql query";

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

    static TransactionReplCommand getCommand(LineReader reader, String prompt) throws InterruptedException {
        TransactionReplCommand command;
        String line = Utils.readNonEmptyLine(reader, prompt);
        String[] tokens = Utils.splitLineByWhitespace(line);
        if (tokens.length == 1 && tokens[0].equals(Exit.token)) {
            command = new Exit();
        } else if (tokens.length == 1 && tokens[0].equals(Help.token)) {
            command = new Help();
        } else if (tokens.length == 1 && tokens[0].equals(Clear.token)) {
            command = new Clear();
        } else if (tokens.length == 1 && tokens[0].equals(Commit.token)) {
            command = new Commit();
        } else if (tokens.length == 1 && tokens[0].equals(Rollback.token)) {
            command = new Rollback();
        } else if (tokens.length == 1 && tokens[0].equals(Close.token)) {
            command = new Close();
        } else if (tokens.length == 2 && tokens[0].equals(Source.token)) {
            String file = tokens[1];
            command = new Source(file);
        } else {
            String query = readQuery(reader, prompt, line);
            command = new Query(query);
        }
        if (command instanceof Query) reader.getHistory().add(command.asQuery().query().trim());
        else reader.getHistory().add(line.trim());
        return command;
    }

    static String readQuery(LineReader reader, String prompt, String firstQueryLine) {
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
