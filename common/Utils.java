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

package com.vaticle.typedb.console.common;

import com.vaticle.typedb.common.collection.Pair;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Utils {

    public static String createHelpMenu(List<Pair<String, String>> menu) {
        if (menu.isEmpty()) return "\n";
        int maxHelpCommandLength = menu.stream().map(x -> x.first().length()).max(Integer::compare).get();
        int spacingLength = 4;
        StringBuilder sb = new StringBuilder("\n");
        for (Pair<String, String> item : menu) {
            sb.append(item.first());
            for (int i = 0; i < maxHelpCommandLength + spacingLength - item.first().length(); i++) {
                sb.append(' ');
            }
            sb.append(item.second());
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String readLineWithoutHistory(LineReader reader, String prompt) {
        String continuationPrompt = getContinuationPrompt(prompt);
        reader.variable(LineReader.SECONDARY_PROMPT_PATTERN, continuationPrompt);
        try {
            reader.variable(LineReader.DISABLE_HISTORY, true);
            return reader.readLine(prompt);
        } finally {
            reader.variable(LineReader.DISABLE_HISTORY, false);
        }
    }

    public static String readNonEmptyLine(LineReader reader, String prompt) throws InterruptedException {
        String line = null;
        while (line == null) {
            try {
                line = readLineWithoutHistory(reader, prompt);
                if (line.trim().isEmpty()) line = null;
            } catch (UserInterruptException e) {
                if (reader.getBuffer().toString().isEmpty()) {
                    throw new InterruptedException();
                }
            } catch (EndOfFileException e) {
                throw new InterruptedException();
            }
        }
        return line;
    }

    public static String readPassword(LineReader passwordReader, String prompt) {
        return passwordReader.readLine(prompt, '*');
    }

    public static String getContinuationPrompt(String prompt) {
        return String.join("", Collections.nCopies(prompt.length(), " "));
    }

    public static String[] splitLineByWhitespace(String line) {
        return Arrays.stream(line.split("\\s+")).map(String::trim).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }
}
