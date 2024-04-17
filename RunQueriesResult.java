/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console;

public class RunQueriesResult {
    private final boolean success;
    private final boolean hasChanges;

    public RunQueriesResult(boolean success, boolean hasChanges) {
        this.success = success;
        this.hasChanges = hasChanges;
    }

    public static RunQueriesResult error() {
        return new RunQueriesResult(false, false);
    }

    public boolean success() {
        return success;
    }

    public boolean hasChanges() {
        return hasChanges;
    }
}
