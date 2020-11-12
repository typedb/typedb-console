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
import grakn.client.rpc.GraknClient;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Arrays;

public class GraknConsole {
    private static final String COPYRIGHT = "\n" +
            "Welcome to Grakn Console. You are now in Grakn Wonderland!\n" +
            "Copyright (C) 2020 Grakn Labs\n";
    private static final String HELP_MENU = "HELP_MENU\n";
    private static final String TX_HELP_MENU = "TX_HELP_MENU\n";

    public static void main(String[] args) throws IOException {
        CommandLineOptions options = parseCommandLine(args);
        System.out.println(COPYRIGHT);
        Terminal terminal = TerminalBuilder.builder().signalHandler(Terminal.SignalHandler.SIG_IGN).build();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

        try (Grakn.Client client = new GraknClient(options.address())) {
            while (true) {
                try {
                    String prompt = "> ";
                    String line = reader.readLine(prompt);
                    String[] words = Arrays.stream(line.split("\\s+")).map(String::trim).filter(x -> !x.equals("")).toArray(String[]::new);
                    if (words.length > 0) {
                        if (words.length == 1 && words[0].equals("exit")) {
                            break;
                        } else if (words.length == 1 && words[0].equals("help")) {
                            System.out.println(HELP_MENU);
                        } else if (words.length == 2 && words[0].equals("database") && words[1].equals("list")) {
                            try {
                                client.databases().all().forEach(System.out::println);
                            } catch (GraknClientException e) {
                                System.err.println(error(e.getMessage()));
                            }
                        } else if (words.length == 3 && words[0].equals("database") && words[1].equals("create")) {
                            try {
                                client.databases().create(words[2]);
                                System.out.println("Database '" + words[2] + "' created");
                            } catch (GraknClientException e) {
                                System.err.println(error(e.getMessage()));
                            }
                        } else if (words.length == 3 && words[0].equals("database") && words[1].equals("delete")) {
                            try {
                                client.databases().delete(words[2]);
                                System.out.println("Database '" + words[2] + "' deleted");
                            } catch (GraknClientException e) {
                                System.err.println(error(e.getMessage()));
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
                                            String txPrompt = database + "|" + sessionType + "|" + transactionType + "> ";
                                            String txLine = reader.readLine(txPrompt);
                                            String[] txWords = Arrays.stream(txLine.split("\\s+")).map(String::trim).filter(x -> !x.equals("")).toArray(String[]::new);
                                            if (txWords.length == 1 && txWords[0].equals("exit")) {
                                                break;
                                            } else if (txWords.length == 1 && txWords[0].equals("help")) {
                                                System.out.println(TX_HELP_MENU);
                                            } else if (txWords.length == 1 && txWords[0].equals("commit")) {
                                                tx.commit();
                                                System.out.println("Transaction changes committed");
                                                break;
                                            } else if (txWords.length == 1 && txWords[0].equals("rollback")) {
                                                tx.rollback();
                                                System.out.println("Rolled back to the beginning of the transaction");
                                            } else if (txWords.length == 1 && txWords[0].equals("close")) {
                                                tx.close();
                                                System.out.println("Transaction closed without committing changes");
                                                break;
                                            } else {
                                                System.err.println(error("Unrecognised command: '" + txLine + "'\n"));
                                                System.out.println(TX_HELP_MENU);
                                            }
                                        } catch (UserInterruptException | EndOfFileException e) {
                                            System.out.println("Use command 'exit' to exit to console");
                                        }
                                    }
                                }
                            }
                        } else {
                            System.err.println(error("Unrecognised command: '" + line + "'\n"));
                            System.out.println(HELP_MENU);
                        }
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    System.out.println("Use command 'exit' to exit to console");
                }
            }
        }
    }

    private static String error(String s) {
        return new AttributedString(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).toAnsi();
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
