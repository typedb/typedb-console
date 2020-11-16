/*
 * Copyright (C) 2020 Grakn Labs
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
import java.util.Collections;
import java.util.List;

import static grakn.common.collection.Collections.pair;

public abstract class TransactionReplCommand {
    public static class Exit extends TransactionReplCommand {
        private static String token = "exit";
        private static String helpCommand = "exit";
        private static String description = "Exit console";
    }

    public static class Help extends TransactionReplCommand {
        private static String token = "help";
        private static String helpCommand = "help";
        private static String description = "Print this help menu";
    }

    public static class Clear extends TransactionReplCommand {
        private static String token = "clear";
        private static String helpCommand = "clear";
        private static String description = "Clear console screen";
    }

    public static class Commit extends TransactionReplCommand {
        private static String token = "commit";
        private static String helpCommand = "commit";
        private static String description = "Commit the transaction changes and close";
    }

    public static class Rollback extends TransactionReplCommand {
        private static String token = "rollback";
        private static String helpCommand = "rollback";
        private static String description = "Rollback the transaction to the beginning state";
    }

    public static class Close extends TransactionReplCommand {
        private static String token = "close";
        private static String helpCommand = "close";
        private static String description = "Close the transaction without committing changes";
    }

    public static class Source extends TransactionReplCommand {
        private static String token = "source";
        private static String helpCommand = "source <file>";
        private static String description = "Run Graql queries in file";

        private final String file;
        public Source(String file) {
            this.file = file;
        }
        public String file() { return file; }
    }

    public static class Query extends TransactionReplCommand {
        private static String helpCommand = "<query>";
        private static String description = "Run Graql query";

        private final String query;
        public Query(String query) {
            this.query = query;
        }
        public String query() { return query; }
    }

    public TransactionReplCommand.Source asSource() { return (TransactionReplCommand.Source)this; }
    public TransactionReplCommand.Query asQuery() { return (TransactionReplCommand.Query)this; }

    public static String getHelpMenu() {
        List<Pair<String, String>> menu = Arrays.asList(
                pair(TransactionReplCommand.Exit.helpCommand, TransactionReplCommand.Exit.description),
                pair(TransactionReplCommand.Help.helpCommand, TransactionReplCommand.Help.description),
                pair(TransactionReplCommand.Clear.helpCommand, TransactionReplCommand.Clear.description),
                pair(TransactionReplCommand.Commit.helpCommand, TransactionReplCommand.Commit.description),
                pair(TransactionReplCommand.Rollback.helpCommand, TransactionReplCommand.Rollback.description),
                pair(TransactionReplCommand.Close.helpCommand, TransactionReplCommand.Close.description),
                pair(TransactionReplCommand.Source.helpCommand, TransactionReplCommand.Source.description),
                pair(TransactionReplCommand.Query.helpCommand, TransactionReplCommand.Query.description));
        return Utils.buildHelpMenu(menu);
    }

    public static TransactionReplCommand getCommand(LineReader reader, Printer printer, String prompt) {
        TransactionReplCommand command;
        String[] tokens = getTokens(reader, printer, prompt);
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
            String firstQueryLine = String.join(" ", tokens);
            String query = getQuery(reader, firstQueryLine, prompt.length());
            command = new Query(query);
        }
        return command;
    }

    private static String getQuery(LineReader reader, String firstQueryLine, int indentation) {
        List<String> queryLines = new ArrayList<>();
        queryLines.add(firstQueryLine);
        while (true) {
            String prompt = String.join("", Collections.nCopies(indentation, " "));
            String queryLine;
            try {
                queryLine = reader.readLine(prompt);
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

    private static String[] getTokens(LineReader reader, Printer printer, String prompt) {
        String[] words = null;
        while (words == null) {
            try {
                String line = reader.readLine(prompt);
                words = Utils.splitLineByWhitespace(line);
                if (words.length == 0) words = null;
            } catch (UserInterruptException | EndOfFileException e) {
                printer.info("Use command '" + Exit.token + "' to exit the console");
            }
        }
        return words;
    }
}
