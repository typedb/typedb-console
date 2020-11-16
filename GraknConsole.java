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
import grakn.client.common.exception.GraknClientException;
import grakn.client.concept.answer.ConceptMap;
import grakn.client.rpc.GraknClient;
import graql.lang.Graql;
import graql.lang.common.exception.GraqlException;
import graql.lang.query.*;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraknConsole {
    private static final String COPYRIGHT =
            "\n" +
            "Welcome to Grakn Console. You are now in Grakn Wonderland!\n" +
            "Copyright (C) 2020 Grakn Labs\n";
    private final CommandLineOptions options;
    private final Terminal terminal;
    private final Printer printer;

    public GraknConsole(CommandLineOptions options, Terminal terminal, Printer printer) {
        this.options = options;
        this.terminal = terminal;
        this.printer = printer;
    }

    public void run() {
        printer.info(COPYRIGHT);
        try (Grakn.Client client = new GraknClient(options.address())) {
            runRepl(client);
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
        }
    }

    private void runRepl(Grakn.Client client) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), "~/.grakn-console-history").toAbsolutePath())
                .build();
        while (true) {
            ReplCommand command = ReplCommand.getCommand(reader, printer, "> ");
            if (command instanceof ReplCommand.Exit) {
                break;
            } else if (command instanceof ReplCommand.Help) {
                printer.info(ReplCommand.getHelpMenu());
            } else if (command instanceof ReplCommand.Clear) {
                reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
            } else if (command instanceof ReplCommand.Database.List) {
                try {
                    client.databases().all().forEach(database -> printer.info(database));
                } catch (GraknClientException e) {
                    printer.error(e.getMessage());
                }
            } else if (command instanceof ReplCommand.Database.Create) {
                try {
                    client.databases().create(command.asDatabaseCreate().database());
                    printer.info("Database '" + command.asDatabaseCreate().database() + "' created");
                } catch (GraknClientException e) {
                    printer.error(e.getMessage());
                }
            } else if (command instanceof ReplCommand.Database.Delete) {
                try {
                    client.databases().delete(command.asDatabaseDelete().database());
                    printer.info("Database '" + command.asDatabaseDelete().database() + "' deleted");
                } catch (GraknClientException e) {
                    printer.error(e.getMessage());
                }
            } else if (command instanceof ReplCommand.Transaction) {
                String database = command.asTransaction().database();
                Grakn.Session.Type sessionType = command.asTransaction().sessionType();
                Grakn.Transaction.Type transactionType = command.asTransaction().transactionType();
                boolean shouldExit = runTransactionRepl(client, database, sessionType, transactionType);
                if (shouldExit) break;
            }
        }
    }

    private boolean runTransactionRepl(Grakn.Client client, String database, Grakn.Session.Type sessionType, Grakn.Transaction.Type transactionType) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".grakn-console-transaction-history").toAbsolutePath())
                .build();
        String prompt = database + "::" + sessionType.name().toLowerCase() + "::" + transactionType.name().toLowerCase() + "> ";
        try (Grakn.Session session = client.session(database, sessionType);
             Grakn.Transaction tx = session.transaction(transactionType)) {
            while (true) {
                TransactionReplCommand command = TransactionReplCommand.getCommand(reader, printer, prompt);
                if (command instanceof TransactionReplCommand.Exit) {
                    return true;
                } else if (command instanceof TransactionReplCommand.Clear) {
                    reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                } else if (command instanceof TransactionReplCommand.Help) {
                    printer.info(TransactionReplCommand.getHelpMenu());
                } else if (command instanceof TransactionReplCommand.Commit) {
                    tx.commit();
                    printer.info("Transaction changes committed");
                    break;
                } else if (command instanceof TransactionReplCommand.Rollback) {
                    tx.rollback();
                    printer.info("Rolled back to the beginning of the transaction");
                } else if (command instanceof TransactionReplCommand.Close) {
                    tx.close();
                    printer.info("Transaction closed without committing changes");
                    break;
                } else if (command instanceof TransactionReplCommand.Source) {
                    String queryString;
                    try {
                        queryString = new String(Files.readAllBytes(Paths.get(command.asSource().file())), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        printer.error("Failed to open file '" + command.asSource().file() + "'");
                        continue;
                    }
                    runQuery(tx, queryString, printer);
                } else if (command instanceof TransactionReplCommand.Query) {
                    runQuery(tx, command.asQuery().query(), printer);
                }
            }
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
        }
        return false;
    }

    private static void runQuery(Grakn.Transaction tx, String queryString, Printer printer) {
        List<GraqlQuery> queries;
        try {
            queries = Graql.parseQueries(queryString).collect(Collectors.toList());
        } catch (GraqlException e) {
            printer.error(e.getMessage());
            return;
        }
        for (GraqlQuery query : queries) {
            if (query instanceof GraqlDefine) {
                tx.query().define(query.asDefine()).get();
                printer.info("Concepts have been defined");
            } else if (query instanceof GraqlUndefine) {
                tx.query().undefine(query.asUndefine()).get();
                printer.info("Concepts have been undefined");
            } else if (query instanceof GraqlInsert) {
                Stream<ConceptMap> result = tx.query().insert(query.asInsert()).get();
                result.forEach(cm -> printer.info(cm + " has been inserted"));
            } else if (query instanceof GraqlDelete) {
                tx.query().delete(query.asDelete()).get();
                printer.info("Concepts have been deleted");
            } else if (query instanceof GraqlMatch) {
                throw new GraknClientException("Match query is not yet supported");
            } else if (query instanceof GraqlCompute) {
                throw new GraknClientException("Compute query is not yet supported");
            }
        }
    }

    public static void main(String[] args) {
        CommandLineOptions options = parseCommandLine(args);
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder().signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException e) {
            System.err.println("Failed to initialise terminal: " + e.getMessage());
            System.exit(1);
        }
        Printer printer = new Printer(System.out, System.err);
        GraknConsole console = new GraknConsole(options, terminal, printer);
        console.run();
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
}
