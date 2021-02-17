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

import grakn.client.GraknClient;
import grakn.common.collection.Pair;
import org.jline.reader.LineReader;

import java.util.Arrays;
import java.util.List;

import static grakn.common.collection.Collections.pair;
import static grakn.console.ErrorMessage.Internal.ILLEGAL_CAST;

public interface ReplCommand {

    default boolean isExit() {
        return false;
    }

    default Exit asExit() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isHelp() {
        return false;
    }

    default Help asHelp() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isClear() {
        return false;
    }

    default Clear asClear() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseList() {
        return false;
    }

    default Database.List asDatabaseList() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseCreate() {
        return false;
    }

    default Database.Create asDatabaseCreate() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseDelete() {
        return false;
    }

    default Database.Delete asDatabaseDelete() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseReplicas() {
        return false;
    }

    default Database.Replicas asDatabaseReplicas() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    default boolean isTransaction() {
        return false;
    }

    default Transaction asTransaction() {
        throw new GraknConsoleException(ILLEGAL_CAST);
    }

    class Exit implements ReplCommand {

        private static String token = "exit";
        private static String helpCommand = token;
        private static String description = "Exit console";

        @Override
        public boolean isExit() {
            return true;
        }

        @Override
        public Exit asExit() {
            return this;
        }
    }

    class Help implements ReplCommand {

        private static String token = "help";
        private static String helpCommand = token;
        private static String description = "Print this help menu";

        @Override
        public boolean isHelp() {
            return true;
        }

        @Override
        public Help asHelp() {
            return this;
        }
    }

    class Clear implements ReplCommand {

        private static String token = "clear";
        private static String helpCommand = token;
        private static String description = "Clear console screen";

        @Override
        public boolean isClear() {
            return true;
        }

        @Override
        public Clear asClear() {
            return this;
        }
    }

    abstract class Database implements ReplCommand {

        private static String token = "database";

        public static class List extends ReplCommand.Database {

            private static String token = "list";
            private static String helpCommand = Database.token + " " + token;
            private static String description = "List the databases on the server";

            @Override
            public boolean isDatabaseList() {
                return true;
            }

            @Override
            public Database.List asDatabaseList() {
                return this;
            }
        }

        public static class Create extends ReplCommand.Database {

            private static String token = "create";
            private static String helpCommand = Database.token + " " + token + " " + "<db>";
            private static String description = "Create a database with name <db> on the server";

            private final String database;

            public Create(String database) {
                this.database = database;
            }

            public String database() {
                return database;
            }

            @Override
            public boolean isDatabaseCreate() {
                return true;
            }

            @Override
            public Database.Create asDatabaseCreate() {
                return this;
            }
        }

        public static class Delete extends ReplCommand.Database {

            private static String token = "delete";
            private static String helpCommand = Database.token + " " + token + " " + "<db>";
            private static String description = "Delete a database with name <db> on the server";

            private final String database;

            public Delete(String database) {
                this.database = database;
            }

            public String database() {
                return database;
            }

            @Override
            public boolean isDatabaseDelete() {
                return true;
            }

            @Override
            public Database.Delete asDatabaseDelete() {
                return this;
            }
        }

        public static class Replicas extends ReplCommand.Database {

            private static String token = "replicas";
            private static String helpCommand = Database.token + " " + token + " " + "<db>";
            private static String description = "List replicas of a database with name <db>";

            private final String database;

            public Replicas(String database) {
                this.database = database;
            }

            public String database() {
                return database;
            }

            @Override
            public boolean isDatabaseReplicas() {
                return true;
            }

            @Override
            public Database.Replicas asDatabaseReplicas() {
                return this;
            }
        }
    }

    class Transaction implements ReplCommand {

        private static String token = "transaction";
        private static String helpCommand = token + " <db> schema|data read|write";
        private static String description = "Start a transaction to database <db> with schema or data session, with read or write transaction";

        private final String database;
        private final GraknClient.Session.Type sessionType;
        private final GraknClient.Transaction.Type transactionType;

        public Transaction(String database, GraknClient.Session.Type sessionType, GraknClient.Transaction.Type transactionType) {
            this.database = database;
            this.sessionType = sessionType;
            this.transactionType = transactionType;
        }

        public String database() {
            return database;
        }

        public GraknClient.Session.Type sessionType() {
            return sessionType;
        }

        public GraknClient.Transaction.Type transactionType() {
            return transactionType;
        }

        @Override
        public boolean isTransaction() {
            return true;
        }

        @Override
        public Transaction asTransaction() {
            return this;
        }
    }

    static String getHelpMenu() {
        List<Pair<String, String>> menu = Arrays.asList(
                pair(Database.List.helpCommand, Database.List.description),
                pair(Database.Create.helpCommand, Database.Create.description),
                pair(Database.Delete.helpCommand, Database.Delete.description),
                pair(Database.Replicas.helpCommand, Database.Replicas.description),
                pair(Transaction.helpCommand, Transaction.description),
                pair(Help.helpCommand, Help.description),
                pair(Clear.helpCommand, Clear.description),
                pair(Exit.helpCommand, Exit.description)
        );
        return Utils.buildHelpMenu(menu);
    }

    static ReplCommand getCommand(LineReader reader, Printer printer, String prompt) throws InterruptedException {
        ReplCommand command = null;
        while (command == null) {
            String line = Utils.readNonEmptyLine(reader, prompt);
            command = getCommand(line);
            if (command == null) {
                printer.error("Unrecognised command, please check help menu");
            }
            reader.getHistory().add(line.trim());
        }
        return command;
    }

    static ReplCommand getCommand(String line) {
        ReplCommand command = null;
        String[] tokens = Utils.splitLineByWhitespace(line);
        if (tokens.length == 1 && tokens[0].equals(Exit.token)) {
            command = new Exit();
        } else if (tokens.length == 1 && tokens[0].equals(Help.token)) {
            command = new Help();
        } else if (tokens.length == 1 && tokens[0].equals(Clear.token)) {
            command = new Clear();
        } else if (tokens.length == 2 && tokens[0].equals(Database.token) && tokens[1].equals(Database.List.token)) {
            command = new Database.List();
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Create.token)) {
            String database = tokens[2];
            command = new Database.Create(database);
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Delete.token)) {
            String database = tokens[2];
            command = new Database.Delete(database);
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Replicas.token)) {
            String database = tokens[2];
            command = new Database.Replicas(database);
        } else if (tokens.length == 4 && tokens[0].equals(Transaction.token) &&
                (tokens[2].equals("schema") || tokens[2].equals("data")) && (tokens[3].equals("read") || tokens[3].equals("write"))) {
            String database = tokens[1];
            GraknClient.Session.Type sessionType = tokens[2].equals("schema") ? GraknClient.Session.Type.SCHEMA : GraknClient.Session.Type.DATA;
            GraknClient.Transaction.Type transactionType = tokens[3].equals("read") ? GraknClient.Transaction.Type.READ : GraknClient.Transaction.Type.WRITE;
            command = new Transaction(database, sessionType, transactionType);
        }
        return command;
    }
}
