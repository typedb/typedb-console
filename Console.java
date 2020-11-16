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
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Console {
    private static final String COPYRIGHT =
            "\n" +
            "Welcome to Grakn Console. You are now in Grakn Wonderland!\n" +
            "Copyright (C) 2020 Grakn Labs\n";
    private static final String HELP_MENU =
            "\n" +
            "exit                                            Exit console\n" +
            "clear                                           Clear console screen\n" +
            "help                                            Print this help menu\n" +
            "database list                                   List the databases on the server\n" +
            "database create <db>                            Create a database with name <db> on the server\n" +
            "database delete <db>                            Delete a database with name <db> on the server\n" +
            "transaction <db> schema|data read|write         Start a transaction to database <db> with schema or data session, with read or write transaction\n";
    private static final String TRANSACTION_HELP_MENU =
            "\n" +
            "exit             Exit console\n" +
            "clear            Clear console screen\n" +
            "help             Print this help menu\n" +
            "commit           Commit the transaction changes and close\n" +
            "rollback         Rollback the transaction to the beginning state\n" +
            "close            Close the transaction without committing changes\n" +
            "<query>          Run graql queries\n" +
            "source <file>    Run graql queries in file\n";

    private final CommandLineOptions options;
    private final LineReader reader;
    private final Printer printer;

    public Console(CommandLineOptions options, LineReader reader, Printer printer) {
        this.options = options;
        this.reader = reader;
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
        while (true) {
            ReplCommand command = ReplCommand.getCommand(reader, printer, "> ");
            if (command instanceof ReplCommand.Exit) {
                System.exit(0);
            } else if (command instanceof ReplCommand.Help) {
                printer.info(HELP_MENU);
            } else if (command instanceof ReplCommand.Clear) {
                reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
            } else if (command instanceof ReplCommand.DatabaseList) {
                try {
                    client.databases().all().forEach(database -> printer.info(database));
                } catch (GraknClientException e) {
                    printer.error(e.getMessage());
                }
            } else if (command instanceof ReplCommand.DatabaseCreate) {
                try {
                    client.databases().create(command.asDatabaseCreate().database());
                    printer.info("Database '" + command.asDatabaseCreate().database() + "' created");
                } catch (GraknClientException e) {
                    printer.error(e.getMessage());
                }
            } else if (command instanceof ReplCommand.DatabaseDelete) {
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
                runTransactionRepl(client, database, sessionType, transactionType);
            }
        }
    }

    private void runTransactionRepl(Grakn.Client client, String database, Grakn.Session.Type sessionType, Grakn.Transaction.Type transactionType) {
        try (Grakn.Session session = client.session(database, sessionType);
             Grakn.Transaction tx = session.transaction(transactionType)) {
            while (true) {
                String prompt = database + "::" + sessionType.name().toLowerCase() + "::" + transactionType.name().toLowerCase() + "> ";
                TransactionReplCommand command = TransactionReplCommand.getCommand(reader, printer, prompt);
                if (command instanceof TransactionReplCommand.Exit) {
                    System.exit(0);
                } else if (command instanceof TransactionReplCommand.Clear) {
                    reader.getTerminal().puts(InfoCmp.Capability.clear_screen);
                } else if (command instanceof TransactionReplCommand.Help) {
                    printer.info(TRANSACTION_HELP_MENU);
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
}
