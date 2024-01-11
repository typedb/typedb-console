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
 *
 */

package com.vaticle.typedb.console.test.runner;

import org.zeroturnaround.exec.ProcessExecutor;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;


public class Util {

    private static final String TAR_GZ = ".tar.gz";
    private static final String ZIP = ".zip";

    public static File getConsoleArchiveFile() {
        String[] args = System.getProperty("sun.java.command").split(" ");
        Optional<CLIOptions> maybeOptions = CLIOptions.parseCLIOptions(args);
        if (!maybeOptions.isPresent()) {
            throw new IllegalArgumentException("No archives were passed as arguments");
        }
        CLIOptions options = maybeOptions.get();
        return new File(options.getConsoleArchive());
    }

    public static Path unarchive(File archive) throws IOException, TimeoutException, InterruptedException {
        Path runnerDir = Files.createTempDirectory("typedb");
        ProcessExecutor executor = createProcessExecutor(Paths.get(".").toAbsolutePath());
        if (archive.toString().endsWith(TAR_GZ)) {
            executor.command("tar", "-xf", archive.toString(),
                    "-C", runnerDir.toString()).execute();
        } else if (archive.toString().endsWith(ZIP)) {
            executor.command("unzip", "-q", archive.toString(),
                    "-d", runnerDir.toString()).execute();
        } else {
            throw new IllegalStateException(String.format("The distribution archive format must be either %s or %s", TAR_GZ, ZIP));
        }
        // The archive extracts to a folder inside runnerDir named
        // typedb-console-{platform}-{version}. We know it's the only folder, so we can retrieve it using Files.list.
        return Files.list(runnerDir).findFirst().get().toAbsolutePath();
    }

    public static List<String> typeDBCommand(List<String> cmd) {
        List<String> command = new ArrayList<>();
        List<String> result;
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            result = Collections.singletonList("typedb");
        } else {
            result = Arrays.asList("cmd.exe", "/c", "typedb.bat");
        }
        command.addAll(result);
        command.addAll(cmd);
        return command;
    }

    public static ProcessExecutor createProcessExecutor(Path directory) {
        return new ProcessExecutor()
                .directory(directory.toFile())
                .redirectOutput(System.out)
                .redirectError(System.err)
                .readOutput(true)
                .environment("JAVA_HOME", System.getProperty("java.home"))
                .destroyOnExit();
    }

    @CommandLine.Command(name = "java")
    private static class CLIOptions {
        @CommandLine.Parameters String mainClass;
        @CommandLine.Option(
                names = {"--console"},
                description = "Location of the archive containing a console artifact."
        )
        private String consoleArchive;

        public String getConsoleArchive() {
            return consoleArchive;
        }

        public static Optional<CLIOptions> parseCLIOptions(String[] args) {
            CommandLine commandLine = new CommandLine(new CLIOptions()).setUnmatchedArgumentsAllowed(true);
            try {
                CommandLine.ParseResult result = commandLine.parseArgs(args);
                return Optional.of(result.asCommandLineList().get(0).getCommand());
            } catch (CommandLine.ParameterException ex) {
                commandLine.getErr().println(ex.getMessage());
                if (!CommandLine.UnmatchedArgumentException.printSuggestions(ex, commandLine.getErr())) {
                    ex.getCommandLine().usage(commandLine.getErr());
                }
                return Optional.empty();
            }
        }
    }
}
