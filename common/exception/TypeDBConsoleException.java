/*
 * Copyright (C) 2021 Vaticle
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
