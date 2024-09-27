/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.console.tool.runner;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.typedb.console.tool.runner.Util.getConsoleArchiveFile;
import static com.typedb.console.tool.runner.Util.unarchive;

public class TypeDBConsoleRunner {

    protected final Path distribution;
    protected ProcessExecutor executor;

    public TypeDBConsoleRunner() throws InterruptedException, TimeoutException, IOException {
        System.out.println("Constructing " + name() + " runner");
        System.out.println("Extracting " + name() + " distribution archive.");
        distribution = unarchive(getConsoleArchiveFile());
        System.out.println(name() + " distribution archive extracted.");
        executor = new ProcessExecutor()
                .directory(distribution.toFile())
                .environment("JAVA_HOME", System.getProperty("java.home"))
                .redirectOutput(System.out)
                .redirectError(System.err)
                .readOutput(true)
                .destroyOnExit();
        System.out.println(name() + " runner constructed");
    }

    public int run(String... options) {
        try {
            StartedProcess consoleProcess = executor.command(command(options)).start();
            return consoleProcess.getProcess().waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> command(String... options) {
        List<String> cmd = new ArrayList<>();
        cmd.add("console");
        cmd.addAll(Arrays.asList(options));
        return Util.typeDBCommand(cmd);
    }

    private String name() {
        return "TypeDB Console";
    }
}
