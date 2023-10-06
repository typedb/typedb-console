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

package com.vaticle.typedb.console.test.assembly;

import com.vaticle.typedb.common.test.console.TypeDBConsoleRunner;
import com.vaticle.typedb.common.test.core.TypeDBCoreRunner;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;

public class AssemblyTest {

    @Test
    public void test_console_command() throws IOException, InterruptedException, TimeoutException {
        TypeDBConsoleRunner consoleRunner = new TypeDBConsoleRunner();
        TypeDBCoreRunner coreRunner = new TypeDBCoreRunner();
        try {
            coreRunner.start();
            int status = consoleRunner.run("--command", "database create assembly-test-db");
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
