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
import grakn.client.GraknOptions;
import grakn.common.collection.Pair;
import org.jline.reader.LineReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static grakn.common.collection.Collections.list;
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
        private static String helpCommand = token + " <db> schema|data read|write [options]";
        private static String description = "Start a transaction to database <db> with schema or data session, with read or write transaction";

        private final String database;
        private final GraknClient.Session.Type sessionType;
        private final GraknClient.Transaction.Type transactionType;
        private final GraknOptions options;

        public Transaction(String database, GraknClient.Session.Type sessionType, GraknClient.Transaction.Type transactionType, GraknOptions options) {
            this.database = database;
            this.sessionType = sessionType;
            this.transactionType = transactionType;
            this.options = options;
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

        public GraknOptions options() {
            return options;
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

    class Options {

        static GraknOptions from(String[] optionTokens, boolean isCluster) {
            if (isCluster) return parseClusterOptions(optionTokens, GraknOptions.cluster());
            else return parseCoreOptions(optionTokens, GraknOptions.core());
        }

        private static GraknOptions.Cluster parseClusterOptions(String[] optionTokens, GraknOptions.Cluster options) {
            for (int i = 0; i < optionTokens.length; i += 2) {
                String token = optionTokens[i];
                String arg = optionTokens[i + 1];
                Option<GraknOptions.Cluster> option = Options.Cluster.from(token);
                try {
                    options = option.build(options, arg);
                } catch (IllegalArgumentException e) {
                    throw new GraknConsoleException(e);
                }
            }
            return options;
        }

        private static GraknOptions parseCoreOptions(String[] optionTokens, GraknOptions options) {
            for (int i = 0; i < optionTokens.length; i += 2) {
                String token = optionTokens[i];
                String arg = optionTokens[i + 1];
                Option<GraknOptions> option = Options.Core.from(token);
                try {
                    options = option.build(options, arg);
                } catch (IllegalArgumentException e) {
                    throw new GraknConsoleException(e);
                }
            }
            return options;
        }

        static class Core {

            static List<Option<GraknOptions>> options = list(
                    Option.of("infer", Option.Arg.BOOLEAN, "Enable or disable inference", (opt, arg) -> opt.infer((Boolean) arg)),
                    Option.of("traceInference", Option.Arg.BOOLEAN, "Enable or disable inference tracing", (opt, arg) -> opt.traceInference((Boolean) arg)),
                    Option.of("explain", Option.Arg.BOOLEAN, "Enable or disable inference explanations", (opt, arg) -> opt.explain((Boolean) arg)),
                    Option.of("parallel", Option.Arg.BOOLEAN, "Enable or disable parallel query execution", (opt, arg) -> opt.parallel((Boolean) arg)),
                    Option.of("batchSize", Option.Arg.INTEGER, "Set RPC answer batch size", (opt, arg) -> opt.batchSize((Integer) arg)),
                    Option.of("prefetch", Option.Arg.BOOLEAN, "Enable or disable RPC answer prefetch ", (opt, arg) -> opt.prefetch((Boolean) arg)),
                    Option.of("sessionIdleTimeout", Option.Arg.INTEGER, "Kill idle session timeout", (opt, arg) -> opt.sessionIdleTimeout((Integer) arg)),
                    Option.of("schemaLockAcquireTimeout", Option.Arg.INTEGER, "", (opt, arg) -> opt.schemaLockAcquireTimeout((Integer) arg))
            );

            static Option<GraknOptions> from(String token) throws IllegalArgumentException {
                for (Option<GraknOptions> option : options) {
                    if (option.name().equals(token)) return option;
                }
                // TODO create custom error message
                throw new IllegalArgumentException(String.format("Unrecognized Option '%s'", token));
            }

            static List<Pair<String, String>> helpMenu() {
                List<Pair<String, String>> optionsMenu = new ArrayList<>();
                optionsMenu.add(pair("Option", "Transaction-level options"));
                for (Option<GraknOptions> option : options) {
                    optionsMenu.add(pair("--" + option.name(), "[" + option.arg().readableString() + "] " + option.description()));
                }
                return optionsMenu;
            }

        }

        static class Cluster {

            static List<Option<GraknOptions.Cluster>> options = list(
                    Option.of("infer", Option.Arg.BOOLEAN, "Enable or disable inference", (opt, arg) -> opt.infer((Boolean) arg)),
                    Option.of("traceInference", Option.Arg.BOOLEAN, "Enable or disable inference tracing", (opt, arg) -> opt.traceInference((Boolean) arg)),
                    Option.of("explain", Option.Arg.BOOLEAN, "Enable or disable inference explanations", (opt, arg) -> opt.explain((Boolean) arg)),
                    Option.of("parallel", Option.Arg.BOOLEAN, "Enable or disable parallel query execution", (opt, arg) -> opt.parallel((Boolean) arg)),
                    Option.of("batchSize", Option.Arg.INTEGER, "Set RPC answer batch size", (opt, arg) -> opt.batchSize((Integer) arg)),
                    Option.of("prefetch", Option.Arg.BOOLEAN, "Enable or disable RPC answer prefetch ", (opt, arg) -> opt.prefetch((Boolean) arg)),
                    Option.of("sessionIdleTimeout", Option.Arg.INTEGER, "Kill idle session timeout", (opt, arg) -> opt.sessionIdleTimeout((Integer) arg)),
                    Option.of("schemaLockAcquireTimeout", Option.Arg.INTEGER, "", (opt, arg) -> opt.schemaLockAcquireTimeout((Integer) arg)),
                    Option.of("anyReplica", Option.Arg.BOOLEAN, "Allow (possibly stale) reads from any replica", (opt, arg) -> opt.readAnyReplica((Boolean) arg))
            );

            static Option<GraknOptions.Cluster> from(String token) throws IllegalArgumentException {
                for (Option<GraknOptions.Cluster> option : options) {
                    if (option.name().equals(token)) return option;
                }
                // TODO create custom error message
                throw new IllegalArgumentException(String.format("Unrecognized Option '%s'", token));
            }

            static List<Pair<String, String>> helpMenu() {
                List<Pair<String, String>> optionsMenu = new ArrayList<>();
                optionsMenu.add(pair("Option", "Transaction-level options for cluster"));
                for (Option<GraknOptions.Cluster> option : options) {
                    optionsMenu.add(pair("--" + option.name(), "[" + option.arg().readableString() + "] " + option.description()));
                }
                return optionsMenu;
            }
        }

        static class Option<OPTIONS> {

            private final String name;
            private final Arg arg;
            private final String description;
            private BiFunction<OPTIONS, Object, OPTIONS> builder;

            private Option(String name, Arg arg, String description, BiFunction<OPTIONS, Object, OPTIONS> builder) {
                this.name = name;
                this.arg = arg;
                this.description = description;
                this.builder = builder;
            }

            static <OPT extends GraknOptions> Option<OPT> of(String name, Arg arg, String description, BiFunction<OPT, Object, OPT> builder) {
                return new Option<>(name, arg, description, builder);
            }

            public String name() { return name; }

            public Arg arg() { return arg; }

            public String description() { return description; }

            OPTIONS build(OPTIONS options, String arg) {
                return builder.apply(options, this.arg.parse(arg));
            }

            enum Arg {

                BOOLEAN("true/false"),
                INTEGER("> 0");

                private final String readableString;

                Arg(String readableString) {
                    this.readableString = readableString;
                }

                public String readableString() { return readableString; }

                Object parse(String arg) throws IllegalArgumentException {
                    if (this == BOOLEAN) return Boolean.parseBoolean(arg);
                    else if (this == INTEGER) {
                        int value = Integer.parseInt(arg);
                        // TODO this should be a custom error message class in Console
                        if (value <= 0) throw new IllegalArgumentException("Integer argument must be greater than 0");
                        else return value;
                    } else {
                        // TODO this should be a custom error message class in Console
                        throw new IllegalArgumentException("Unrecognized option argument type: " + this.name());
                    }
                }
            }

        }

    }


    static String getHelpMenu(GraknClient client) {
        List<Pair<String, String>> menu = new ArrayList<>(Arrays.asList(
                pair(Database.List.helpCommand, Database.List.description),
                pair(Database.Create.helpCommand, Database.Create.description),
                pair(Database.Delete.helpCommand, Database.Delete.description)));


        if (client.isCluster()) {
            menu.add(pair(Database.Replicas.helpCommand, Database.Replicas.description));
        }

        menu.add(pair(Transaction.helpCommand, Transaction.description));
        if (client.isCluster()) menu.addAll(Options.Cluster.helpMenu());
        else menu.addAll(Options.Core.helpMenu());

        menu.addAll(Arrays.asList(
                pair(Help.helpCommand, Help.description),
                pair(Clear.helpCommand, Clear.description),
                pair(Exit.helpCommand, Exit.description)
        ));
        return Utils.buildHelpMenu(menu);
    }

    static ReplCommand getCommand(LineReader reader, Printer printer, String prompt, boolean isCluster) throws InterruptedException {
        ReplCommand command = null;
        while (command == null) {
            String line = Utils.readNonEmptyLine(reader, prompt);
            command = getCommand(line, isCluster);
            if (command == null) {
                printer.error("Unrecognised command, please check help menu");
            }
            reader.getHistory().add(line.trim());
        }
        return command;
    }

    static ReplCommand getCommand(String line, boolean isCluster) {
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
        } else if (tokens.length >= 4 && tokens[0].equals(Transaction.token) &&
                (tokens[2].equals("schema") || tokens[2].equals("data")) && (tokens[3].equals("read") || tokens[3].equals("write"))) {
            String database = tokens[1];
            GraknClient.Session.Type sessionType = tokens[2].equals("schema") ? GraknClient.Session.Type.SCHEMA : GraknClient.Session.Type.DATA;
            GraknClient.Transaction.Type transactionType = tokens[3].equals("read") ? GraknClient.Transaction.Type.READ : GraknClient.Transaction.Type.WRITE;
            GraknOptions options;
            if (tokens.length > 4) options = Options.from(Arrays.copyOfRange(tokens, 4, tokens.length), isCluster);
            else options = isCluster ? GraknOptions.cluster() : GraknOptions.core();
            command = new Transaction(database, sessionType, transactionType, options);
        }
        return command;
    }

}
