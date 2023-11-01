/*
 * Copyright (C) 2022 Vaticle
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

import com.vaticle.typedb.driver.TypeDB;
import com.vaticle.typedb.driver.api.TypeDBDriver;
import com.vaticle.typedb.driver.api.TypeDBCredential;
import com.vaticle.typedb.driver.api.TypeDBOptions;
import com.vaticle.typedb.driver.api.TypeDBSession;
import com.vaticle.typedb.driver.api.TypeDBTransaction;
import com.vaticle.typedb.driver.api.answer.ConceptMap;
import com.vaticle.typedb.driver.api.answer.ConceptMapGroup;
import com.vaticle.typedb.driver.api.answer.JSON;
import com.vaticle.typedb.driver.api.answer.ValueGroup;
import com.vaticle.typedb.driver.api.concept.value.Value;
import com.vaticle.typedb.driver.api.database.Database;
import com.vaticle.typedb.driver.api.user.User;
import com.vaticle.typedb.driver.common.exception.TypeDBDriverException;
import com.vaticle.typedb.common.collection.Either;
import com.vaticle.typedb.common.util.Java;
import com.vaticle.typedb.console.command.REPLCommand;
import com.vaticle.typedb.console.command.TransactionREPLCommand;
import com.vaticle.typedb.console.common.Printer;
import com.vaticle.typedb.console.common.exception.TypeDBConsoleException;
import com.vaticle.typeql.lang.TypeQL;
import com.vaticle.typeql.lang.common.TypeQLArg;
import com.vaticle.typeql.lang.common.exception.TypeQLException;
import com.vaticle.typeql.lang.query.TypeQLDefine;
import com.vaticle.typeql.lang.query.TypeQLDelete;
import com.vaticle.typeql.lang.query.TypeQLFetch;
import com.vaticle.typeql.lang.query.TypeQLInsert;
import com.vaticle.typeql.lang.query.TypeQLGet;
import com.vaticle.typeql.lang.query.TypeQLQuery;
import com.vaticle.typeql.lang.query.TypeQLUndefine;
import com.vaticle.typeql.lang.query.TypeQLUpdate;
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
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.vaticle.typedb.common.collection.Collections.set;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Console.INCOMPATIBLE_JAVA_RUNTIME;
import static java.util.stream.Collectors.toList;
import static org.jline.builtins.Completers.TreeCompleter.node;

public class TypeDBConsole {

    private static final String COPYRIGHT = "\n" +
            "Welcome to TypeDB Console. You are now in TypeDB Wonderland!\n" +
            "Copyright (C) 2022 Vaticle\n";
    private static final Path COMMAND_HISTORY_FILE =
            Paths.get(System.getProperty("user.home"), ".typedb-console-repl-history").toAbsolutePath();
    private static final Path TRANSACTION_HISTORY_FILE =
            Paths.get(System.getProperty("user.home"), ".typedb-console-transaction-repl-history").toAbsolutePath();
    private static final Logger LOG = LoggerFactory.getLogger(TypeDBConsole.class);

    private static final Duration PASSWORD_EXPIRY_WARN = Duration.ofDays(7);
    private static final int ONE_HOUR_IN_MILLIS = 60 * 60 * 1000;

    private final Printer printer;
    private ExecutorService executorService;
    private Terminal terminal;
    private boolean hasUncommittedChanges = false;

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
        boolean isEnterprise = options.enterprise() != null;
        try (TypeDBDriver driver = createTypeDBDriver(options)) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, COMMAND_HISTORY_FILE)
                    .completer(getCompleter(driver, isEnterprise))
                    .build();
            while (true) {
                REPLCommand command;
                try {
                    command = REPLCommand.readREPLCommand(reader, printer, "> ", isEnterprise);
                } catch (InterruptedException e) {
                    break;
                }
                if (command.isExit()) {
                    break;
                } else if (command.isHelp()) {
                    printer.info(REPLCommand.createHelpMenu(driver, isEnterprise));
                } else if (command.isClear()) {
                    reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                } else if (command.isUserList()) {
                    runUserList(driver, isEnterprise);
                } else if (command.isUserCreate()) {
                    runUserCreate(driver, isEnterprise, command.asUserCreate().user(), command.asUserCreate().password());
                } else if (command.isUserPasswordUpdate()) {
                    REPLCommand.User.PasswordUpdate userPasswordUpdate = command.asUserPasswordUpdate();
                    boolean passwordUpdateSuccessful = runUserPasswordUpdate(driver,
                            isEnterprise,
                            options.username,
                            userPasswordUpdate.passwordOld(),
                            userPasswordUpdate.passwordNew());
                    if (passwordUpdateSuccessful) {
                        printer.info("Please login again with your updated password.");
                        break;
                    }
                } else if (command.isUserPasswordSet()) {
                    REPLCommand.User.PasswordSet userPasswordSet = command.asUserPasswordSet();
                    boolean passwordSetSuccessful = runUserPasswordSet(driver,
                            isEnterprise,
                            userPasswordSet.user(),
                            userPasswordSet.password());
                    if (passwordSetSuccessful && userPasswordSet.user().equals(driver.user().username())) {
                        printer.info("Please login again with your updated password.");
                        break;
                    }
                } else if (command.isUserDelete()) {
                    runUserDelete(driver, isEnterprise, command.asUserDelete().user());
                } else if (command.isDatabaseList()) {
                    runDatabaseList(driver);
                } else if (command.isDatabaseCreate()) {
                    runDatabaseCreate(driver, command.asDatabaseCreate().database());
                } else if (command.isDatabaseDelete()) {
                    runDatabaseDelete(driver, command.asDatabaseDelete().database());
                } else if (command.isDatabaseSchema()) {
                    runDatabaseSchema(driver, command.asDatabaseSchema().database());
                } else if (command.isDatabaseReplicas()) {
                    runDatabaseReplicas(driver, isEnterprise, command.asDatabaseReplicas().database());
                } else if (command.isTransaction()) {
                    String database = command.asTransaction().database();
                    TypeDBSession.Type sessionType = command.asTransaction().sessionType();
                    TypeDBTransaction.Type transactionType = command.asTransaction().transactionType();
                    TypeDBOptions typedbOptions = command.asTransaction().options();
                    if (typedbOptions.readAnyReplica().isPresent() && !isEnterprise) {
                        printer.error("The option '--any-replica' is only available in TypeDB Enterprise.");
                        continue;
                    }
                    boolean shouldExit = transactionREPL(driver, isEnterprise, database, sessionType, transactionType, typedbOptions);
                    if (shouldExit) break;
                }
            }
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
    }

    private Completers.TreeCompleter getCompleter(TypeDBDriver driver, boolean isEnterprise) {
        Completer databaseNameCompleter = (reader, line, candidates) -> driver.databases().all().stream()
                .map(Database::name)
                .filter(name -> name.startsWith(line.word()))
                .forEach(name -> candidates.add(new Candidate(name)));
        Completer userNameCompleter = (reader, line, candidates) -> {
            driver.users().all().stream()
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
        if (isEnterprise) {
            nodes.add(node(REPLCommand.User.token,
                    node(REPLCommand.User.List.token),
                    node(REPLCommand.User.Create.token),
                    node(REPLCommand.User.PasswordUpdate.token),
                    node(REPLCommand.User.PasswordSet.token),
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

    private boolean transactionREPL(TypeDBDriver driver, boolean isEnterprise, String database, TypeDBSession.Type sessionType, TypeDBTransaction.Type transactionType, TypeDBOptions options) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser().escapeChars(null))
                .variable(LineReader.HISTORY_FILE, TRANSACTION_HISTORY_FILE)
                .build();
        StringBuilder promptBuilder = new StringBuilder(database + "::" + sessionType.name().toLowerCase() + "::" + transactionType.name().toLowerCase());
        if (isEnterprise && options.readAnyReplica().isPresent() && options.readAnyReplica().get()) {
            promptBuilder.append("[any-replica]");
        }
        options.transactionTimeoutMillis(ONE_HOUR_IN_MILLIS);
        try (TypeDBSession session = driver.session(database, sessionType, options);
             TypeDBTransaction tx = session.transaction(transactionType, options)) {
            hasUncommittedChanges = false;
            while (true) {
                Either<TransactionREPLCommand, String> command;
                try {
                    String prompt = hasUncommittedChanges ? promptBuilder + "*> " : promptBuilder + "> ";
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
                        RunQueriesResult result = runSource(tx, replCommand.asSource().file(), replCommand.asSource().printAnswers());
                        hasUncommittedChanges = result.hasChanges();
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
        boolean isEnterprise = options.enterprise() != null;
        try (TypeDBDriver driver = createTypeDBDriver(options)) {
            for (int i = 0; i < inlineCommands.size() && !cancelled[0]; i++) {
                String commandString = inlineCommands.get(i);
                printer.info("+ " + commandString);
                if (commandString.startsWith("#")) continue;
                REPLCommand command = REPLCommand.readREPLCommand(commandString, null, isEnterprise);
                if (command != null) {
                    if (command.isUserList()) {
                        boolean success = runUserList(driver, isEnterprise);
                        if (!success) return false;
                    } else if (command.isUserCreate()) {
                        boolean success = runUserCreate(driver, isEnterprise, command.asUserCreate().user(), command.asUserCreate().password());
                        if (!success) return false;
                    } else if (command.isUserDelete()) {
                        boolean success = runUserDelete(driver, isEnterprise, command.asUserDelete().user());
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
                    } else if (command.isDatabaseReplicas()) {
                        boolean success = runDatabaseReplicas(driver, isEnterprise, command.asDatabaseReplicas().database());
                        if (!success) return false;
                    } else if (command.isTransaction()) {
                        String database = command.asTransaction().database();
                        TypeDBSession.Type sessionType = command.asTransaction().sessionType();
                        TypeDBTransaction.Type transactionType = command.asTransaction().transactionType();
                        TypeDBOptions sessionOptions = command.asTransaction().options();
                        if (sessionOptions.readAnyReplica().isPresent() && !isEnterprise) {
                            printer.error("The option '--any-replica' is only available in TypeDB Enterprise.");
                            return false;
                        }
                        try (TypeDBSession session = driver.session(database, sessionType, sessionOptions);
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

    private TypeDBDriver createTypeDBDriver(CLIOptions options) {
        TypeDBDriver driver = null;
        try {
            if (options.server() != null) {
                driver = TypeDB.coreDriver(options.server());
            } else {
                String optEnterprise = options.enterprise();
                if (optEnterprise != null) {
                    driver = TypeDB.enterpriseDriver(set(optEnterprise.split(",")), createTypeDBCredential(options));
                    Optional<Duration> passwordExpiry = driver.users().get(options.username)
                            .passwordExpirySeconds().map(Duration::ofSeconds);
                    if (passwordExpiry.isPresent() && passwordExpiry.get().compareTo(PASSWORD_EXPIRY_WARN) < 0) {
                        printer.info("Your password will expire within " + (passwordExpiry.get().toHours() + 1) + " hour(s).");
                    }
                } else {
                    driver = TypeDB.coreDriver(TypeDB.DEFAULT_ADDRESS);
                }
            }
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            System.exit(1);
        }
        return driver;
    }

    private TypeDBCredential createTypeDBCredential(CLIOptions options) {
        TypeDBCredential credential;
        if (options.tlsEnabled()) {
            String optRootCa = options.tlsRootCA();
            if (optRootCa != null) {
                credential = new TypeDBCredential(options.username(), options.password(), Paths.get(optRootCa));
            } else {
                credential = new TypeDBCredential(options.username(), options.password(), true);
            }
        } else
            credential = new TypeDBCredential(options.username(), options.password(), false);
        return credential;
    }

    private boolean runUserList(TypeDBDriver driver, boolean isEnterprise) {
        try {
            if (!isEnterprise) {
                printer.error("The command 'user list' is only available in TypeDB Enterprise.");
                return false;
            }
            if (driver.users().all().size() > 0) {
                driver.users().all().forEach(user -> {
                    Optional<Long> expirySeconds = user.passwordExpirySeconds();
                    if (expirySeconds.isPresent()) {
                        printer.info(user.username() + " (expiry within: " + (Duration.ofSeconds(expirySeconds.get()).toHours() + 1) + " hours)");
                    } else {
                        printer.info(user.username());
                    }
                });
            } else printer.info("No users are present on the server.");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserCreate(TypeDBDriver driver, boolean isEnterprise, String username, String password) {
        try {
            if (!isEnterprise) {
                printer.error("The command 'user create' is only available in TypeDB Enterprise.");
                return false;
            }
            driver.users().create(username, password);
            printer.info("User '" + username + "' created");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserPasswordUpdate(TypeDBDriver driver, boolean isEnterprise, String username, String passwordOld, String passwordNew) {
        try {
            if (!isEnterprise) {
                printer.error("The command 'user password-update' is only available in TypeDB Enterprise.");
                return false;
            }
            driver.users().get(username).passwordUpdate(passwordOld, passwordNew);
            printer.info("Updated password for user '" + username + "'.");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserPasswordSet(TypeDBDriver driver, boolean isEnterprise, String username, String password) {
        try {
            if (!isEnterprise) {
                printer.error("The command 'user password-set' is only available in TypeDB Enterprise.");
                return false;
            }
            if (driver.users().contains(username)) {
                driver.users().passwordSet(username, password);
                printer.info("Set password for user '" + username + "'");
                return true;
            } else {
                printer.info("No such user '" + username + "'");
                return false;
            }
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runUserDelete(TypeDBDriver driver, boolean isEnterprise, String username) {
        try {
            if (!isEnterprise) {
                printer.error("The command 'user delete' is only available in TypeDB Enterprise.");
                return false;
            }
            driver.users().delete(username);
            printer.info("User '" + username + "' deleted");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseList(TypeDBDriver driver) {
        try {
            if (driver.databases().all().size() > 0)
                driver.databases().all().forEach(database -> printer.info(database.name()));
            else printer.info("No databases are present on the server.");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseCreate(TypeDBDriver driver, String database) {
        try {
            driver.databases().create(database);
            printer.info("Database '" + database + "' created");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseSchema(TypeDBDriver driver, String database) {
        try {
            String schema = driver.databases().get(database).schema();
            printer.info(schema);
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseDelete(TypeDBDriver driver, String database) {
        try {
            driver.databases().get(database).delete();
            printer.info("Database '" + database + "' deleted");
            return true;
        } catch (TypeDBDriverException e) {
            printer.error(e.getMessage());
            return false;
        }
    }

    private boolean runDatabaseReplicas(TypeDBDriver driver, boolean isEnterprise, String database) {
        try {
            if (!isEnterprise) {
                printer.error("The command 'database replicas' is only available in TypeDB Enterprise.");
                return false;
            }
            for (Database.Replica replica : driver.databases().get(database).replicas()) {
                printer.databaseReplica(replica);
            }
            return true;
        } catch (TypeDBDriverException e) {
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

    private RunQueriesResult runSource(TypeDBTransaction tx, String file, boolean printAnswers) {
        try {
            String queryString = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
            if (printAnswers) return runQueriesPrintAnswers(tx, queryString);
            else return runQueries(tx, queryString);
        } catch (IOException e) {
            printer.error("Failed to open file '" + file + "'");
            return RunQueriesResult.error();
        }
    }

    private RunQueriesResult runQueries(TypeDBTransaction tx, String queryString) {
        Optional<List<TypeQLQuery>> queries = parseQueries(queryString);
        if (queries.isEmpty()) return RunQueriesResult.error();
        queries.get().forEach(query -> runQuery(tx, query));
        boolean hasChanges = queries.get().stream().anyMatch(query -> query.type() == TypeQLArg.QueryType.WRITE);
        return new RunQueriesResult(true, hasChanges);
    }

    private RunQueriesResult runQueriesPrintAnswers(TypeDBTransaction tx, String queryString) {
        Optional<List<TypeQLQuery>> queries = parseQueries(queryString);
        if (queries.isEmpty()) return RunQueriesResult.error();
        queries.get().forEach(query -> runQueryPrintAnswers(tx, query));
        boolean hasChanges = queries.get().stream().anyMatch(query -> query.type() == TypeQLArg.QueryType.WRITE);
        return new RunQueriesResult(true, hasChanges);
    }

    @SuppressWarnings("CheckReturnValue")
    private void runQuery(TypeDBTransaction tx, TypeQLQuery query) {
        if (query instanceof TypeQLDefine) {
            tx.query().define(query.asDefine()).resolve();
            printer.info("Concepts have been defined");
        } else if (query instanceof TypeQLUndefine) {
            tx.query().undefine(query.asUndefine()).resolve();
            printer.info("Concepts have been undefined");
        } else if (query instanceof TypeQLInsert) {
            Optional<ConceptMap> ignore = tx.query().insert(query.asInsert()).findFirst();
        } else if (query instanceof TypeQLDelete) {
            tx.query().delete(query.asDelete()).resolve();
        } else if (query instanceof TypeQLUpdate) {
            Optional<ConceptMap> ignore = tx.query().update(query.asUpdate()).findFirst();
        } else if (query instanceof TypeQLGet) {
            Optional<ConceptMap> ignore = tx.query().get(query.asGet()).findFirst();
        } else if (query instanceof TypeQLGet.Aggregate) {
            Optional<Value> ignore = tx.query().get(query.asGetAggregate()).resolve();
        } else if (query instanceof TypeQLGet.Group) {
            Optional<ConceptMapGroup> ignore = tx.query().get(query.asGetGroup()).findFirst();
        } else if (query instanceof TypeQLGet.Group.Aggregate) {
            Optional<ValueGroup> ignore = tx.query().get(query.asGetGroupAggregate()).findFirst();
        } else if (query instanceof TypeQLFetch) {
            Optional<JSON> ignore = tx.query().fetch(query.asFetch()).findFirst();
        } else {
            throw new TypeDBConsoleException("Query is of unrecognized type: " + query);
        }
    }

    private void runQueryPrintAnswers(TypeDBTransaction tx, TypeQLQuery query) {
        if (query instanceof TypeQLDefine) {
            tx.query().define(query.asDefine()).resolve();
            printer.info("Concepts have been defined");
            hasUncommittedChanges = true;
        } else if (query instanceof TypeQLUndefine) {
            tx.query().undefine(query.asUndefine()).resolve();
            printer.info("Concepts have been undefined");
            hasUncommittedChanges = true;
        } else if (query instanceof TypeQLInsert) {
            Stream<ConceptMap> result = tx.query().insert(query.asInsert());
            AtomicBoolean changed = new AtomicBoolean(false);
            printCancellableResult(result, x -> {
                changed.set(true);
                printer.conceptMap(x, tx);
            });
            if (changed.get()) hasUncommittedChanges = true;
        } else if (query instanceof TypeQLDelete) {
            Optional<TypeQLQuery.MatchClause> match = query.asDelete().match();
            assert match.isPresent();
            long limitedCount = tx.query().get(match.get().get()).limit(20).count();
            if (limitedCount > 0) {
                tx.query().delete(query.asDelete()).resolve();
                if (limitedCount == 20) printer.info("Deleted from 20+ matched answers");
                else printer.info("Deleted from " + limitedCount + " matched answer(s)");
                hasUncommittedChanges = true;
            } else {
                printer.info("No concepts were matched");
            }
        } else if (query instanceof TypeQLUpdate) {
            Stream<ConceptMap> result = tx.query().update(query.asUpdate());
            AtomicBoolean changed = new AtomicBoolean(false);
            printCancellableResult(result, x -> {
                changed.set(true);
                printer.conceptMap(x, tx);
            });
            if (changed.get()) hasUncommittedChanges = true;
        } else if (query instanceof TypeQLGet) {
            Stream<ConceptMap> result = tx.query().get(query.asGet());
            printCancellableResult(result, x -> printer.conceptMap(x, tx));
        } else if (query instanceof TypeQLGet.Aggregate) {
            printer.value(tx.query().get(query.asGetAggregate()).resolve().orElse(null));
        } else if (query instanceof TypeQLGet.Group) {
            Stream<ConceptMapGroup> result = tx.query().get(query.asGetGroup());
            printCancellableResult(result, x -> printer.conceptMapGroup(x, tx));
        } else if (query instanceof TypeQLGet.Group.Aggregate) {
            Stream<ValueGroup> result = tx.query().get(query.asGetGroupAggregate());
            printCancellableResult(result, x -> printer.valueGroup(x, tx));
        } else if (query instanceof TypeQLFetch) {
            Stream<JSON> result = tx.query().fetch(query.asFetch());
            printCancellableResult(result, printer::json);
        } else {
            throw new TypeDBConsoleException("Query is of unrecognized type: " + query);
        }
    }

    Optional<List<TypeQLQuery>> parseQueries(String queryString) {
        try {
            return Optional.of(TypeQL.parseQueries(queryString).collect(toList()));
        } catch (TypeQLException e) {
            printer.error(e.getMessage());
            return Optional.empty();
        }
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
            printer.info("");
            printer.info("answers: " + counter[0] + ", total duration: " + Duration.between(start, end).toMillis() + " ms");
            printer.info("");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            throw (TypeDBDriverException) e.getCause();
        } catch (CancellationException e) {
            Instant end = Instant.now();
            printer.info("");
            printer.info("answers: " + counter[0] + ", total duration: " + Duration.between(start, end).toMillis() + " ms");
            printer.info("The query has been cancelled. It may take some time for the cancellation to finish on the server side.");
            printer.info("");
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
                names = {"--enterprise"},
                description = "TypeDB Enterprise address to which Console will connect to"
        )
        private @Nullable
        String enterprise;

        @CommandLine.Option(names = {"--username"}, description = "Username")
        private @Nullable
        String username;

        @CommandLine.Option(
                names = {"--password"},
                description = "Password",
                prompt = "Password: ",
                interactive = true,
                arity = "0..1"
        )
        private @Nullable
        String password;

        @CommandLine.Option(
                names = {"--tls-enabled"},
                description = "Whether to connect to TypeDB Enterprise with TLS encryption"
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
            if (server != null && enterprise != null) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Either '--server' or '--enterprise' must be provided, but not both.");
            } else {
                if (enterprise != null) validateEnterpriseOptions();
                else validateServerOptions();
            }
        }

        private void validateServerOptions() {
            if (username != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--username' should only be supplied with '--enterprise'");
            if (password != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--password' should only be supplied with '--enterprise'");
            if (tlsEnabled)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-enabled' is only valid with '--enterprise'");
            if (tlsRootCA != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' is only valid with '--enterprise'");
        }

        private void validateEnterpriseOptions() {
            if (username == null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--username' must be supplied with '--enterprise'");
            if (password == null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--password' must be supplied with '--enterprise'");
            if (!tlsEnabled && tlsRootCA != null)
                throw new CommandLine.ParameterException(spec.commandLine(), "'--tls-root-ca' should only be supplied when '--tls-enabled' is set to 'true'");
        }

        @Nullable
        private String server() {
            return server;
        }

        @Nullable
        private String enterprise() {
            return enterprise;
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
