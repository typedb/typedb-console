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

package com.vaticle.typedb.console;

import com.vaticle.typedb.client.TypeDB;
import com.vaticle.typedb.client.api.TypeDBClient;
import com.vaticle.typedb.client.api.TypeDBCredential;
import com.vaticle.typedb.client.api.TypeDBOptions;
import com.vaticle.typedb.client.api.TypeDBSession;
import com.vaticle.typedb.client.api.TypeDBTransaction;
import com.vaticle.typedb.client.api.answer.ConceptMap;
import com.vaticle.typedb.client.api.answer.ConceptMapGroup;
import com.vaticle.typedb.client.api.answer.Numeric;
import com.vaticle.typedb.client.api.answer.NumericGroup;
import com.vaticle.typedb.client.api.database.Database;
import com.vaticle.typedb.client.common.exception.TypeDBClientException;
import com.vaticle.typedb.common.collection.Either;
import com.vaticle.typedb.common.util.Java;
import com.vaticle.typedb.console.command.ReplCommand;
import com.vaticle.typedb.console.command.TransactionReplCommand;
import com.vaticle.typedb.console.common.Printer;
import com.vaticle.typedb.console.common.exception.TypeDBConsoleException;
import com.vaticle.typeql.lang.TypeQL;
import com.vaticle.typeql.lang.common.exception.TypeQLException;
import com.vaticle.typeql.lang.query.TypeQLCompute;
import com.vaticle.typeql.lang.query.TypeQLDefine;
import com.vaticle.typeql.lang.query.TypeQLDelete;
import com.vaticle.typeql.lang.query.TypeQLInsert;
import com.vaticle.typeql.lang.query.TypeQLMatch;
import com.vaticle.typeql.lang.query.TypeQLQuery;
import com.vaticle.typeql.lang.query.TypeQLUndefine;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.vaticle.typedb.common.collection.Collections.set;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Console.INCOMPATIBLE_JAVA_RUNTIME;
import static java.util.stream.Collectors.toList;

public class TypeDBConsole {
    private static final Logger LOG = LoggerFactory.getLogger(TypeDBConsole.class);

    private static final String COPYRIGHT = "\n" +
            "Welcome to TypeDB Console. You are now in TypeDB Wonderland!\n" +
            "Copyright (C) 2021 Vaticle\n";
    private final Printer printer;
    private ExecutorService executorService;
    private Terminal terminal;

    public TypeDBConsole(Printer printer) {
        this.printer = printer;
        try {
            executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            terminal = TerminalBuilder.builder().signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException e) {
            System.err.println("Failed to initialise terminal: " + e.getMessage());
            System.exit(1);
        }
    }

    private TypeDBClient createTypeDBClient(CommandLineOptions options) {
        TypeDBClient client = null;
        try {
            if (options.server() != null) {
                client = TypeDB.coreClient(options.server());
            } else {
                String optCluster = options.cluster();
                if (optCluster != null) {
                    client = TypeDB.clusterClient(set(optCluster.split(",")), createCredential(options));
                } else {
                    client = TypeDB.coreClient(TypeDB.DEFAULT_ADDRESS);
                }
            }
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            System.exit(1);
        }
        return client;
    }

    private TypeDBCredential createCredential(CommandLineOptions options) {
        TypeDBCredential credential;
        if (options.tlsEnabled()) {
            String optRootCa = options.tlsRootCA();
            if (optRootCa != null)
                credential = TypeDBCredential.tls(Paths.get(optRootCa));
            else
                credential = TypeDBCredential.tls();
        } else
            credential = TypeDBCredential.plainText();
        return credential;
    }

    public boolean runScript(CommandLineOptions options, String script) {
        String scriptLines;
        try {
            scriptLines = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(script))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            printer.error("Failed to open file '" + options.script() + "'");
            return false;
        }
        return runCommands(options, Arrays.stream(scriptLines.split("\n")).collect(toList()));
    }

    public boolean runCommands(CommandLineOptions options, List<String> commandStrings) {
        commandStrings = commandStrings.stream().map(x -> x.trim()).filter(x -> !x.isEmpty()).collect(toList());
        boolean[] cancelled = new boolean[]{false};
        terminal.handle(Terminal.Signal.INT, s -> cancelled[0] = true);
        try (TypeDBClient client = createTypeDBClient(options)) {
            int i = 0;
            for (; i < commandStrings.size() && !cancelled[0]; i++) {
                String commandString = commandStrings.get(i);
                printer.info("+ " + commandString);
                ReplCommand command = ReplCommand.getCommand(commandString, client.isCluster());
                if (command != null) {
                    if (command.isDatabaseList()) {
                        boolean success = runDatabaseList(client);
                        if (!success) return false;
                    } else if (command.isDatabaseCreate()) {
                        boolean success = runDatabaseCreate(client, command.asDatabaseCreate().database());
                        if (!success) return false;
                    } else if (command.isDatabaseSchema()) {
                        boolean success = runDatabaseSchema(client, command.asDatabaseSchema().database());
                        if (!success) return false;
                    } else if (command.isDatabaseDelete()) {
                        boolean success = runDatabaseDelete(client, command.asDatabaseDelete().database());
                        if (!success) return false;
                    } else if (command.isDatabaseReplicas()) {
                        boolean success = runDatabaseReplicas(client, command.asDatabaseReplicas().database());
                        if (!success) return false;
                    } else if (command.isTransaction()) {
                        String database = command.asTransaction().database();
                        TypeDBSession.Type sessionType = command.asTransaction().sessionType();
                        TypeDBTransaction.Type transactionType = command.asTransaction().transactionType();
                        TypeDBOptions sessionOptions = command.asTransaction().options();
                        if (sessionOptions.isCluster() && !client.isCluster()) {
                            printer.error("The option '--any-replica' is only available in TypeDB Cluster.");
                            return false;
                        }
                        try (TypeDBSession session = client.session(database, sessionType, sessionOptions);
                             TypeDBTransaction tx = session.transaction(transactionType)) {
                            for (i += 1; i < commandStrings.size() && !cancelled[0]; i++) {
                                String txCommandString = commandStrings.get(i);
                                printer.info("++ " + txCommandString);
                                Either<TransactionReplCommand, String> txCommand = TransactionReplCommand.getCommand(txCommandString);
                                if (txCommand.isSecond()) {
                                    printer.error(txCommand.second());
                                    return false;
                                } else if (txCommand.first().isCommit()) {
                                    runCommit(tx);
                                    break;
                                } else if (txCommand.first().isRollback()) {
                                    runRollback(tx);
                                } else if (txCommand.first().isClose()) {
                                    runClose(tx);
                                    break;
                                } else if (txCommand.first().isSource()) {
                                    boolean success = runSource(tx, txCommand.first().asSource().file());
                                    if (!success) return false;
                                } else if (txCommand.first().isQuery()) {
                                    boolean success = runQuery(tx, txCommand.first().asQuery().query());
                                    if (!success) return false;
                                } else {
                                    printer.error("Command is not available while running console script.");
                                }
                            }
                        } catch (TypeDBClientException e) {
                            printer.error(e.getMessage());
                            return false;
                        }
                    } else {
                        printer.error("Command is not available while running console script.");
                    }
                } else {
                    printer.error("Unrecognised command, exit console script.");
                    return false;
                }
            }
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        } finally {
            executorService.shutdownNow();
        }
        return true;
    }

    public void runInteractive(CommandLineOptions options) {
        printer.info(COPYRIGHT);
        try (TypeDBClient client = createTypeDBClient(options)) {
            runRepl(client);
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    private void runRepl(TypeDBClient client) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".typedb-console-command-history").toAbsolutePath())
                .build();
        while (true) {
            ReplCommand command;
            try {
                command = ReplCommand.getCommand(reader, printer, "> ", client.isCluster());
            } catch (InterruptedException e) {
                break;
            }
            if (command.isExit()) {
                break;
            } else if (command.isHelp()) {
                printer.info(ReplCommand.getHelpMenu(client));
            } else if (command.isClear()) {
                reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
            } else if (command.isDatabaseList()) {
                runDatabaseList(client);
            } else if (command.isDatabaseCreate()) {
                runDatabaseCreate(client, command.asDatabaseCreate().database());
            } else if (command.isDatabaseDelete()) {
                runDatabaseDelete(client, command.asDatabaseDelete().database());
            } else if (command.isDatabaseSchema()) {
                runDatabaseSchema(client, command.asDatabaseSchema().database());
            } else if (command.isDatabaseReplicas()) {
                runDatabaseReplicas(client, command.asDatabaseReplicas().database());
            } else if (command.isTransaction()) {
                String database = command.asTransaction().database();
                TypeDBSession.Type sessionType = command.asTransaction().sessionType();
                TypeDBTransaction.Type transactionType = command.asTransaction().transactionType();
                TypeDBOptions typedbOptions = command.asTransaction().options();
                if (typedbOptions.isCluster() && !client.isCluster()) {
                    printer.error("The option '--any-replica' is only available in TypeDB Cluster.");
                    continue;
                }
                boolean shouldExit = runTransactionRepl(client, database, sessionType, transactionType, typedbOptions);
                if (shouldExit) break;
            }
        }
    }

    private boolean runTransactionRepl(TypeDBClient client, String database, TypeDBSession.Type sessionType, TypeDBTransaction.Type transactionType, TypeDBOptions options) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".typedb-console-transaction-history").toAbsolutePath())
                .build();
        StringBuilder prompt = new StringBuilder(database + "::" + sessionType.name().toLowerCase() + "::" + transactionType.name().toLowerCase());
        if (options.isCluster() && options.asCluster().readAnyReplica().isPresent() && options.asCluster().readAnyReplica().get())
            prompt.append("[any-replica]");
        prompt.append("> ");
        try (TypeDBSession session = client.session(database, sessionType, options);
             TypeDBTransaction tx = session.transaction(transactionType, options)) {
            while (true) {
                Either<TransactionReplCommand, String> command;
                try {
                    command = TransactionReplCommand.getCommand(reader, prompt.toString());
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isSecond()) {
                    printer.error(command.second());
                    continue;
                } else {
                    TransactionReplCommand replCommand = command.first();
                    if (replCommand.isExit()) {
                        return true;
                    } else if (replCommand.isClear()) {
                        reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                    } else if (replCommand.isHelp()) {
                        printer.info(TransactionReplCommand.getHelpMenu());
                    } else if (replCommand.isCommit()) {
                        runCommit(tx);
                        break;
                    } else if (replCommand.isRollback()) {
                        runRollback(tx);
                    } else if (replCommand.isClose()) {
                        runClose(tx);
                        break;
                    } else if (replCommand.isSource()) {
                        runSource(tx, replCommand.asSource().file());
                    } else if (replCommand.isQuery()) {
                        runQuery(tx, replCommand.asQuery().query());
                    }
                }
            }
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
        }
        return false;
    }

    private boolean runUserList(TypeDBClient.Cluster client) {
        try {
            if (client.users().all().size() > 0)
                client.users().all().forEach(user -> printer.info(user.name()));
            else printer.info("No databases are present on the server.");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserCreate(TypeDBClient.Cluster client, String user, String password) {
        try {
            client.users().create(user, password);
            printer.info("User '" + user + "' created");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserDelete(TypeDBClient.Cluster client, String user) {
        try {
            client.users().get(user).delete();
            printer.info("User '" + user + "' deleted");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseList(TypeDBClient client) {
        try {
            if (client.databases().all().size() > 0)
                client.databases().all().forEach(database -> printer.info(database.name()));
            else printer.info("No databases are present on the server.");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseCreate(TypeDBClient client, String database) {
        try {
            client.databases().create(database);
            printer.info("Database '" + database + "' created");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseSchema(TypeDBClient client, String database) {
        try {
            String schema = client.databases().get(database).schema();
            printer.info(schema);
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseDelete(TypeDBClient client, String database) {
        try {
            client.databases().get(database).delete();
            printer.info("Database '" + database + "' deleted");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseReplicas(TypeDBClient client, String database) {
        try {
            if (!client.isCluster()) {
                printer.error("The command 'database replicas' is only available in TypeDB Cluster.");
                return false;
            }
            for (Database.Replica replica : client.asCluster().databases().get(database).replicas()) {
                printer.databaseReplica(replica);
            }
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private void runCommit(TypeDBTransaction tx) {
        tx.commit();
        printer.info("Transaction changes committed");
    }

    private void runRollback(TypeDBTransaction tx) {
        tx.rollback();
        printer.info("Transaction changes committed");
    }

    private void runClose(TypeDBTransaction tx) {
        tx.close();
        if (tx.type().isWrite()) printer.info("Transaction closed without committing changes");
        else printer.info("Transaction closed");
    }

    private boolean runSource(TypeDBTransaction tx, String file) {
        try {
            String queryString = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
            return runQuery(tx, queryString);
        } catch (IOException e) {
            printer.error("Failed to open file '" + file + "'");
            return false;
        }
    }

    private boolean runQuery(TypeDBTransaction tx, String queryString) {
        List<TypeQLQuery> queries;
        try {
            queries = TypeQL.parseQueries(queryString).collect(toList());
        } catch (TypeQLException e) {
            printer.error(e.getMessage());
            return false;
        }
        for (TypeQLQuery query : queries) {
            if (query instanceof TypeQLDefine) {
                tx.query().define(query.asDefine()).get();
                printer.info("Concepts have been defined");
            } else if (query instanceof TypeQLUndefine) {
                tx.query().undefine(query.asUndefine()).get();
                printer.info("Concepts have been undefined");
            } else if (query instanceof TypeQLInsert) {
                Stream<ConceptMap> result = tx.query().insert(query.asInsert());
                printCancellableResult(result, x -> printer.conceptMap(x, tx));
            } else if (query instanceof TypeQLDelete) {
                tx.query().delete(query.asDelete()).get();
                printer.info("Concepts have been deleted");
            } else if (query instanceof TypeQLMatch) {
                Stream<ConceptMap> result = tx.query().match(query.asMatch());
                printCancellableResult(result, x -> printer.conceptMap(x, tx));
            } else if (query instanceof TypeQLMatch.Aggregate) {
                Numeric answer = tx.query().match(query.asMatchAggregate()).get();
                printer.numeric(answer);
            } else if (query instanceof TypeQLMatch.Group) {
                Stream<ConceptMapGroup> result = tx.query().match(query.asMatchGroup());
                printCancellableResult(result, x -> printer.conceptMapGroup(x, tx));
            } else if (query instanceof TypeQLMatch.Group.Aggregate) {
                Stream<NumericGroup> result = tx.query().match(query.asMatchGroupAggregate());
                printCancellableResult(result, x -> printer.numericGroup(x, tx));
            } else if (query instanceof TypeQLCompute) {
                throw new TypeDBConsoleException("Compute query is not yet supported");
            }
        }
        return true;
    }

    private <T> void printCancellableResult(Stream<T> results, Consumer<T> printFn) {
        long[] counter = new long[]{0};
        Instant start = Instant.now();
        Terminal.SignalHandler prevHandler = null;
        try {
            Iterator<T> iterator = results.iterator();
            Future<?> answerPrintingJob = executorService.submit(() -> {
                while (iterator.hasNext() && !Thread.interrupted()) {
                    printFn.accept(iterator.next());
                    counter[0]++;
                }
            });
            prevHandler = terminal.handle(Terminal.Signal.INT, s -> answerPrintingJob.cancel(true));
            answerPrintingJob.get();
            Instant end = Instant.now();
            printer.info("answers: " + counter[0] + ", duration: " + Duration.between(start, end).toMillis() + " ms");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            throw (TypeDBClientException) e.getCause();
        } catch (CancellationException e) {
            Instant end = Instant.now();
            printer.info("answers: " + counter[0] + ", duration: " + Duration.between(start, end).toMillis() + " ms");
            printer.info("The query has been cancelled. It may take some time for the cancellation to finish on the server side.");
        } finally {
            if (prevHandler != null) terminal.handle(Terminal.Signal.INT, prevHandler);
        }
    }

    public static void main(String[] args) {
        configureAndVerifyJavaVersion();
        CommandLineOptions options = parseCommandLine(args);
        TypeDBConsole console = new TypeDBConsole(new Printer(System.out, System.err));
        if (options.script() == null && options.commands() == null) {
            console.runInteractive(options);
        } else if (options.script() != null) {
            boolean success = console.runScript(options, options.script());
            if (!success) System.exit(1);
        } else if (options.commands() != null) {
            boolean success = console.runCommands(options, options.commands());
            if (!success) System.exit(1);
        }
    }

    private static void configureAndVerifyJavaVersion() {
        int majorVersion = Java.getMajorVersion();
        if (majorVersion == Java.UNKNOWN_VERSION) {
            LOG.warn("Could not detect Java version from version string '{}'. Will start TypeDB Server anyway.", System.getProperty("java.version"));
        } else if (majorVersion < 11) {
            throw TypeDBConsoleException.of(INCOMPATIBLE_JAVA_RUNTIME, majorVersion);
        }
    }

    private static CommandLineOptions parseCommandLine(String[] args) {
        CommandLineOptions options = new CommandLineOptions();
        CommandLine command = new CommandLine(options);
        try {
            int exitCode = command.execute(args);
            if (exitCode == 0) {
                if (command.isUsageHelpRequested()) {
                    command.usage(command.getOut());
                    System.exit(0);
                } else if (command.isVersionHelpRequested()) {
                    command.printVersionHelp(command.getOut());
                    System.exit(0);
                } else {
                    return options;
                }
            } else {
                System.exit(1);
            }
        } catch (CommandLine.ParameterException ex) {
            command.getErr().println(ex.getMessage());
            if (!CommandLine.UnmatchedArgumentException.printSuggestions(ex, command.getErr())) {
                ex.getCommandLine().usage(command.getErr());
            }
            System.exit(1);
        }
        return null;
    }

    @CommandLine.Command(name = "typedb console", mixinStandardHelpOptions = true, version = {com.vaticle.typedb.console.Version.VERSION})
    public static class CommandLineOptions implements Runnable {

        @CommandLine.Option(names = {"--server"},
                description = "TypeDB address to which Console will connect to")
        private @Nullable
        String server;

        @CommandLine.Option(names = {"--cluster"},
                description = "TypeDB Cluster address to which Console will connect to")
        private @Nullable
        String cluster;

        @CommandLine.Option(names = {"--tls-enabled"},
                description = "Whether to connect to Grakn Cluster with TLS encryption")
        private boolean tlsEnabled;

        @CommandLine.Option(names = {"--tls-root-ca"},
                description = "Path to the TLS root CA file")
        private @Nullable
        String tlsRootCA;

        @CommandLine.Option(names = {"--script"},
                description = "Script with commands to run in the Console, without interactive mode")
        private @Nullable
        String script;

        @CommandLine.Option(names = {"--command"},
                description = "Commands to run in the Console, without interactive mode")
        private @Nullable
        List<String> commands;

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public void run() {
            validateAddress();
            validateTLS();
        }

        private void validateAddress() {
            if (server != null && cluster != null) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Either '--server' or '--cluster' must be provided, but not both.");
            }
        }

        private void validateTLS() {
            if (server != null) {
                if (tlsEnabled)
                    throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-enabled' is only valid with '--cluster'");
                if (tlsRootCA != null)
                    throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' is only valid with '--cluster'");

            } else {
                if (!tlsEnabled && tlsRootCA != null)
                    throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' is only valid when '--tls-enabled' is set to 'true'");
            }
        }

        @Nullable
        public String server() {
            return server;
        }

        @Nullable
        public String cluster() {
            return cluster;
        }

        public boolean tlsEnabled() {
            return tlsEnabled;
        }

        @Nullable
        public String tlsRootCA() {
            return tlsRootCA;
        }

        @Nullable
        public String script() {
            return script;
        }

        @Nullable
        public List<String> commands() {
            return commands;
        }
    }
}
