/*
 * Copyright (C) 2021 Grakn Labs
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

import grakn.common.collection.Pair;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.util.Arrays;
import java.util.List;

public class Utils {
    public static String buildHelpMenu(List<Pair<String, String>> menu) {
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

    public static String[] getTokens(LineReader reader, String prompt) throws InterruptedException {
        String[] words = null;
        while (words == null) {
            try {
                String line = reader.readLine(prompt);
                words = Utils.splitLineByWhitespace(line);
                if (words.length == 0) words = null;
            } catch (UserInterruptException e) {
                if (reader.getBuffer().toString().isEmpty()) {
                    throw new InterruptedException();
                }
            } catch (EndOfFileException e) {
                throw new InterruptedException();
            }
        }
        return words;
    }

    private static String[] splitLineByWhitespace(String line) {
        return Arrays.stream(line.split("\\s+")).map(String::trim).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }
}
