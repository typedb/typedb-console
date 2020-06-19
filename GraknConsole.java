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

import grakn.client.GraknClient;
import grakn.client.exception.GraknClientException;
import grakn.console.exception.ErrorMessage;
import grakn.console.exception.GraknConsoleException;
import io.grpc.Status;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Grakn Console is a Command Line Application to interact with the Grakn Core database
 */
@Command(
        name = "console",
        mixinStandardHelpOptions = true,
        version = {
                Version.VERSION
        }
)
public class GraknConsole implements Callable<Integer> {

    public static final String DEFAULT_KEYSPACE = "grakn";

    private final PrintStream printOut;
    private final PrintStream printErr;

    @Option(names = {"-n", "--no-infer"}, negatable = true, description = "Do not perform inference on results.")
    Boolean infer = true;

    @Option(names = {"-r", "--address"}, description = "Grakn Server address.")
    String serverAddress = GraknClient.DEFAULT_URI;

    @Option(names = {"-k", "--keyspace"}, description = "Keyspace of the graph.")
    String keyspace = DEFAULT_KEYSPACE;

    @Option(names = {"-f", "--file"}, description = "Path to a Graql file.")
    Path[] file;

    public GraknConsole(PrintStream printOut, PrintStream printErr) {
        this.printOut = printOut;
        this.printErr = printErr;
    }

    public Integer call() throws InterruptedException, IOException {
        // Start a Console Session to load some Graql file(s)
        if (file != null) {
            try (ConsoleSession consoleSession = new ConsoleSession(serverAddress, keyspace, infer, printOut, printErr)) {
                //Intercept Ctrl+C and gracefully terminate connection with server
                Runtime.getRuntime().addShutdownHook(new Thread(consoleSession::close, "grakn-console-shutdown"));
                for (Path filePath : file) consoleSession.load(filePath);
                return 0;
            }
        }
        // Start a live Console Session for the user to interact with Grakn
        else {
            try (ConsoleSession consoleSession = new ConsoleSession(serverAddress, keyspace, infer, printOut, printErr)) {
                //Intercept Ctrl+C and gracefully terminate connection with server
                Runtime.getRuntime().addShutdownHook(new Thread(consoleSession::close, "grakn-console-shutdown"));
                consoleSession.run();
                return 0;
            }
        }
    }

    public static int execute(String[] args, PrintStream out, PrintStream err) {
        return new CommandLine(new GraknConsole(out, err))
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .setExecutionExceptionHandler((e, commandLine, parseResult) -> {
                    if (e instanceof GraknConsoleException) {
                        err.println(e.getMessage());
                        err.println("Cause: " + e.getCause().getClass().getName());
                        err.println(e.getCause().getMessage());
                        return 1;
                    }
                    if (e instanceof GraknClientException) {
                        // TODO: don't do if-checks. Use different catch-clauses by class
                        if (e.getMessage().startsWith(Status.Code.UNAVAILABLE.name())) {
                            err.println(ErrorMessage.COULD_NOT_CONNECT.getMessage());
                        } else {
                            e.printStackTrace(err);
                        }
                        return 1;
                    }
                    e.printStackTrace(err);
                    return 1;
                }).execute(args);
    }

    /**
     * Invocation from bash script './grakn console'
     */
    public static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }
}
