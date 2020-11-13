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
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraknConsole {
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
            "transaction open <db> schema|data read|write    Start a transaction to database <db> with schema or data session, with read or write transaction\n";
    private static final String TX_HELP_MENU =
            "\n" +
            "exit             Exit console\n" +
            "clear            Clear console screen\n" +
            "help             Print this help menu\n" +
            "commit           Commit the transaction changes and close\n" +
            "rollback         Rollback the transaction to the beginning state\n" +
            "close            Close the transaction without committing changes\n" +
            "<query>          Run graql queries\n" +
            "source <file>    Run graql queries in file\n";

    public static void main(String[] args) {
        CommandLineOptions options = parseCommandLine(args);
        Printer printer = new Printer();
        printer.info(COPYRIGHT);
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder().signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        } catch (IOException e) {
            printer.error("Failed to initialise terminal: " + e.getMessage());
            System.exit(1);
        }
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        try (Grakn.Client client = new GraknClient(options.address())) {
            repl: while (true) {
                try {
                    String prompt = "> ";
                    String line = reader.readLine(prompt);
                    String[] words = splitLine(line);
                    if (words.length > 0) {
                        if (words.length == 1 && words[0].equals("exit")) {
                            break;
                        } else if (words.length == 1 && words[0].equals("help")) {
                            printer.info(HELP_MENU);
                        } else if (words.length == 1 && words[0].equals("clear")) {
                            terminal.puts(InfoCmp.Capability.clear_screen);
                        } else if (words.length == 2 && words[0].equals("database") && words[1].equals("list")) {
                            try {
                                client.databases().all().forEach(database -> printer.info(database));
                            } catch (GraknClientException e) {
                                printer.error(e.getMessage());
                            }
                        } else if (words.length == 3 && words[0].equals("database") && words[1].equals("create")) {
                            try {
                                String database = words[2];
                                client.databases().create(database);
                                printer.info("Database '" + database + "' created");
                            } catch (GraknClientException e) {
                                printer.error(e.getMessage());
                            }
                        } else if (words.length == 3 && words[0].equals("database") && words[1].equals("delete")) {
                            try {
                                String database = words[2];
                                client.databases().delete(database);
                                printer.info("Database '" + database + "' deleted");
                            } catch (GraknClientException e) {
                                printer.error(e.getMessage());
                            }
                        } else if (words.length == 5 && words[0].equals("transaction") && words[1].equals("open") &&
                                (words[3].equals("schema") || words[3].equals("data") && (words[4].equals("read") || words[4].equals("write")))) {
                            String database = words[2];
                            String sessionType = words[3];
                            String transactionType = words[4];
                            try (Grakn.Session session = client.session(database, sessionType.equals("schema") ? Grakn.Session.Type.SCHEMA : Grakn.Session.Type.DATA)) {
                                try (Grakn.Transaction tx = session.transaction(transactionType.equals("read") ? Grakn.Transaction.Type.READ : Grakn.Transaction.Type.WRITE)) {
                                    while (true) {
                                        try {
                                            String txPrompt = database + "::" + sessionType + "::" + transactionType + "> ";
                                            String txLine = reader.readLine(txPrompt);
                                            String[] txWords = splitLine(txLine);
                                            if (txWords.length > 0) {
                                                if (txWords.length == 1 && txWords[0].equals("exit")) {
                                                    break repl;
                                                } else if (txWords.length == 1 && txWords[0].equals("clear")) {
                                                    terminal.puts(InfoCmp.Capability.clear_screen);
                                                } else if (txWords.length == 1 && txWords[0].equals("help")) {
                                                    printer.info(TX_HELP_MENU);
                                                } else if (txWords.length == 1 && txWords[0].equals("commit")) {
                                                    tx.commit();
                                                    printer.info("Transaction changes committed");
                                                    break;
                                                } else if (txWords.length == 1 && txWords[0].equals("rollback")) {
                                                    tx.rollback();
                                                    printer.info("Rolled back to the beginning of the transaction");
                                                } else if (txWords.length == 1 && txWords[0].equals("close")) {
                                                    tx.close();
                                                    printer.info("Transaction closed without committing changes");
                                                    break;
                                                } else if (txWords.length == 2 && txWords[0].equals("source")) {
                                                    String file = txWords[1];
                                                    String queryString;
                                                    try {
                                                        queryString = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.US_ASCII);
                                                    } catch (IOException e) {
                                                        printer.error("Failed to open file '" + file + "'");
                                                        continue;
                                                    }
                                                    List<GraqlQuery> queries;
                                                    try {
                                                        queries = Graql.parseQueries(queryString).collect(Collectors.toList());
                                                    } catch (GraqlException e) {
                                                        printer.error(e.getMessage());
                                                        return;
                                                    }
                                                    runQuery(tx, queries, printer);
                                                } else {
                                                    List<String> queryLines = new ArrayList<>();
                                                    queryLines.add(txLine);
                                                    while (true) {
                                                        String queryPrompt = String.join("", Collections.nCopies(txPrompt.length(), " "));
                                                        String queryLine;
                                                        try {
                                                            queryLine = reader.readLine(queryPrompt);
                                                        } catch (UserInterruptException | EndOfFileException e) {
                                                            break;
                                                        }
                                                        if (queryLine.trim().isEmpty()) {
                                                            break;
                                                        } else {
                                                            queryLines.add(queryLine);
                                                        }
                                                    }
                                                    String queryString = String.join("\n", queryLines);
                                                    GraqlQuery query;
                                                    try {
                                                        query = Graql.parseQuery(queryString);
                                                    } catch (GraqlException e) {
                                                        printer.error(e.getMessage());
                                                        return;
                                                    }
                                                    runQuery(tx, Collections.singletonList(query), printer);
                                                }
                                            }
                                        } catch (UserInterruptException | EndOfFileException e) {
                                            printer.info("Use command 'exit' to exit the console");
                                        }
                                    }
                                } catch (GraknClientException e) {
                                    printer.error(e.getMessage());
                                }
                            } catch (GraknClientException e) {
                                printer.error(e.getMessage());
                            }
                        } else {
                            printer.error("Unrecognised command: '" + line + "'");
                        }
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    printer.info("Use command 'exit' to exit the console");
                }
            }
        } catch (GraknClientException e) {
            printer.error(e.getMessage());
        }
    }

    private static void runQuery(Grakn.Transaction tx, List<GraqlQuery> queries, Printer printer) {
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

    private static String[] splitLine(String line) {
        return Arrays.stream(line.split("\\s+")).map(String::trim).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }

    private static class Printer {
        private final PrintStream out;
        private final PrintStream err;

        public Printer() {
            this.out = System.out;
            this.err = System.err;
        }

        public void info(String s) {
            out.println(s);
        }

        public void error(String s) {
            err.println(new AttributedString(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).toAnsi());
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

    @Command(name = "grakn console", mixinStandardHelpOptions = true, version = {grakn.console.Version.VERSION})
    public static class CommandLineOptions {
        @Option(names = {"--server-address"},
                defaultValue = GraknClient.DEFAULT_URI,
                description = "Server address to which the console will connect to")
        private String address;

        public String address() {
            return address;
        }
    }
}
