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

import grakn.client.rpc.GraknClient;
import picocli.CommandLine;

@CommandLine.Command(name = "grakn console", mixinStandardHelpOptions = true, version = {grakn.console.Version.VERSION})
public class CommandLineOptions {
    @CommandLine.Option(names = {"--server"},
            defaultValue = GraknClient.DEFAULT_URI,
            description = "Server address to which the console will connect to")
    private String address;

    public String address() {
        return address;
    }
}
