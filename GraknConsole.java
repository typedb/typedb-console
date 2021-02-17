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
import grakn.client.common.exception.GraknClientException;
import grakn.client.concept.answer.ConceptMap;
import grakn.client.concept.answer.ConceptMapGroup;
import grakn.client.concept.answer.Numeric;
import grakn.client.concept.answer.NumericGroup;
import graql.lang.Graql;
import graql.lang.common.exception.GraqlException;
import graql.lang.query.GraqlCompute;
import graql.lang.query.GraqlDefine;
import graql.lang.query.GraqlDelete;
import graql.lang.query.GraqlInsert;
import graql.lang.query.GraqlMatch;
import graql.lang.query.GraqlQuery;
import graql.lang.query.GraqlUndefine;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraknConsole {
    private static final String COPYRIGHT = "\n" +
                    "Welcome to Grakn Console. You are now in Grakn Wonderland!\n" +
                    "Copyright (C) 2021 Grakn Labs\n";
    private final Printer printer;
    private ExecutorService executorService;
    private Terminal terminal;

    public GraknConsole(Printer printer) {
        this.printer = printer;
        try {
            executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            terminal = TerminalBuilder.builder().signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException e) {
            System.err.println("Failed to initialise terminal: " + e.getMessage());
            System.exit(1);
        }
    }

    private GraknClient createGraknClient(CommandLineOptions options) {
        GraknClient client;
        if (options.server() != null) {
            client = GraknClient.core(options.server());
        } else if (options.cluster() != null) {
            client = GraknClient.cluster(options.cluster().split(","));
        } else {
            client = GraknClient.core();
        }
        return client;
    }

    public boolean runScript(CommandLineOptions options) {
        String scriptString;
        try {
            scriptString = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(options.script()))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            printer.error("Failed to open file '" + options.script() + "'");
            return false;
        }
        boolean[] cancelled = new boolean[] { false };
        terminal.handle(Terminal.Signal.INT, s -> cancelled[0] = true);
        try (GraknClient client = createGraknClient(options)) {
            List<String> commandStrings = Arrays.stream(scriptString.trim().split("\n")).map(x -> x.trim()).filter(x -> !x.isEmpty()).collect(Collectors.toList());
            int i = 0;
            for (; i < commandStrings.size() && !cancelled[0]; i++) {
                String commandString = commandStrings.get(i);
                printer.info("+ " + commandString);
                ReplCommand command = ReplCommand.getCommand(commandString);
                if (command != null) {
                    if (command.isDatabaseList()) {
                        boolean success = runDatabaseList(client);
                        if (!success) return false;
                    } else if (command.isDatabaseCreate()) {
                        boolean success = runDatabaseCreate(client, command.asDatabaseCreate().database());
                        if (!success) return false;
                    } else if (command.isDatabaseDelete()) {
                        boolean success = runDatabaseDelete(client, command.asDatabaseDelete().database());
                        if (!success) return false;
                    } else if (command.isDatabaseReplicas()) {
                        boolean success = runDatabaseReplicas(client, command.asDatabaseReplicas().database());
                        if (!success) return false;
                    } else if (command.isTransaction()) {
                        String database = command.asTransaction().database();
                        GraknClient.Session.Type sessionType = command.asTransaction().sessionType();
                        GraknClient.Transaction.Type transactionType = command.asTransaction().transactionType();
                        try (GraknClient.Session session = client.session(database, sessionType);
                             GraknClient.Transaction tx = session.transaction(transactionType)) {
                            for (i += 1; i < commandStrings.size() && !cancelled[0]; i++) {
                                String txCommandString = commandStrings.get(i);
                                printer.info("++ " + txCommandString);
                                TransactionReplCommand txCommand = Objects.requireNonNull(TransactionReplCommand.getCommand(txCommandString));
                                if (txCommand.isCommit()) {
                                    runCommit(tx);
                                    break;
                                } else if (txCommand.isRollback()) {
                                    runRollback(tx);
                                } else if (txCommand.isClose()) {
                                    runClose(tx);
                                    break;
                                } else if (txCommand.isSource()) {
                                    boolean success = runSource(tx, txCommand.asSource().file());
                                    if (!success) return false;
                                } else if (txCommand.isQuery()) {
                                    boolean success = runQuery(tx, txCommand.asQuery().query());
                                    if (!success) return false;
                                } else {
                                    printer.error("Command is not available while running console script.");
                                }
                            }
                        } catch (GraknClientException e) {
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
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
            return false;
        } finally {
            executorService.shutdownNow();
        }
        return true;
    }

    public void runInteractive(CommandLineOptions options) {
        printer.info(COPYRIGHT);
        try (GraknClient client = createGraknClient(options)) {
            runRepl(client);
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    private void runRepl(GraknClient client) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".grakn-console-command-history").toAbsolutePath())
                .build();
        while (true) {
            ReplCommand command;
            try {
                command = ReplCommand.getCommand(reader, printer, "> ");
            } catch (InterruptedException e) {
                break;
            }
            if (command.isExit()) {
                break;
            } else if (command.isHelp()) {
                printer.info(ReplCommand.getHelpMenu());
            } else if (command.isClear()) {
                reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
            } else if (command.isDatabaseList()) {
                runDatabaseList(client);
            } else if (command.isDatabaseCreate()) {
                runDatabaseCreate(client, command.asDatabaseCreate().database());
            } else if (command.isDatabaseDelete()) {
                runDatabaseDelete(client, command.asDatabaseDelete().database());
            } else if (command.isDatabaseReplicas()) {
                runDatabaseReplicas(client, command.asDatabaseReplicas().database());
            } else if (command.isTransaction()) {
                String database = command.asTransaction().database();
                GraknClient.Session.Type sessionType = command.asTransaction().sessionType();
                GraknClient.Transaction.Type transactionType = command.asTransaction().transactionType();
                boolean shouldExit = runTransactionRepl(client, database, sessionType, transactionType);
                if (shouldExit) break;
            }
        }
    }

    private boolean runTransactionRepl(GraknClient client, String database, GraknClient.Session.Type sessionType, GraknClient.Transaction.Type transactionType) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".grakn-console-transaction-history").toAbsolutePath())
                .build();
        String prompt = database + "::" + sessionType.name().toLowerCase() + "::" + transactionType.name().toLowerCase() + "> ";
        try (GraknClient.Session session = client.session(database, sessionType);
             GraknClient.Transaction tx = session.transaction(transactionType)) {
            while (true) {
                TransactionReplCommand command;
                try {
                    command = TransactionReplCommand.getCommand(reader, prompt);
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isExit()) {
                    return true;
                } else if (command.isClear()) {
                    reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                } else if (command.isHelp()) {
                    printer.info(TransactionReplCommand.getHelpMenu());
                } else if (command.isCommit()) {
                    runCommit(tx);
                    break;
                } else if (command.isRollback()) {
                    runRollback(tx);
                } else if (command.isClose()) {
                    runClose(tx);
                    break;
                } else if (command.isSource()) {
                    runSource(tx, command.asSource().file());
                } else if (command.isQuery()) {
                    runQuery(tx, command.asQuery().query());
                }
            }
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
        }
        return false;
    }

    private boolean runDatabaseList(GraknClient client) {
        try {
            if (client.databases().all().size() > 0) client.databases().all().forEach(database -> printer.info(database.name()));
            else printer.info("No databases are present on the server.");
            return true;
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseCreate(GraknClient client, String database) {
        try {
            client.databases().create(database);
            printer.info("Database '" + database + "' created");
            return true;
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseDelete(GraknClient client, String database) {
        try {
            client.databases().get(database).delete();
            printer.info("Database '" + database + "' deleted");
            return true;
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseReplicas(GraknClient client, String database) {
        try {
            if (!client.isCluster()) {
                printer.error("The command 'database replicas' is only available in Grakn Cluster.");
                return false;
            }
            for (GraknClient.Database.Replica replica : client.asCluster().databases().get(database).replicas()) {
                printer.databaseReplica(replica);
            }
            return true;
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private void runCommit(GraknClient.Transaction tx) {
        tx.commit();
        printer.info("Transaction changes committed");
    }

    private void runRollback(GraknClient.Transaction tx) {
        tx.rollback();
        printer.info("Transaction changes committed");
    }

    private void runClose(GraknClient.Transaction tx) {
        tx.close();
        printer.info("Transaction closed without committing changes");
    }

    private boolean runSource(GraknClient.Transaction tx, String file) {
        try {
            String queryString = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
            return runQuery(tx, queryString);
        } catch (IOException e) {
            printer.error("Failed to open file '" + file + "'");
            return false;
        }
    }

    private boolean runQuery(GraknClient.Transaction tx, String queryString) {
        List<GraqlQuery> queries;
        try {
            queries = Graql.parseQueries(queryString).collect(Collectors.toList());
        } catch (GraqlException e) {
            printer.error(e.getMessage());
            return false;
        }
        for (GraqlQuery query : queries) {
            if (query instanceof GraqlDefine) {
                tx.query().define(query.asDefine()).get();
                printer.info("Concepts have been defined");
            } else if (query instanceof GraqlUndefine) {
                tx.query().undefine(query.asUndefine()).get();
                printer.info("Concepts have been undefined");
            } else if (query instanceof GraqlInsert) {
                Stream<ConceptMap> result = tx.query().insert(query.asInsert());
                printCancellableResult(result, x -> printer.conceptMap(x, tx));
            } else if (query instanceof GraqlDelete) {
                tx.query().delete(query.asDelete()).get();
                printer.info("Concepts have been deleted");
            } else if (query instanceof GraqlMatch) {
                Stream<ConceptMap> result = tx.query().match(query.asMatch());
                printCancellableResult(result, x -> printer.conceptMap(x, tx));
            } else if (query instanceof GraqlMatch.Aggregate) {
                Numeric answer = tx.query().match(query.asMatchAggregate()).get();
                printer.numeric(answer);
            } else if (query instanceof GraqlMatch.Group) {
                Stream<ConceptMapGroup> result = tx.query().match(query.asMatchGroup());
                printCancellableResult(result, x -> printer.conceptMapGroup(x, tx));
            } else if (query instanceof GraqlMatch.Group.Aggregate) {
                Stream<NumericGroup> result = tx.query().match(query.asMatchGroupAggregate());
                printCancellableResult(result, x -> printer.numericGroup(x, tx));
            } else if (query instanceof GraqlCompute) {
                throw new GraknClientException("Compute query is not yet supported");
            }
        }
        return true;
    }

    private <T> void printCancellableResult(Stream<T> results, Consumer<T> printFn) {
        long[] counter = new long[] {0};
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
            throw (GraknClientException)e.getCause();
        } catch (CancellationException e) {
            Instant end = Instant.now();
            printer.info("answers: " + counter[0] + ", duration: " + Duration.between(start, end).toMillis() + " ms");
            printer.info("The query has been cancelled. It may take some time for the cancellation to finish on the server side.");
        } finally {
            if (prevHandler != null) terminal.handle(Terminal.Signal.INT, prevHandler);
        }
    }

    public static void main(String[] args) {
        GraknConsole console = new GraknConsole(new Printer(System.out, System.err));
        CommandLineOptions options = parseCommandLine(args);
        if (options.script() == null) {
            console.runInteractive(options);
        } else {
            boolean success = console.runScript(options);
            if (!success) System.exit(1);
        }
    }

    private static CommandLineOptions parseCommandLine(String[] args) {
        CommandLineOptions options = new CommandLineOptions();
        CommandLine command = new CommandLine(options);
        try {
            command.parseArgs(args);
            if (command.isUsageHelpRequested()) {
                command.usage(command.getOut());
                System.exit(0);
            } else if (command.isVersionHelpRequested()) {
                command.printVersionHelp(command.getOut());
                System.exit(0);
            } else {
                return options;
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

    @CommandLine.Command(name = "grakn console", mixinStandardHelpOptions = true, version = {grakn.console.Version.VERSION})
    public static class CommandLineOptions {

        @CommandLine.Option(names = {"--server"},
                description = "Grakn Core address to which Console will connect to")
        private @Nullable
        String server;

        @Nullable
        public String server() {
            return server;
        }

        @CommandLine.Option(names = {"--cluster"},
                description = "Grakn Cluster address to which Console will connect to")
        private @Nullable
        String cluster;

        @Nullable
        public String cluster() {
            return cluster;
        }

        @CommandLine.Option(names = {"--script"},
                description = "Script with commands to run in the Console, without interactive mode")
        private @Nullable
        String script;

        @Nullable
        public String script() {
            return script;
        }
    }
}
