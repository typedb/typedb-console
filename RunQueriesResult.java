/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.console;

public class RunQueriesResult {
    private final boolean success;

    public RunQueriesResult(boolean success) {
        this.success = success;
    }

    public static RunQueriesResult error() {
        return new RunQueriesResult(false);
    }

    public boolean success() {
        return success;
    }
}
