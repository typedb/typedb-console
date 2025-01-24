/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.console;

import com.typedb.console.command.REPLCommand;
import com.typedb.console.command.TransactionREPLCommand;
import com.typedb.console.common.Either;
import com.typedb.console.common.Printer;
import com.typedb.console.common.exception.TypeDBConsoleException;
import com.typedb.console.common.util.Java;
import com.typedb.driver.TypeDB;
import com.typedb.driver.api.Credentials;
import com.typedb.driver.api.Driver;
import com.typedb.driver.api.DriverOptions;
import com.typedb.driver.api.QueryType;
import com.typedb.driver.api.Transaction;
import com.typedb.driver.api.answer.ConceptRow;
import com.typedb.driver.api.answer.JSON;
import com.typedb.driver.api.answer.QueryAnswer;
import com.typedb.driver.api.database.Database;
import com.typedb.driver.api.user.User;
import com.typedb.driver.common.exception.TypeDBDriverException;
import io.sentry.Sentry;
import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.typedb.console.Version.VERSION;
import static com.typedb.console.common.Printer.QUERY_COMPILATION_SUCCESS;
import static com.typedb.console.common.Printer.QUERY_SUCCESS;
import static com.typedb.console.common.Printer.QUERY_WRITE_SUCCESS;
import static com.typedb.console.common.Printer.TOTAL_ANSWERS;
import static com.typedb.console.common.exception.ErrorMessage.Console.INCOMPATIBLE_JAVA_RUNTIME;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.jline.builtins.Completers.TreeCompleter.node;

public class TypeDBConsole {

    private static final String DISTRIBUTION_NAME = "TypeDB Console";
    private static final String COPYRIGHT = "\n" +
            "Welcome to TypeDB Console. You are now in TypeDB Wonderland!\n";
    private static final Path COMMAND_HISTORY_FILE =
            Paths.get(System.getProperty("user.home"), ".typedb-console-repl-history").toAbsolutePath();
    private static final Path TRANSACTION_HISTORY_FILE =
            Paths.get(System.getProperty("user.home"), ".typedb-console-transaction-repl-history").toAbsolutePath();
    private static final String DIAGNOSTICS_REPORTING_URI = "https://7f0ccb67b03abfccbacd7369d1f4ac6b@o4506315929812992.ingest.sentry.io/4506355433537536";
    private static final Logger LOG = LoggerFactory.getLogger(TypeDBConsole.class);

    private static final Duration PASSWORD_EXPIRY_WARN = Duration.ofDays(7);

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
            Sentry.captureException(e);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        configureAndVerifyJavaVersion();
        CLIOptions options = parseCLIOptions(args);
        configureDiagnostics(options.diagnosticsDisabled);
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
            TypeDBConsoleException exception = TypeDBConsoleException.of(INCOMPATIBLE_JAVA_RUNTIME, majorVersion);
            throw exception;
        }
    }

    /**
     * We initialise diagnostics in the default set-up which will only report uncaught exceptions.
     */
    private static void configureDiagnostics(boolean diagnosticsDisabled) {
        Sentry.init(options -> {
            options.setDsn(DIAGNOSTICS_REPORTING_URI);
            options.setSendDefaultPii(false);
            options.setRelease(releaseName());
            if (!diagnosticsDisabled) options.setEnabled(true);
            else options.setEnabled(false);
        });
        io.sentry.protocol.User user = new io.sentry.protocol.User();
        user.setUsername(userID());
        Sentry.setUser(user);
    }

    private static String releaseName() {
        return DISTRIBUTION_NAME + "@" + VERSION;
    }

    private static String userID() {
        try {
            byte[] mac = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getHardwareAddress();
            byte[] macHash = MessageDigest.getInstance("SHA-256").digest(mac);
            byte[] truncatedHash = Arrays.copyOfRange(macHash, 0, 8);
            return String.format("%X", ByteBuffer.wrap(truncatedHash).getLong());
        } catch (NoSuchAlgorithmException | IOException | NullPointerException e) {
            return "";
        }
    }

    private static CLIOptions parseCLIOptions(String[] args) {
        CLIOptions options = new CLIOptions();
        CommandLine CLI = new CommandLine(options);
        try {
            int exitCode = CLI.execute(args);
            if (exitCode == 0) {
                if (CLI.isUsageHelpRequested() || CLI.isVersionHelpRequested()) {
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
        printer.infoln(COPYRIGHT);
        boolean isCloud = options.cloud() != null;
        try (Driver driver = createDriver(options)) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, COMMAND_HISTORY_FILE)
                    .completer(getCompleter(driver))
                    .build();
            while (true) {
                REPLCommand command;
                try {
                    command = REPLCommand.readREPLCommand(reader, printer, "> ", isCloud);
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isExit()) {
                    break;
                } else if (command.isHelp()) {
                    printer.infoln(REPLCommand.createHelpMenu(driver, isCloud));
                } else if (command.isClear()) {
                    reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                } else if (command.isUserList()) {
                    runUserList(driver);
                } else if (command.isUserCreate()) {
                    runUserCreate(driver, command.asUserCreate().user(), command.asUserCreate().password());
                } else if (command.isUserPasswordUpdate()) {
                    REPLCommand.User.PasswordUpdate userPasswordUpdate = command.asUserPasswordUpdate();
                    String username = driver.users().getCurrentUser().name();
                    boolean passwordUpdateSuccessful = runUserPasswordUpdate(driver,
                            userPasswordUpdate.user(),
                            userPasswordUpdate.password());
                    if (passwordUpdateSuccessful && userPasswordUpdate.user().equals(username)) {
                        printer.infoln("Please login again with your updated password.");
                        break;
                    }
                } else if (command.isUserDelete()) {
                    runUserDelete(driver, command.asUserDelete().user());
                } else if (command.isDatabaseList()) {
                    runDatabaseList(driver);
                } else if (command.isDatabaseCreate()) {
                    runDatabaseCreate(driver, command.asDatabaseCreate().database());
                } else if (command.isDatabaseDelete()) {
                    runDatabaseDelete(driver, command.asDatabaseDelete().database());
                } else if (command.isDatabaseSchema()) {
                    runDatabaseSchema(driver, command.asDatabaseSchema().database());
//                } else if (command.isDatabaseReplicas()) {
//                    runDatabaseReplicas(driver, isCloud, command.asDatabaseReplicas().database());
                } else if (command.isTransaction()) {
                    String database = command.asTransaction().database();
                    Transaction.Type transactionType = command.asTransaction().transactionType();
//                    Options Options = command.asTransaction().options();
//                    if (Options.readAnyReplica().isPresent() && !isCloud) {
//                        printer.error("The option '--any-replica' is only available in TypeDB Cloud.");
//                        continue;
//                    }
                    boolean shouldExit = transactionREPL(driver, isCloud, database, transactionType/*, Options*/);
                    if (shouldExit) break;
                }
            }
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    private Completers.TreeCompleter getCompleter(Driver driver) {
        Completer databaseNameCompleter = (reader, line, candidates) -> driver.databases().all().stream()
                .map(Database::name)
                .filter(name -> name.startsWith(line.word()))
                .forEach(name -> candidates.add(new Candidate(name)));
        Completer userNameCompleter = (reader, line, candidates) -> {
            driver.users().all().stream()
                    .map(User::name)
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
        nodes.add(node(REPLCommand.User.token,
                node(REPLCommand.User.List.token),
                node(REPLCommand.User.Create.token),
                node(REPLCommand.User.PasswordUpdate.token),
                node(REPLCommand.User.Delete.token,
                        node(userNameCompleter))
        ));
        nodes.add(node(REPLCommand.Transaction.token,
                node(databaseNameCompleter,
                        node(new StringsCompleter(
                                REPLCommand.Transaction.readToken,
                                REPLCommand.Transaction.writeToken,
                                REPLCommand.Transaction.schemaToken
                        ))
                )
        ));
        nodes.add(node(REPLCommand.Help.token));
        nodes.add(node(REPLCommand.Clear.token));
        nodes.add(node(REPLCommand.Exit.token));
        return new Completers.TreeCompleter(nodes);
    }

    private boolean transactionREPL(Driver driver, boolean isCloud, String database, Transaction.Type transactionType/*, Options options*/) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser().escapeChars(null))
                .variable(LineReader.HISTORY_FILE, TRANSACTION_HISTORY_FILE)
                .build();
        StringBuilder promptBuilder = new StringBuilder(database + "::" + transactionType.name().toLowerCase());
//        if (isCloud && options.readAnyReplica().isPresent() && options.readAnyReplica().get()) {
//            promptBuilder.append("[any-replica]");
//        }
        try (Transaction tx = driver.transaction(database, transactionType/*, options*/)) {
            while (true) {
                Either<TransactionREPLCommand, String> command;
                try {
                    String prompt = promptBuilder + "> ";
                    command = TransactionREPLCommand.readCommand(reader, prompt);
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isSecond()) {
                    printer.error(command.second());
                } else {
                    TransactionREPLCommand replCommand = command.first();
                    if (replCommand.isExit()) {
                        return true;
                    } else if (replCommand.isClear()) {
                        reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                    } else if (replCommand.isHelp()) {
                        printer.infoln(TransactionREPLCommand.createHelpMenu());
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
                        runQueriesPrintAnswers(tx, replCommand.asQuery().query());
                    }
                }
            }
        } catch (TypeDBDriverException e) {
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
        inlineCommands = inlineCommands.stream().map(String::trim).filter(x -> !x.isEmpty()).collect(toList());
        boolean[] cancelled = new boolean[]{false};
        terminal.handle(Terminal.Signal.INT, s -> cancelled[0] = true);
        boolean isCloud = options.cloud() != null;
        try (Driver driver = createDriver(options)) {
            for (int i = 0; i < inlineCommands.size() && !cancelled[0]; i++) {
                String commandString = inlineCommands.get(i);
                printer.infoln("+ " + commandString);
                if (commandString.startsWith("#")) continue;
                REPLCommand command = REPLCommand.readREPLCommand(commandString, null, isCloud);
                if (command != null) {
                    if (command.isUserList()) {
                        boolean success = runUserList(driver);
                        if (!success) return false;
                    } else if (command.isUserCreate()) {
                        boolean success = runUserCreate(driver, command.asUserCreate().user(), command.asUserCreate().password());
                        if (!success) return false;
                    } else if (command.isUserPasswordUpdate()) {
                        REPLCommand.User.PasswordUpdate userPasswordUpdate = command.asUserPasswordUpdate();
                        String username = driver.users().getCurrentUser().name();
                        boolean passwordUpdateSuccessful = runUserPasswordUpdate(driver,
                                userPasswordUpdate.user(),
                                userPasswordUpdate.password());
                        if (passwordUpdateSuccessful && userPasswordUpdate.user().equals(username)) {
                            printer.infoln("Please login again with your updated password.");
                            break;
                        } else return false;
                    } else if (command.isUserDelete()) {
                        boolean success = runUserDelete(driver, command.asUserDelete().user());
                        if (!success) return false;
                    } else if (command.isDatabaseList()) {
                        boolean success = runDatabaseList(driver);
                        if (!success) return false;
                    } else if (command.isDatabaseCreate()) {
                        boolean success = runDatabaseCreate(driver, command.asDatabaseCreate().database());
                        if (!success) return false;
                    } else if (command.isDatabaseSchema()) {
                        boolean success = runDatabaseSchema(driver, command.asDatabaseSchema().database());
                        if (!success) return false;
                    } else if (command.isDatabaseDelete()) {
                        boolean success = runDatabaseDelete(driver, command.asDatabaseDelete().database());
                        if (!success) return false;
//                    } else if (command.isDatabaseReplicas()) {
//                        boolean success = runDatabaseReplicas(driver, isCloud, command.asDatabaseReplicas().database());
//                        if (!success) return false;
                    } else if (command.isTransaction()) {
                        String database = command.asTransaction().database();
                        Transaction.Type transactionType = command.asTransaction().transactionType();
//                        Options sessionOptions = command.asTransaction().options();
//                        if (sessionOptions.readAnyReplica().isPresent() && !isCloud) {
//                            printer.error("The option '--any-replica' is only available in TypeDB Cloud.");
//                            return false;
//                        }
                        try (Transaction tx = driver.transaction(database, transactionType)) {
                            for (i += 1; i < inlineCommands.size() && !cancelled[0]; i++) {
                                String txCommandString = inlineCommands.get(i);
                                printer.infoln("++ " + txCommandString);
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
                                    boolean success = runSource(tx, source.file(), source.printAnswers()).success();
                                    if (!success) return false;
                                } else if (txCommand.first().isQuery()) {
                                    boolean success = runQueriesPrintAnswers(tx, txCommand.first().asQuery().query()).success();
                                    if (!success) return false;
                                } else {
                                    printer.error("Command is not available while running console script.");
                                }
                            }
                        } catch (TypeDBDriverException e) {
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
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        } finally {
            executorService.shutdownNow();
        }
        return true;
    }

    private Driver createDriver(CLIOptions options) {
        try {
            Driver driver;
            Credentials credentials = new Credentials(options.username(), options.password());
            DriverOptions driverOptions = new DriverOptions(options.tlsEnabled(), options.tlsRootCA());
            if (options.core() != null) {
                driver = TypeDB.coreDriver(options.core(), credentials, driverOptions);
            } else if (options.cloud() != null) {
                String[] optCloud = options.cloud();
                if (Arrays.stream(optCloud).anyMatch(address -> address.contains("="))) {
                    Map<String, String> addressTranslation = Arrays.stream(optCloud).map(address -> address.split("=", 2))
                            .collect(toUnmodifiableMap(parts -> parts[0], parts -> parts[1]));
                    driver = TypeDB.cloudDriver(addressTranslation, credentials, driverOptions);
                } else {
                    driver = TypeDB.cloudDriver(Set.of(optCloud), credentials, driverOptions);
                }
//                Optional<Duration> passwordExpiry = driver.users().get(options.username)
//                        .passwordExpirySeconds().map(Duration::ofSeconds);
//                if (passwordExpiry.isPresent() && passwordExpiry.get().compareTo(PASSWORD_EXPIRY_WARN) < 0) {
//                    printer.info("Your password will expire within " + (passwordExpiry.get().toHours() + 1) + " hour(s).");
            } else {
                driver = TypeDB.coreDriver(TypeDB.DEFAULT_ADDRESS, credentials, driverOptions);
            }
            return driver;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            System.exit(1);
            return null; // unreachable, but needed to satisfy the compiler
        }
    }

    private boolean runUserList(Driver driver) {
        try {
            if (!driver.users().all().isEmpty()) {
                driver.users().all().forEach(user -> {
//                    Optional<Long> expirySeconds = user.passwordExpirySeconds();
//                    if (expirySeconds.isPresent()) {
//                        printer.info(user.username() + " (expiry within: " + (Duration.ofSeconds(expirySeconds.get()).toHours() + 1) + " hours)");
//                    } else {
                    printer.infoln(user.name());
//                    }
                });
            } else printer.infoln("No users are present on the server.");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserCreate(Driver driver, String username, String password) {
        try {
            driver.users().create(username, password);
            printer.infoln("User '" + username + "' created");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserPasswordUpdate(Driver driver, String username, String password) {
        try {
            if (driver.users().contains(username)) {
                driver.users().get(username).updatePassword(password);
                printer.infoln("Update password for user '" + username + "'");
                return true;
            } else {
                printer.infoln("No such user '" + username + "'");
                return false;
            }
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserDelete(Driver driver, String username) {
        try {
            driver.users().get(username).delete();
            printer.infoln("User '" + username + "' deleted");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseList(Driver driver) {
        try {
            if (driver.databases().all().size() > 0)
                driver.databases().all().forEach(database -> printer.infoln(database.name()));
            else printer.infoln("No databases are present on the server.");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseCreate(Driver driver, String database) {
        try {
            driver.databases().create(database);
            printer.infoln("Database '" + database + "' created");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseSchema(Driver driver, String database) {
        try {
            String schema = driver.databases().get(database).schema();
            printer.infoln(schema);
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseDelete(Driver driver, String database) {
        try {
            driver.databases().get(database).delete();
            printer.infoln("Database '" + database + "' deleted");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

//    private boolean runDatabaseReplicas(Driver driver, boolean isCloud, String database) {
//        try {
//            if (!isCloud) {
//                printer.error("The command 'database replicas' is only available in TypeDB Cloud.");
//                return false;
//            }
//            for (Database.Replica replica : driver.databases().get(database).replicas()) {
//                printer.databaseReplica(replica);
//            }
//            return true;
//        } catch (TypeDBDriverException e) {
//            printer.error(e.getMessage());
//            return false;
//        }
//    }

    private void runCommit(Transaction tx) {
        tx.commit();
        printer.infoln("Transaction changes committed");
    }

    private void runRollback(Transaction tx) {
        tx.rollback();
        printer.infoln("Transaction changes have rolled back, i.e. erased, and not committed");
    }

    private void runClose(Transaction tx) {
        tx.close();
        if (tx.getType().isWrite() || tx.getType().isSchema())
            printer.infoln("Transaction closed without committing changes");
        else printer.infoln("Transaction closed");
    }

    private RunQueriesResult runSource(Transaction tx, String file, boolean printAnswers) {
        try {
            String queryString = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
            if (printAnswers) return runQueriesPrintAnswers(tx, queryString);
            else return runQueries(tx, queryString);
        } catch (IOException e) {
            printer.error("Failed to open file '" + file + "'");
            return RunQueriesResult.error();
        }
    }

    private RunQueriesResult runQueries(Transaction tx, String queryString) {
        if (queryString.isBlank()) return RunQueriesResult.error();
        runQuery(tx, queryString);
        return new RunQueriesResult(true);
    }

    private RunQueriesResult runQueriesPrintAnswers(Transaction tx, String queryString) {
        if (queryString.isBlank()) return RunQueriesResult.error();
        runQueryPrintAnswers(tx, queryString);
        return new RunQueriesResult(true);
    }

    @SuppressWarnings("CheckReturnValue")
    private void runQuery(Transaction tx, String queryString) {
        tx.query(queryString).resolve();
        printer.infoln(QUERY_WRITE_SUCCESS);
    }

    private void runQueryPrintAnswers(Transaction tx, String queryString) {
        QueryAnswer answer = tx.query(queryString).resolve();
        QueryType queryType = answer.getQueryType();
        printer.infoln(QUERY_COMPILATION_SUCCESS);

        if (answer.isOk()) {
            printer.infoln(QUERY_SUCCESS);
        } else if (answer.isConceptRows()) {
            Stream<ConceptRow> resultRows = answer.asConceptRows().stream();
            AtomicBoolean first = new AtomicBoolean(true);
            printCancellableResult(resultRows, row -> {
                printer.conceptRow(row, queryType, tx, first.get());
                first.set(false);
            });
        } else if (answer.isConceptDocuments()) {
            Stream<JSON> resultDocuments = answer.asConceptDocuments().stream();
            AtomicBoolean first = new AtomicBoolean(true);
            printCancellableResult(resultDocuments, document -> {
                printer.conceptDocument(document, queryType, first.get());
                first.set(false);
            });
        } else {
            throw new TypeDBConsoleException("Query is of unrecognized type: " + queryString);
        }
    }

    private <T> void printCancellableResult(Stream<T> results, Consumer<T> printFn) {
        long[] counter = new long[]{0};
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
            printer.infoln("");
            printer.infoln("Finished. " + TOTAL_ANSWERS + counter[0]);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            throw (TypeDBDriverException) e.getCause();
        } catch (CancellationException e) {
            printer.infoln("");
            printer.infoln("The query has been cancelled. It may take some time for the cancellation to finish on the server side. " + TOTAL_ANSWERS + counter[0]);
        } finally {
            if (prevHandler != null) terminal.handle(Terminal.Signal.INT, prevHandler);
        }
    }

    @CommandLine.Command(name = "typedb console", mixinStandardHelpOptions = true, version = {VERSION})
    private static class CLIOptions implements Runnable {

        @CommandLine.Option(
                names = {"--core"},
                description = "TypeDB Core address to which Console will connect to"
        )
        private @Nullable
        String core;

        @CommandLine.Option(
                names = {"--cloud"},
                description = "TypeDB Cloud address(es) to which Console will connect to, or Cloud address translation 'configured-url=actual-url'",
                split = ","
        )
        private @Nullable
        String[] cloud;

        @CommandLine.Option(names = {"--username"}, description = "Username", required = true)
        private String username;

        @CommandLine.Option(
                names = {"--password"},
                description = "Password",
                prompt = "Password: ",
                interactive = true,
                arity = "0..1",
                required = true
        )
        private String password;

        @CommandLine.Option(
                names = {"--tls-enabled", "--encryption-enable"},
                description = "Whether to connect to TypeDB Cloud with TLS encryption"
        )
        private boolean tlsEnabled;

        @CommandLine.Option(
                names = {"--tls-root-ca", "--encryption-root-ca"},
                description = "Path to the TLS encryption root CA file"
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

        @CommandLine.Option(
                names = {"--diagnostics-disable"},
                description = "Disable diagnostics reporting"
        )
        private boolean diagnosticsDisabled;

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        private CLIOptions() {
        }

        @Override
        public void run() {
            validateOptions();
        }

        private void validateOptions() {
            if (core != null && cloud != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "Either '--core' or '--cloud' must be provided, but not both.");
            if (cloud != null) validateCloudOptions();
            if (!tlsEnabled && tlsRootCA != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' should only be supplied when '--tls-enabled' is set to 'true'");
        }

        private void validateCloudOptions() {
            assert cloud != null;
            if (Arrays.stream(cloud).map(address -> address.contains("=")).distinct().count() != 1) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Either all or none of the parameters supplied with '--cloud' must provide translation.");
            }
        }

        @Nullable
        private String core() {
            return core;
        }

        @Nullable
        private String[] cloud() {
            return cloud;
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

        private boolean diagnosticsDisabled() {
            return diagnosticsDisabled;
        }
    }
}
