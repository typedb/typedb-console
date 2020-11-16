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

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TransactionReplCommand {
    static class Exit extends TransactionReplCommand {}
    static class Help extends TransactionReplCommand {}
    static class Clear extends TransactionReplCommand {}
    static class Commit extends TransactionReplCommand {}
    static class Rollback extends TransactionReplCommand {}
    static class Close extends TransactionReplCommand {}
    static class Source extends TransactionReplCommand {
        private final String file;
        public Source(String file) {
            this.file = file;
        }
        public String file() { return file; }
    }
    static class Query extends TransactionReplCommand {
        private final String query;
        public Query(String query) {
            this.query = query;
        }
        public String query() { return query; }
    }
    public TransactionReplCommand.Source asSource() { return (TransactionReplCommand.Source)this; }
    public TransactionReplCommand.Query asQuery() { return (TransactionReplCommand.Query)this; }

    public static TransactionReplCommand getCommand(LineReader reader, Printer printer, String prompt) {
        TransactionReplCommand command;
        String[] tokens = getTokens(reader, printer, prompt);
        if (tokens.length == 1 && tokens[0].equals("exit")) {
            command = new Exit();
        } else if (tokens.length == 1 && tokens[0].equals("help")) {
            command = new Help();
        } else if (tokens.length == 1 && tokens[0].equals("clear")) {
            command = new Clear();
        } else if (tokens.length == 1 && tokens[0].equals("commit")) {
            command = new Commit();
        } else if (tokens.length == 1 && tokens[0].equals("rollback")) {
            command = new Rollback();
        } else if (tokens.length == 1 && tokens[0].equals("close")) {
            command = new Close();
        } else if (tokens.length == 2 && tokens[0].equals("source")) {
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
                words = Utils.splitLine(line);
                if (words.length == 0) words = null;
            } catch (UserInterruptException | EndOfFileException e) {
                printer.info("Use command 'exit' to exit the console");
            }
        }
        return words;
    }
}
