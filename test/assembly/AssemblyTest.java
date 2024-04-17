/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console.test.assembly;

import com.vaticle.typedb.console.tool.runner.TypeDBConsoleRunner;
import com.vaticle.typedb.core.tool.runner.TypeDBCoreRunner;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;

public class AssemblyTest {

    @Test
    public void test_console_command() throws IOException, InterruptedException, TimeoutException {
        TypeDBConsoleRunner consoleRunner = new TypeDBConsoleRunner();
        Map<String, String> coreOptions = new HashMap<>();
        coreOptions.put("--diagnostics.reporting.errors", "false");
        coreOptions.put("--diagnostics.monitoring.enable", "false");
        TypeDBCoreRunner coreRunner = new TypeDBCoreRunner(coreOptions);
        try {
            coreRunner.start();
            int status = consoleRunner.run("--core", coreRunner.address(), "--command", "database create assembly-test-db");
            if (status != 0) {
               fail("Console command returned non-zero exit status: " + status);
            }
        } catch (Exception e) {
            fail("Exception occurred while starting server and console runner." +  e);
        } finally {
            coreRunner.stop();
            coreRunner.deleteFiles();
        }
    }
}
