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

import grakn.client.Grakn;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

public abstract class ReplCommand {
    static class Exit extends ReplCommand {}
    static class Help extends ReplCommand {}
    static class Clear extends ReplCommand {}
    static class DatabaseList extends ReplCommand {}
    static class DatabaseCreate extends ReplCommand {
        private final String database;
        public DatabaseCreate(String database) {
            this.database = database;
        }
        public String database() { return database; }
    }
    static class DatabaseDelete extends ReplCommand {
        private final String database;
        public DatabaseDelete(String database) {
            this.database = database;
        }
        public String database() { return database; }
    }
    static class Transaction extends ReplCommand {
        private final String database;
        private final Grakn.Session.Type sessionType;
        private final Grakn.Transaction.Type transactionType;
        public Transaction(String database, Grakn.Session.Type sessionType, Grakn.Transaction.Type transactionType) {
            this.database = database;
            this.sessionType = sessionType;
            this.transactionType = transactionType;
        }
        public String database() { return database; }
        public Grakn.Session.Type sessionType() { return sessionType; }
        public Grakn.Transaction.Type transactionType() { return transactionType; }
    }
    public DatabaseCreate asDatabaseCreate() { return (DatabaseCreate)this; }
    public DatabaseDelete asDatabaseDelete() { return (DatabaseDelete)this; }
    public Transaction asTransaction() { return (Transaction) this; }

    public static ReplCommand getCommand(LineReader reader, Printer printer, String prompt) {
        ReplCommand command = null;
        while (command == null) {
            String[] tokens = getTokens(reader, printer, prompt);
            if (tokens.length == 1 && tokens[0].equals("exit")) {
                command = new Exit();
            } else if (tokens.length == 1 && tokens[0].equals("help")) {
                command = new Help();
            } else if (tokens.length == 1 && tokens[0].equals("clear")) {
                command = new Clear();
            } else if (tokens.length == 2 && tokens[0].equals("database") && tokens[1].equals("list")) {
                command = new DatabaseList();
            } else if (tokens.length == 3 && tokens[0].equals("database") && tokens[1].equals("create")) {
                String database = tokens[2];
                command = new DatabaseCreate(database);
            } else if (tokens.length == 3 && tokens[0].equals("database") && tokens[1].equals("delete")) {
                String database = tokens[2];
                command = new DatabaseDelete(database);
            } else if (tokens.length == 4 && tokens[0].equals("transaction") &&
                    (tokens[2].equals("schema") || tokens[2].equals("data") && (tokens[3].equals("read") || tokens[3].equals("write")))) {
                String database = tokens[1];
                Grakn.Session.Type sessionType = tokens[2].equals("schema") ? Grakn.Session.Type.SCHEMA : Grakn.Session.Type.DATA;
                Grakn.Transaction.Type transactionType = tokens[3].equals("read") ? Grakn.Transaction.Type.READ : Grakn.Transaction.Type.WRITE;
                command = new Transaction(database, sessionType, transactionType);
            } else {
                printer.error("Unrecognised command, please check help menu");
            }
        }
        return command;
    }

    private static String[] getTokens(LineReader reader, Printer printer, String prompt) {
        String[] words = null;
        while (words == null) {
            try {
                String line = reader.readLine(prompt);
                words = Utils.splitLineByWhitespace(line);
                if (words.length == 0) words = null;
            } catch (UserInterruptException | EndOfFileException e) {
                printer.info("Use command 'exit' to exit the console");
            }
        }
        return words;
    }
}
