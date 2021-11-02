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
