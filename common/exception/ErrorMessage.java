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

package com.vaticle.typedb.console.common.exception;

public abstract class ErrorMessage extends com.vaticle.typedb.common.exception.ErrorMessage {

    private ErrorMessage(String codePrefix, int codeNumber, String messagePrefix, String messageBody) {
        super(codePrefix, codeNumber, messagePrefix, messageBody);
    }

    public static class TransactionRepl extends ErrorMessage {

        public static final TransactionRepl INVALID_OPTIONAL_ARG =
                new TransactionRepl(1, "'%s' does not have an optional argument '%s'.");
        public static final TransactionRepl INVALID_EXIT_ARGS =
                new TransactionRepl(2, "'exit' expects %s space-separated arguments, received %s.");
        public static final TransactionRepl INVALID_HELP_ARGS =
                new TransactionRepl(3, "'help' expects %s space-separated arguments, received %s.");
        public static final TransactionRepl INVALID_CLEAR_ARGS =
                new TransactionRepl(4, "'clear' expects %s space-separated arguments, received %s.");
        public static final TransactionRepl INVALID_COMMIT_ARGS =
                new TransactionRepl(5, "'commit' expects %s space-separated arguments, received %s.");
        public static final TransactionRepl INVALID_ROLLBACK_ARGS =
                new TransactionRepl(6, "'rollback' expects %s space-separated arguments, received %s.");
        public static final TransactionRepl INVALID_CLOSE_ARGS =
                new TransactionRepl(7, "'close' expects %s space-separated arguments, received %s.");
        public static final TransactionRepl INVALID_SOURCE_ARGS =
                new TransactionRepl(8, "'source' expects %s mandatory arguments and up to %s optional " +
                        "arguments, received %s arguments.");

        private static final String codePrefix = "TXN";
        private static final String messagePrefix = "Invalid Transaction command";

        TransactionRepl(int number, String message) {
            super(codePrefix, number, messagePrefix, message);
        }

    }

    public static class Internal extends ErrorMessage {
        public static final Internal ILLEGAL_CAST =
                new Internal(2, "Illegal casting operation from '%s' to '%s'.");

        private static final String codePrefix = "INT";
        private static final String messagePrefix = "Invalid Internal State";

        Internal(int number, String message) {
            super(codePrefix, number, messagePrefix, message);
        }
    }

    public static class Console extends ErrorMessage {
        public static final Console INCOMPATIBLE_JAVA_RUNTIME =
                new Console(1, "Incompatible Java runtime version: '%s'. Please use Java 11 or above.");
        public static final Console UNABLE_TO_READ_PASSWORD_INTERACTIVELY =
                new Console(1, "Unable to read password interactively in non-interactive mode.");

        private static final String codePrefix = "CON";
        private static final String messagePrefix = "Invalid Console Operation";

        Console(int number, String message) {
            super(codePrefix, number, messagePrefix, message);
        }
    }

}
