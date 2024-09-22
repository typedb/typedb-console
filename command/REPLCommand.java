/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console.command;

import com.vaticle.typedb.driver.api.TypeDBDriver;
import com.vaticle.typedb.driver.api.TypeDBOptions;
import com.vaticle.typedb.driver.api.TypeDBTransaction;
import com.vaticle.typedb.common.collection.Pair;
import com.vaticle.typedb.console.common.Printer;
import com.vaticle.typedb.console.common.Utils;
import com.vaticle.typedb.console.common.exception.TypeDBConsoleException;
import org.jline.reader.LineReader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static com.vaticle.typedb.common.collection.Collections.list;
import static com.vaticle.typedb.common.collection.Collections.pair;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Console.UNABLE_TO_READ_PASSWORD_INTERACTIVELY;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;

public interface REPLCommand {

    default boolean isExit() {
        return false;
    }

    default Exit asExit() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isHelp() {
        return false;
    }

    default Help asHelp() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isClear() {
        return false;
    }

    default Clear asClear() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isUserList() {
        return false;
    }

    default User.List asUserList() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isUserCreate() {
        return false;
    }

    default User.Create asUserCreate() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isUserPasswordUpdate() {
        return false;
    }

    default User.PasswordUpdate asUserPasswordUpdate() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isUserPasswordSet() {
        return false;
    }

    default User.PasswordSet asUserPasswordSet() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isUserDelete() {
        return false;
    }

    default User.Delete asUserDelete() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseList() {
        return false;
    }

    default Database.List asDatabaseList() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseCreate() {
        return false;
    }

    default Database.Create asDatabaseCreate() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseSchema() {
        return false;
    }

    default Database.Schema asDatabaseSchema() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseDelete() {
        return false;
    }

    default Database.Delete asDatabaseDelete() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isDatabaseReplicas() {
        return false;
    }

    default Database.Replicas asDatabaseReplicas() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    default boolean isTransaction() {
        return false;
    }

    default Transaction asTransaction() {
        throw new TypeDBConsoleException(ILLEGAL_CAST);
    }

    class Exit implements REPLCommand {

        public static String token = "exit";
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

    class Help implements REPLCommand {

        public static String token = "help";
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

    class Clear implements REPLCommand {

        public static String token = "clear";
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

    abstract class Database implements REPLCommand {

        public static String token = "database";

        public static class List extends REPLCommand.Database {

            public static String token = "list";
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

        public static class Create extends REPLCommand.Database {

            public static String token = "create";
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

        public static class Delete extends REPLCommand.Database {

            public static String token = "delete";
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

        public static class Schema extends REPLCommand.Database {

            public static String token = "schema";
            private static String helpCommand = Database.token + " " + token + " " + "<db>";
            private static String description = "Print the schema of the database with name <db>";

            private final String database;

            public Schema(String database) {
                this.database = database;
            }

            public String database() {
                return database;
            }

            @Override
            public boolean isDatabaseSchema() {
                return true;
            }

            @Override
            public Database.Schema asDatabaseSchema() {
                return this;
            }
        }

        public static class Replicas extends REPLCommand.Database {

            public static String token = "replicas";
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

    abstract class User implements REPLCommand {

        public static String token = "user";

        public static class List extends REPLCommand.User {

            public static String token = "list";
            private static String helpCommand = User.token + " " + token;
            private static String description = "List the users on the server";

            @Override
            public boolean isUserList() {
                return true;
            }

            @Override
            public User.List asUserList() {
                return this;
            }
        }

        public static class Create extends REPLCommand.User {

            public static String token = "create";
            private static String helpCommand = User.token + " " + token + " " + "<username>";
            private static String description = "Create a user with name <username> and a supplied password on the server";

            private final String user;
            private final String password;

            public Create(String user, String password) {
                this.user = user;
                this.password = password;
            }

            public String user() {
                return user;
            }

            public String password() {
                return password;
            }

            @Override
            public boolean isUserCreate() {
                return true;
            }

            @Override
            public User.Create asUserCreate() {
                return this;
            }
        }

        public static class PasswordUpdate extends REPLCommand.User {

            public static String token = "password-update";
            private static String helpCommand = User.token + " " + token + " [old-password new-password]";
            private static String description = "Update the password of the current user";

            private final String passwordOld;
            private final String passwordNew;

            public PasswordUpdate(String passwordOld, String passwordNew) {
                this.passwordOld = passwordOld;
                this.passwordNew = passwordNew;
            }

            public String passwordOld() {
                return passwordOld;
            }

            public String passwordNew() {
                return passwordNew;
            }

            @Override
            public boolean isUserPasswordUpdate() {
                return true;
            }

            @Override
            public PasswordUpdate asUserPasswordUpdate() {
                return this;
            }
        }

        public static class PasswordSet extends REPLCommand.User {

            public static String token = "password-set";
            private static String helpCommand = User.token + " " + token + " " + "<username>";
            private static String description = "Set the password of user with name <username>";

            private final String user;

            private final String password;

            public PasswordSet(String user, String password) {
                this.user = user;
                this.password = password;
            }

            public String user() {
                return user;
            }

            public String password() {
                return password;
            }

            @Override
            public boolean isUserPasswordSet() {
                return true;
            }

            @Override
            public PasswordSet asUserPasswordSet() {
                return this;
            }
        }

        public static class Delete extends REPLCommand.User {

            public static String token = "delete";
            private static String helpCommand = User.token + " " + token + " " + "<username>";
            private static String description = "Delete a user with name <username> on the server";

            private final String user;

            public Delete(String user) {
                this.user = user;
            }

            public String user() {
                return user;
            }

            @Override
            public boolean isUserDelete() {
                return true;
            }

            @Override
            public User.Delete asUserDelete() {
                return this;
            }
        }
    }

    class Transaction implements REPLCommand {

        public static final String token = "transaction";
        private static final String helpCommand = token + " <db> schema|data read|write [" + Options.token + "]";
        private static final String description = "Start a transaction to database <db> with schema or data session, with read or write transaction";

        private final String database;
        private final TypeDBTransaction.Type transactionType;
        private final TypeDBOptions options;

        public Transaction(String database, TypeDBTransaction.Type transactionType, TypeDBOptions options) {
            this.database = database;
            this.transactionType = transactionType;
            this.options = options;
        }

        public String database() {
            return database;
        }

        public TypeDBTransaction.Type transactionType() {
            return transactionType;
        }

        public TypeDBOptions options() {
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

        public static String token = "transaction-options";

        static TypeDBOptions from(String[] optionTokens, boolean isCloud) {
            if (isCloud) return parseCloudOptions(optionTokens, new TypeDBOptions());
            else return parseCoreOptions(optionTokens, new TypeDBOptions());
        }

        private static TypeDBOptions parseCloudOptions(String[] optionTokens, TypeDBOptions options) {
            for (int i = 0; i < optionTokens.length; i += 2) {
                String token = optionTokens[i];
                String arg = optionTokens[i + 1];
                assert token.charAt(0) == '-' && token.charAt(1) == '-';
                Option<TypeDBOptions> option = Options.Cloud.cloudOption(token.substring(2));
                try {
                    options = option.build(options, arg);
                } catch (IllegalArgumentException e) {
                    throw new TypeDBConsoleException(e);
                }
            }
            return options;
        }

        private static TypeDBOptions parseCoreOptions(String[] optionTokens, TypeDBOptions options) {
            for (int i = 0; i < optionTokens.length; i += 2) {
                String token = optionTokens[i];
                String arg = optionTokens[i + 1];
                assert token.charAt(0) == '-' && token.charAt(1) == '-';
                Option<TypeDBOptions> option = Options.Core.coreOption(token.substring(2));
                try {
                    options = option.build(options, arg);
                } catch (IllegalArgumentException e) {
                    throw new TypeDBConsoleException(e);
                }
            }
            return options;
        }

        static class Core {

            static List<Option.Core> options = list(
                    Option.core("infer", Option.Arg.BOOLEAN, "Enable or disable inference", (opt, arg) -> opt.infer((Boolean) arg)),
                    Option.core("trace-inference", Option.Arg.BOOLEAN, "Enable or disable inference tracing", (opt, arg) -> opt.traceInference((Boolean) arg)),
                    Option.core("explain", Option.Arg.BOOLEAN, "Enable or disable inference explanations", (opt, arg) -> opt.explain((Boolean) arg)),
                    Option.core("parallel", Option.Arg.BOOLEAN, "Enable or disable parallel query execution", (opt, arg) -> opt.parallel((Boolean) arg)),
                    Option.core("batch-size", Option.Arg.INTEGER, "Set RPC answer batch size", (opt, arg) -> opt.prefetchSize((Integer) arg)),
                    Option.core("prefetch", Option.Arg.BOOLEAN, "Enable or disable RPC answer prefetch ", (opt, arg) -> opt.prefetch((Boolean) arg)),
                    Option.core("session-idle-timeout", Option.Arg.INTEGER, "Kill idle session timeout (ms)", (opt, arg) -> opt.sessionIdleTimeoutMillis((Integer) arg)),
                    Option.core("transaction-timeout", Option.Arg.INTEGER, "Kill transaction timeout (ms)", (opt, arg) -> opt.transactionTimeoutMillis((Integer) arg)),
                    Option.core("schema-lock-acquire-timeout", Option.Arg.INTEGER, "Acquire exclusive schema session timeout (ms)", (opt, arg) -> opt.schemaLockAcquireTimeoutMillis((Integer) arg))
            );

            public static Option<TypeDBOptions> coreOption(String token) throws IllegalArgumentException {
                return from(token, options);
            }

            public static List<Pair<String, String>> helpMenu() {
                return helpMenu(options);
            }

            static <OPT extends TypeDBOptions> Option<OPT> from(String token, List<? extends Option<OPT>> options) {
                for (Option<OPT> option : options) {
                    if (option.name().equals(token)) return option;
                }
                throw new IllegalArgumentException(String.format("Unrecognized Option '%s'", token));
            }

            static List<Pair<String, String>> helpMenu(List<? extends Option<? extends TypeDBOptions>> options) {
                List<Pair<String, String>> optionsMenu = new ArrayList<>();
                optionsMenu.add(pair("transaction-options", "Transaction options"));
                for (Option<? extends TypeDBOptions> option : options) {
                    optionsMenu.add(pair("--" + option.name() + " " + option.arg().readableString(), option.description()));
                }
                return optionsMenu;
            }
        }

        static class Cloud extends Core {

            private static List<Option.Cloud> options = withCoreOptions(
                    Option.cloud("read-any-replica", Option.Arg.BOOLEAN, "Allow (possibly stale) reads from any replica", (opt, arg) -> opt.readAnyReplica((Boolean) arg))
            );

            private static List<Option.Cloud> withCoreOptions(Option.Cloud... cloudOptions) {
                List<Option.Cloud> extendedOptions = new ArrayList<>();
                Core.options.forEach(opt -> extendedOptions.add(opt.asCloudOption()));
                extendedOptions.addAll(Arrays.asList(cloudOptions));
                return extendedOptions;
            }

            public static Option<TypeDBOptions> cloudOption(String token) throws IllegalArgumentException {
                return from(token, options);
            }

            public static List<Pair<String, String>> helpMenu() {
                return helpMenu(options);
            }
        }

        static abstract class Option<OPTIONS extends TypeDBOptions> {

            final String name;
            final Arg arg;
            final String description;
            BiFunction<OPTIONS, Object, OPTIONS> builder;

            private Option(String name, Arg arg, String description, BiFunction<OPTIONS, Object, OPTIONS> builder) {
                this.name = name;
                this.arg = arg;
                this.description = description;
                this.builder = builder;
            }

            static Option.Core core(String name, Arg arg, String description, BiFunction<TypeDBOptions, Object, TypeDBOptions> builder) {
                return new Option.Core(name, arg, description, builder);
            }

            static Option.Cloud cloud(String name, Arg arg, String description, BiFunction<TypeDBOptions, Object, TypeDBOptions> builder) {
                return new Option.Cloud(name, arg, description, builder);
            }

            OPTIONS build(OPTIONS options, String arg) {
                return builder.apply(options, this.arg.parse(arg));
            }

            public String name() { return name; }

            public Arg arg() { return arg; }

            public String description() { return description; }

            static class Core extends Option<TypeDBOptions> {

                private Core(String name, Arg arg, String description, BiFunction<TypeDBOptions, Object, TypeDBOptions> builder) {
                    super(name, arg, description, builder);
                }

                Option.Cloud asCloudOption() {
                    return new Option.Cloud(name, arg, description, (cloudOption, arg) -> builder.apply(cloudOption, arg));
                }
            }

            static class Cloud extends Option<TypeDBOptions> {

                private Cloud(String name, Arg arg, String description, BiFunction<TypeDBOptions, Object, TypeDBOptions> builder) {
                    super(name, arg, description, builder);
                }
            }

            enum Arg {

                BOOLEAN("true|false"),
                INTEGER("1..[max int]");

                private final String readableString;

                Arg(String readableString) {
                    this.readableString = readableString;
                }

                public String readableString() { return readableString; }

                Object parse(String arg) throws IllegalArgumentException {
                    if (this == BOOLEAN) return Boolean.parseBoolean(arg);
                    else if (this == INTEGER) return Integer.parseInt(arg);
                    else throw new IllegalArgumentException("Unrecognized option argument type: " + this.name());
                }
            }
        }
    }

    static String createHelpMenu(TypeDBDriver driver, boolean isCloud) {
        List<Pair<String, String>> menu = new ArrayList<>();
        if (driver.users() != null) {
            menu.addAll(Arrays.asList(
                    pair(User.List.helpCommand, User.List.description),
                    pair(User.Create.helpCommand, User.Create.description),
                    pair(User.PasswordUpdate.helpCommand, User.PasswordUpdate.description),
                    pair(User.PasswordSet.helpCommand, User.PasswordSet.description),
                    pair(User.Delete.helpCommand, User.Delete.description)));
        }

        menu.addAll(Arrays.asList(
                pair(Database.List.helpCommand, Database.List.description),
                pair(Database.Create.helpCommand, Database.Create.description),
                pair(Database.Delete.helpCommand, Database.Delete.description),
                pair(Database.Schema.helpCommand, Database.Schema.description)));

        if (isCloud) {
            menu.add(pair(Database.Replicas.helpCommand, Database.Replicas.description));
        }

        menu.add(pair(Transaction.helpCommand, Transaction.description));
        if (isCloud) menu.addAll(Options.Cloud.helpMenu());
        else menu.addAll(Options.Core.helpMenu());

        menu.addAll(Arrays.asList(
                pair(Help.helpCommand, Help.description),
                pair(Clear.helpCommand, Clear.description),
                pair(Exit.helpCommand, Exit.description)
        ));
        return Utils.createHelpMenu(menu);
    }

    static REPLCommand readREPLCommand(LineReader reader, Printer printer, String prompt, boolean isCloud) throws InterruptedException {
        REPLCommand command = null;
        while (command == null) {
            String line = Utils.readNonEmptyLine(reader, prompt);
            command = readREPLCommand(line, reader, isCloud);
            if (command == null) {
                printer.error("Unrecognised command, please check help menu");
            }
            reader.getHistory().add(line.trim());
        }
        return command;
    }

    static REPLCommand readREPLCommand(String line, @Nullable LineReader passwordReader, boolean isCloud) {
        REPLCommand command = null;
        String[] tokens = Utils.splitLineByWhitespace(line);
        if (tokens.length == 1 && tokens[0].equals(Exit.token)) {
            command = new Exit();
        } else if (tokens.length == 1 && tokens[0].equals(Help.token)) {
            command = new Help();
        } else if (tokens.length == 1 && tokens[0].equals(Clear.token)) {
            command = new Clear();
        } else if (tokens.length == 2 && tokens[0].equals(User.token) && tokens[1].equals(User.List.token)) {
            command = new User.List();
        } else if (tokens.length == 3 && tokens[0].equals(User.token) && tokens[1].equals(User.Create.token)) {
            String name = tokens[2];
            if (passwordReader == null) throw new TypeDBConsoleException(UNABLE_TO_READ_PASSWORD_INTERACTIVELY);
            String password = Utils.readPassword(passwordReader, "Password: ");
            command = new User.Create(name, password);
        } else if ((tokens.length == 2 || tokens.length == 4) && tokens[0].equals(User.token) && tokens[1].equals(User.PasswordUpdate.token)) {
            String passwordOld;
            String passwordNew;
            if (tokens.length == 2) {
                if (passwordReader == null) throw new TypeDBConsoleException(UNABLE_TO_READ_PASSWORD_INTERACTIVELY);
                passwordOld = Utils.readPassword(passwordReader, "Old password: ");
                passwordNew = Utils.readPassword(passwordReader, "New password: ");
            } else {
                passwordOld = tokens[2];
                passwordNew = tokens[3];
            }
            command = new User.PasswordUpdate(passwordOld, passwordNew);
        } else if (tokens.length == 3 && tokens[0].equals(User.token) && tokens[1].equals(User.PasswordSet.token)) {
            String name = tokens[2];
            if (passwordReader == null) throw new TypeDBConsoleException(UNABLE_TO_READ_PASSWORD_INTERACTIVELY);
            String newPassword = Utils.readPassword(passwordReader, "New password: ");
            command = new User.PasswordSet(name, newPassword);
        } else if (tokens.length == 3 && tokens[0].equals(User.token) && tokens[1].equals(User.Delete.token)) {
            String name = tokens[2];
            command = new User.Delete(name);
        } else if (tokens.length == 2 && tokens[0].equals(Database.token) && tokens[1].equals(Database.List.token)) {
            command = new Database.List();
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Create.token)) {
            String database = tokens[2];
            command = new Database.Create(database);
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Delete.token)) {
            String database = tokens[2];
            command = new Database.Delete(database);
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Schema.token)) {
            String database = tokens[2];
            command = new Database.Schema(database);
        } else if (tokens.length == 3 && tokens[0].equals(Database.token) && tokens[1].equals(Database.Replicas.token)) {
            String database = tokens[2];
            command = new Database.Replicas(database);
        } else if (tokens.length >= 3 && tokens[0].equals(Transaction.token) &&
                (tokens[2].equals("write") || tokens[2].equals("read") || tokens[2].equals("schema"))) {
            String database = tokens[1];
            TypeDBTransaction.Type transactionType = tokens[2].equals("write") ? TypeDBTransaction.Type.WRITE : tokens[2].equals("read") ? TypeDBTransaction.Type.READ : TypeDBTransaction.Type.SCHEMA;
            TypeDBOptions options;
            if (tokens.length > 3) options = Options.from(Arrays.copyOfRange(tokens, 3, tokens.length), isCloud);
            else options = new TypeDBOptions();
            command = new Transaction(database, transactionType, options);
        }
        return command;
    }

}
