/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.console.test.assembly;

import com.typedb.console.tool.runner.TypeDBConsoleRunner;
//import com.typedb.core.tool.runner.TypeDBCoreRunner;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;

public class AssemblyTest {
    static final String DATABASE_NAME = "assembly-test-db";

    @Test
    public void test_console_command() throws IOException, InterruptedException, TimeoutException {
        TypeDBConsoleRunner consoleRunner = new TypeDBConsoleRunner();
//        TypeDBCoreRunner coreRunner = new TypeDBCoreRunner();
        try {
//            coreRunner.start();
            int status = consoleRunner.run("--core", "127.0.0.1:1729", "--command", String.format("database create %s", DATABASE_NAME));
            if (status != 0) {
               fail("Console command returned non-zero exit status: " + status);
            }
        } catch (Exception e) {
            fail("Exception occurred while starting server and console runner." +  e);
//        } finally {
//            coreRunner.stop();
//            coreRunner.deleteFiles();
        }
    }
}
