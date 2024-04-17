/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
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
