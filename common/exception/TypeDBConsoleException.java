/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console.common.exception;

import com.vaticle.typedb.common.exception.ErrorMessage;

public class TypeDBConsoleException extends RuntimeException {

    public TypeDBConsoleException(ErrorMessage error) {
        super(error.toString());
        assert !getMessage().contains("%s");
    }

    private TypeDBConsoleException(ErrorMessage error, Object... parameters) {
        super(error.message(parameters));
        assert !getMessage().contains("%s");
    }

    public TypeDBConsoleException(String errorMessage) {
        super(errorMessage);
    }

    public TypeDBConsoleException(IllegalArgumentException e) {
        super(e);
    }

    public static TypeDBConsoleException of(ErrorMessage errorMessage, Object... parameters) {
        return new TypeDBConsoleException(errorMessage, parameters);
    }
}
