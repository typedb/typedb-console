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
import com.vaticle.typedb.client.api.connection.TypeDBClient;
import com.vaticle.typedb.client.api.connection.TypeDBCredential;
import com.vaticle.typedb.client.api.connection.TypeDBOptions;
import com.vaticle.typedb.client.api.connection.TypeDBSession;
import com.vaticle.typedb.client.api.connection.TypeDBTransaction;
import com.vaticle.typedb.client.api.answer.ConceptMap;
import com.vaticle.typedb.client.api.answer.ConceptMapGroup;
import com.vaticle.typedb.client.api.answer.Numeric;
import com.vaticle.typedb.client.api.answer.NumericGroup;
import com.vaticle.typedb.client.api.connection.database.Database;
import com.vaticle.typedb.client.api.connection.user.User;
import com.vaticle.typedb.client.api.query.QueryFuture;
import com.vaticle.typedb.client.common.exception.TypeDBClientException;
import com.vaticle.typedb.common.collection.Either;
import com.vaticle.typedb.common.util.Java;
import com.vaticle.typedb.console.command.REPLCommand;
import com.vaticle.typedb.console.command.TransactionREPLCommand;
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
import com.vaticle.typeql.lang.query.TypeQLUpdate;
import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.StringsCompleter;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.jline.builtins.Completers.TreeCompleter.node;
import static com.vaticle.typedb.common.collection.Collections.set;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Console.INCOMPATIBLE_JAVA_RUNTIME;
import static java.util.stream.Collectors.toList;

public class TypeDBConsole {

    private static final String COPYRIGHT = "\n" +
            "Welcome to TypeDB Console. You are now in TypeDB Wonderland!\n" +
            "Copyright (C) 2021 Vaticle\n";
    private static final Path COMMAND_HISTORY_FILE =
            Paths.get(System.getProperty("user.home"), ".typedb-console-repl-history").toAbsolutePath();
    private static final Path TRANSACTION_HISTORY_FILE =
            Paths.get(System.getProperty("user.home"), ".typedb-console-transaction-repl-history").toAbsolutePath();
    private static final Logger LOG = LoggerFactory.getLogger(TypeDBConsole.class);

    private final Printer printer;
    private ExecutorService executorService;
    private Terminal terminal;

    private TypeDBConsole(Printer printer) {
        this.printer = printer;
        try {
            executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            terminal = TerminalBuilder.builder().signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException e) {
            System.err.println("Failed to initialise terminal: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        configureAndVerifyJavaVersion();
        CLIOptions options = parseCLIOptions(args);
        TypeDBConsole console = new TypeDBConsole(new Printer(System.out, System.err));
        if (options.script() == null && options.commands() == null) {
            console.runREPLMode(options);
        } else if (options.script() != null) {
            boolean success = console.runScriptMode(options, options.script());
            if (!success) System.exit(1);
        } else if (options.commands() != null) {
            boolean success = console.runInlineCommandMode(options, options.commands());
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

    private static CLIOptions parseCLIOptions(String[] args) {
        CLIOptions options = new CLIOptions();
        CommandLine CLI = new CommandLine(options);
        try {
            int exitCode = CLI.execute(args);
            if (exitCode == 0) {
                if (CLI.isUsageHelpRequested()) {
                    CLI.usage(CLI.getOut());
                    System.exit(0);
                } else if (CLI.isVersionHelpRequested()) {
                    CLI.printVersionHelp(CLI.getOut());
                    System.exit(0);
                } else {
                    return options;
                }
            } else {
                System.exit(1);
            }
        } catch (CommandLine.ParameterException ex) {
            CLI.getErr().println(ex.getMessage());
            if (!CommandLine.UnmatchedArgumentException.printSuggestions(ex, CLI.getErr())) {
                ex.getCommandLine().usage(CLI.getErr());
            }
            System.exit(1);
        }
        return null;
    }

    private void runREPLMode(CLIOptions options) {
        printer.info(COPYRIGHT);
        try (TypeDBClient client = createTypeDBClient(options)) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, COMMAND_HISTORY_FILE)
                    .completer(getCompleter(client))
                    .build();
            while (true) {
                REPLCommand command;
                try {
                    command = REPLCommand.readREPLCommand(reader, printer, "> ", client.isCluster());
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isExit()) {
                    break;
                } else if (command.isHelp()) {
                    printer.info(REPLCommand.createHelpMenu(client));
                } else if (command.isClear()) {
                    reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                } else if (command.isUserList()) {
                    runUserList(client);
                } else if (command.isUserCreate()) {
                    REPLCommand.User.Create userCommand = command.asUserCreate();
                    runUserCreate(client, userCommand.user(), userCommand.password());
                } else if (command.isUserDelete()) {
                    runUserDelete(client, command.asUserDelete().user());
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
                    boolean shouldExit = transactionREPL(client, database, sessionType, transactionType, typedbOptions);
                    if (shouldExit) break;
                }
            }
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    private Completers.TreeCompleter getCompleter(TypeDBClient client) {
        Completer databaseNameCompleter = (reader, line, candidates) -> client.databases().all().stream()
                .map(Database::name)
                .filter(name -> name.startsWith(line.word()))
                .forEach(name -> candidates.add(new Candidate(name)));
        Completer userNameCompleter = (reader, line, candidates) -> {
            client.asCluster().users().all().stream()
                    .map(User::username)
                    // "admin" user is excluded as it can't be deleted
                    .filter(name -> name.startsWith(line.word()) && !"admin".equals(name))
                    .forEach(name -> candidates.add(new Candidate(name)));
        };
        final List<Completers.TreeCompleter.Node> nodes = new ArrayList<>();
        nodes.add(
                node(REPLCommand.Database.token,
                        node(REPLCommand.Database.List.token),
                        node(REPLCommand.Database.Create.token),
                        node(REPLCommand.Database.Delete.token,
                                node(databaseNameCompleter)),
                        node(REPLCommand.Database.Schema.token,
                                node(databaseNameCompleter)
                        )
                ));
        if (client.isCluster()) {
            nodes.add(node(REPLCommand.User.token,
                    node(REPLCommand.User.List.token),
                    node(REPLCommand.User.Create.token),
                    node(REPLCommand.User.Delete.token,
                            node(userNameCompleter))
            ));
        }
        nodes.add(node(REPLCommand.Transaction.token,
                node(databaseNameCompleter,
                        node(new StringsCompleter("schema", "data"),
                                node(new StringsCompleter("read", "write")
                                        // TODO(vmax): complete [transaction-options] here
                                )
                        )
                )
        ));
        nodes.add(node(REPLCommand.Help.token));
        nodes.add(node(REPLCommand.Clear.token));
        nodes.add(node(REPLCommand.Exit.token));
        return new Completers.TreeCompleter(nodes);
    }

    private boolean transactionREPL(TypeDBClient client, String database, TypeDBSession.Type sessionType, TypeDBTransaction.Type transactionType, TypeDBOptions options) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, TRANSACTION_HISTORY_FILE)
                .build();
        StringBuilder prompt = new StringBuilder(database + "::" + sessionType.name().toLowerCase() + "::" + transactionType.name().toLowerCase());
        if (options.isCluster() && options.asCluster().readAnyReplica().isPresent() && options.asCluster().readAnyReplica().get())
            prompt.append("[any-replica]");
        prompt.append("> ");
        try (TypeDBSession session = client.session(database, sessionType, options);
             TypeDBTransaction tx = session.transaction(transactionType, options)) {
            while (true) {
                Either<TransactionREPLCommand, String> command;
                try {
                    command = TransactionREPLCommand.readCommand(reader, prompt.toString());
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isSecond()) {
                    printer.error(command.second());
                    continue;
                } else {
                    TransactionREPLCommand replCommand = command.first();
                    if (replCommand.isExit()) {
                        return true;
                    } else if (replCommand.isClear()) {
                        reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                    } else if (replCommand.isHelp()) {
                        printer.info(TransactionREPLCommand.createHelpMenu());
                    } else if (replCommand.isCommit()) {
                        runCommit(tx);
                        break;
                    } else if (replCommand.isRollback()) {
                        runRollback(tx);
                    } else if (replCommand.isClose()) {
                        runClose(tx);
                        break;
                    } else if (replCommand.isSource()) {
                        runSource(tx, replCommand.asSource().file(), replCommand.asSource().printAnswers());
                    } else if (replCommand.isQuery()) {
                        runQuery(tx, replCommand.asQuery().query(), true);
                    }
                }
            }
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
        }
        return false;
    }

    private boolean runScriptMode(CLIOptions options, String script) {
        String scriptLines;
        try {
            scriptLines = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(script))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            printer.error("Failed to open file '" + options.script() + "'");
            return false;
        }
        return runInlineCommandMode(options, Arrays.stream(scriptLines.split("\n")).collect(toList()));
    }

    private boolean runInlineCommandMode(CLIOptions options, List<String> inlineCommands) {
        inlineCommands = inlineCommands.stream().map(x -> x.trim()).filter(x -> !x.isEmpty()).collect(toList());
        boolean[] cancelled = new boolean[]{false};
        terminal.handle(Terminal.Signal.INT, s -> cancelled[0] = true);
        try (TypeDBClient client = createTypeDBClient(options)) {
            int i = 0;
            for (; i < inlineCommands.size() && !cancelled[0]; i++) {
                String commandString = inlineCommands.get(i);
                printer.info("+ " + commandString);
                REPLCommand command = REPLCommand.readREPLCommand(commandString, null, client.isCluster());
                if (command != null) {
                    if (command.isUserList()) {
                        boolean success = runUserList(client);
                        if (!success) return false;
                    } else if (command.isUserCreate()) {
                        boolean success = runUserCreate(client, command.asUserCreate().user(), command.asUserCreate().password());
                        if (!success) return false;
                    } else if (command.isUserDelete()) {
                        boolean success = runUserDelete(client, command.asUserDelete().user());
                        if (!success) return false;
                    } else if (command.isDatabaseList()) {
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
                            for (i += 1; i < inlineCommands.size() && !cancelled[0]; i++) {
                                String txCommandString = inlineCommands.get(i);
                                printer.info("++ " + txCommandString);
                                Either<TransactionREPLCommand, String> txCommand = TransactionREPLCommand.readCommand(txCommandString);
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
                                    TransactionREPLCommand.Source source = txCommand.first().asSource();
                                    boolean success = runSource(tx, source.file(), source.printAnswers());
                                    if (!success) return false;
                                } else if (txCommand.first().isQuery()) {
                                    boolean success = runQuery(tx, txCommand.first().asQuery().query(), true);
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

    private TypeDBClient createTypeDBClient(CLIOptions options) {
        TypeDBClient client = null;
        try {
            if (options.server() != null) {
                client = TypeDB.coreClient(options.server());
            } else {
                String optCluster = options.cluster();
                if (optCluster != null) {
                    client = TypeDB.clusterClient(set(optCluster.split(",")), createTypeDBCredential(options));
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

    private TypeDBCredential createTypeDBCredential(CLIOptions options) {
        TypeDBCredential credential;
        if (options.tlsEnabled()) {
            String optRootCa = options.tlsRootCA();
            if (optRootCa != null)
                credential = new TypeDBCredential(options.username(), options.password(), true, Paths.get(optRootCa));
            else
                credential = new TypeDBCredential(options.username(), options.password(), true);
        } else
            credential = new TypeDBCredential(options.username(), options.password(), false);
        return credential;
    }

    private boolean runUserList(TypeDBClient client) {
        try {
            if (!client.isCluster()) {
                printer.error("The command 'user list' is only available in TypeDB Cluster.");
                return false;
            }
            TypeDBClient.Cluster clientCluster = client.asCluster();
            if (clientCluster.users().all().size() > 0)
                clientCluster.users().all().forEach(user -> printer.info(user.username()));
            else printer.info("No users are present on the server.");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserCreate(TypeDBClient client, String user, String password) {
        try {
            if (!client.isCluster()) {
                printer.error("The command 'user create' is only available in TypeDB Cluster.");
                return false;
            }
            TypeDBClient.Cluster clientCluster = client.asCluster();
            clientCluster.users().create(user, password);
            printer.info("User '" + user + "' created");
            return true;
        } catch (TypeDBClientException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserDelete(TypeDBClient client, String user) {
        try {
            if (!client.isCluster()) {
                printer.error("The command 'user delete' is only available in TypeDB Cluster.");
                return false;
            }
            TypeDBClient.Cluster clientCluster = client.asCluster();
            clientCluster.users().get(user).delete();
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
        printer.info("Transaction changes have rolled back, i.e. erased, and not committed.");
    }

    private void runClose(TypeDBTransaction tx) {
        tx.close();
        if (tx.type().isWrite()) printer.info("Transaction closed without committing changes");
        else printer.info("Transaction closed");
    }

    private boolean runSource(TypeDBTransaction tx, String file, boolean printAnswers) {
        try {
            String queryString = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
            return runQuery(tx, queryString, printAnswers);
        } catch (IOException e) {
            printer.error("Failed to open file '" + file + "'");
            return false;
        }
    }

    private boolean runQuery(TypeDBTransaction tx, String queryString, boolean printAnswers) {
        List<TypeQLQuery> queries;
        try {
            queries = TypeQL.parseQueries(queryString).collect(toList());
        } catch (TypeQLException e) {
            printer.error(e.getMessage());
            return false;
        }
        List<CompletableFuture<Void>> running = new ArrayList<>();
        for (TypeQLQuery query : queries) {
            if (query instanceof TypeQLDefine) {
                QueryFuture<Void> defineFuture = tx.query().define(query.asDefine());
                if (printAnswers) {
                    defineFuture.get();
                    printer.info("Concepts have been defined");
                } else running.add(CompletableFuture.runAsync(defineFuture::get));
            } else if (query instanceof TypeQLUndefine) {
                QueryFuture<Void> undefineFuture = tx.query().undefine(query.asUndefine());
                if (printAnswers) {
                    undefineFuture.get();
                    printer.info("Concepts have been undefined");
                } else running.add(CompletableFuture.runAsync(undefineFuture::get));
            } else if (query instanceof TypeQLInsert) {
                Stream<ConceptMap> result = tx.query().insert(query.asInsert());
                if (printAnswers) printCancellableResult(result, x -> printer.conceptMap(x, tx));
                else running.add(CompletableFuture.runAsync(result::findFirst));
            } else if (query instanceof TypeQLDelete) {
                QueryFuture<Void> deleteFuture = tx.query().delete(query.asDelete());
                if (printAnswers) {
                    deleteFuture.get();
                    printer.info("Concepts have been deleted");
                } else running.add(CompletableFuture.runAsync(deleteFuture::get));
            } else if (query instanceof TypeQLUpdate) {
                Stream<ConceptMap> result = tx.query().update(query.asUpdate());
                if (printAnswers) printCancellableResult(result, x -> printer.conceptMap(x, tx));
                else running.add(CompletableFuture.runAsync(result::findFirst));
            } else if (query instanceof TypeQLMatch) {
                Stream<ConceptMap> result = tx.query().match(query.asMatch());
                if (printAnswers) printCancellableResult(result, x -> printer.conceptMap(x, tx));
                else running.add(CompletableFuture.runAsync(result::findFirst));
            } else if (query instanceof TypeQLMatch.Aggregate) {
                QueryFuture<Numeric> answerFuture = tx.query().match(query.asMatchAggregate());
                ;
                if (printAnswers) printer.numeric(answerFuture.get());
                else running.add(CompletableFuture.runAsync(answerFuture::get));
            } else if (query instanceof TypeQLMatch.Group) {
                Stream<ConceptMapGroup> result = tx.query().match(query.asMatchGroup());
                if (printAnswers) printCancellableResult(result, x -> printer.conceptMapGroup(x, tx));
                else running.add(CompletableFuture.runAsync(result::findFirst));
            } else if (query instanceof TypeQLMatch.Group.Aggregate) {
                Stream<NumericGroup> result = tx.query().match(query.asMatchGroupAggregate());
                if (printAnswers) printCancellableResult(result, x -> printer.numericGroup(x, tx));
                else running.add(CompletableFuture.runAsync(result::findFirst));
            } else if (query instanceof TypeQLCompute) {
                throw new TypeDBConsoleException("Compute query is not yet supported");
            } else {
                throw new TypeDBConsoleException("Query is of unrecognized type: " + query);
            }
        }
        CompletableFuture.allOf(running.toArray(new CompletableFuture[0]));
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

    @CommandLine.Command(name = "typedb console", mixinStandardHelpOptions = true, version = {com.vaticle.typedb.console.Version.VERSION})
    private static class CLIOptions implements Runnable {

        @CommandLine.Option(
                names = {"--server"},
                description = "TypeDB address to which Console will connect to"
        )
        private @Nullable
        String server;

        @CommandLine.Option(
                names = {"--cluster"},
                description = "TypeDB Cluster address to which Console will connect to"
        )
        private @Nullable
        String cluster;

        @CommandLine.Option(names = {"--username"}, description = "Username")
        private @Nullable
        String username;

        @CommandLine.Option(
                names = {"--password"},
                description = "Password",
                prompt = "Enter password:",
                interactive = true,
                arity = "0..1"
        )
        private @Nullable
        String password;

        @CommandLine.Option(
                names = {"--tls-enabled"},
                description = "Whether to connect to TypeDB Cluster with TLS encryption"
        )
        private boolean tlsEnabled;

        @CommandLine.Option(
                names = {"--tls-root-ca"},
                description = "Path to the TLS root CA file"
        )
        private @Nullable
        String tlsRootCA;

        @CommandLine.Option(
                names = {"--script"},
                description = "Script with commands to run in the Console, without interactive mode"
        )
        private @Nullable
        String script;

        @CommandLine.Option(
                names = {"--command"},
                description = "Commands to run in the Console, without interactive mode"
        )
        private @Nullable
        List<String> commands;

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        private CLIOptions() {
        }

        @Override
        public void run() {
            validateOptions();
        }

        private void validateOptions() {
            if (server != null && cluster != null) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Either '--server' or '--cluster' must be provided, but not both.");
            } else {
                if (cluster != null) validateClusterOptions();
                else validateServerOptions();
            }
        }

        private void validateServerOptions() {
            if (username != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--username' should only be supplied with '--cluster'");
            if (password != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--password' should only be supplied with '--cluster'");
            if (tlsEnabled)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-enabled' is only valid with '--cluster'");
            if (tlsRootCA != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' is only valid with '--cluster'");
        }

        private void validateClusterOptions() {
            if (username == null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--username' must be supplied with '--cluster'");
            if (password == null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--password' must be supplied with '--cluster'");
            if (!tlsEnabled && tlsRootCA != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' should only be supplied when '--tls-enabled' is set to 'true'");
        }

        @Nullable
        private String server() {
            return server;
        }

        @Nullable
        private String cluster() {
            return cluster;
        }

        private String username() {
            return username;
        }

        private String password() {
            return password;
        }

        private boolean tlsEnabled() {
            return tlsEnabled;
        }

        @Nullable
        private String tlsRootCA() {
            return tlsRootCA;
        }

        @Nullable
        private String script() {
            return script;
        }

        @Nullable
        private List<String> commands() {
            return commands;
        }
    }
}
