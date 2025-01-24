/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.console.common;

import com.typedb.console.common.exception.TypeDBConsoleException;
import com.typedb.driver.api.QueryType;
import com.typedb.driver.api.Transaction;
import com.typedb.driver.api.answer.ConceptRow;
import com.typedb.driver.api.answer.JSON;
import com.typedb.driver.api.concept.Concept;
import com.typedb.driver.api.concept.instance.Attribute;
import com.typedb.driver.api.concept.instance.Entity;
import com.typedb.driver.api.concept.instance.Instance;
import com.typedb.driver.api.concept.instance.Relation;
import com.typedb.driver.api.concept.type.Type;
import com.typedb.driver.api.concept.value.Value;
import com.typedb.driver.common.exception.TypeDBDriverException;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static java.util.stream.Collectors.joining;

public class Printer {
    private static final int TABLE_DASHES = 7;

    public static final String QUERY_SUCCESS = "Success";
    public static final String QUERY_COMPILATION_SUCCESS = "Finished validation and compilation...";
    public static final String QUERY_WRITE_SUCCESS = "Finished writes";
    public static final String QUERY_STREAMING_ROWS = "Streaming answers...";
    public static final String QUERY_STREAMING_DOCUMENTS = "Streaming documents...";
    public static final String QUERY_NO_COLUMNS = "No columns to show";
    public static final String TOTAL_ANSWERS = "Total answers: ";
    private static final String TABLE_INDENT = "   ";
    private static final String CONTENT_INDENT = "    ";

    private final PrintStream out;
    private final PrintStream err;

    public Printer(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public void info(String s) {
        out.println(s);
    }

    public void error(String s) {
        err.println(colorError(s));
    }

    public void conceptRow(ConceptRow conceptRow, QueryType queryType, Transaction tx, boolean first) {
        List<String> columnNames = conceptRow.columnNames().collect(Collectors.toList());

        int columnsWidth = columnNames.stream().map(String::length).max(Comparator.comparingInt(Integer::intValue)).orElse(0);
        if (first) {
            out.println(conceptRowDisplayStringHeader(queryType, columnsWidth));
        }

        if (!columnNames.isEmpty()) {
            out.println(conceptRowDisplayString(conceptRow, columnNames, columnsWidth, tx));
        }
    }

    public void conceptDocument(JSON conceptDocument, QueryType queryType, boolean first) {
        if (first) {
            out.println(conceptDocumentDisplayHeader(queryType));
        }
        out.println(conceptDocumentDisplay(conceptDocument));
    }

    public void value(Value answer) {
        out.println(stringifyNumericValue(answer));
    }

    private static String stringifyNumericValue(Value value) {
        if (value == null) return "NaN";
        else return value.toString();
    }

//    public void databaseReplica(Database.Replica replica) {
//        String s = "{ " +
//                colorJsonKey(" server: ") + replica.server() + ";" +
//                colorJsonKey(" role: ") + (replica.isPrimary() ? "primary" : "secondary") + ";" +
//                colorJsonKey(" term: ") + replica.term() +
//                " }";
//        out.println(s);
//    }

    private String conceptRowDisplayStringHeader(QueryType queryType, int columnsWidth) {
        StringBuilder sb = new StringBuilder();

        appendWriteSuccessString(queryType, sb);
        assert !queryType.isSchema(); // expected to return another type of answer
        sb.append(QUERY_STREAMING_ROWS);
        sb.append("\n\n");

        if (columnsWidth == 0) {
            sb.append(QUERY_NO_COLUMNS);
        } else {
            sb.append(lineDashSeparator(columnsWidth));
        }

        return sb.toString();
    }

    private String conceptRowDisplayString(ConceptRow conceptRow, List<String> columnNames, int columnsWidth, Transaction tx) {
        String content = columnNames
                .stream()
                .map(columnName -> {
                    StringBuilder sb = new StringBuilder("$");
                    sb.append(columnName);
                    sb.append(" ".repeat(columnsWidth - columnName.length() + 1));
                    sb.append("| ");
                    Concept concept;
                    try {
                        concept = conceptRow.get(columnName);
                        sb.append(conceptDisplayString(concept.isValue() ? concept.asValue() : concept, tx));
                    } catch (TypeDBDriverException e) {
                        // TODO: substitute the "try catch" by an optional processing when implemented
                    }
                    return sb.toString();
                }).filter(string -> !string.isEmpty()).collect(joining("\n"));

        StringBuilder sb = new StringBuilder(indent(CONTENT_INDENT, content));
        sb.append("\n");
        sb.append(lineDashSeparator(columnsWidth));
        return sb.toString();
    }

    private String conceptDocumentDisplayHeader(QueryType queryType) {
        StringBuilder sb = new StringBuilder();
        appendWriteSuccessString(queryType, sb);
        assert !queryType.isSchema(); // expected to return another type of answer
        sb.append(QUERY_STREAMING_DOCUMENTS);
        sb.append("\n");
        return sb.toString();
    }

    private String conceptDocumentDisplay(JSON document) {
        return document.toString();
    }

    private static void appendWriteSuccessString(QueryType queryType, StringBuilder stringBuilder) {
        if (queryType.isWrite()) {
            stringBuilder.append(QUERY_WRITE_SUCCESS);
            stringBuilder.append(". ");
        }
    }

    private static String indent(String indent, String string) {
        return Arrays.stream(string.split("\n")).map(s -> indent + s).collect(joining("\n"));
    }

    private static String lineDashSeparator(int additionalDashesNum) {
        return indent(TABLE_INDENT, "-".repeat(TABLE_DASHES + additionalDashesNum));
    }

    private String conceptDisplayString(Concept concept, Transaction tx) {
        if (concept.isValue()) return valueDisplayString(concept.asValue());

        StringBuilder sb = new StringBuilder();
        if (concept.isType()) {
            sb.append(typeDisplayString(concept.asType(), tx));
        } else if (concept.isAttribute()) {
            sb.append(attributeDisplayString(concept.asAttribute()));
        } else if (concept.isEntity()) {
            sb.append(entityDisplayKeyString(concept.asEntity()));
        } else if (concept.isRelation()) {
            sb.append(relationDisplayKeyString(concept.asRelation()));
        }

        if (concept.isInstance()) {
            sb.append(" ").append(isaDisplayString(concept.asInstance()));
        }

        return sb.toString();
    }

    private String valueDisplayString(Value value) {
        Object rawValue;
        if (value.isInteger()) rawValue = value.getInteger();
        else if (value.isDouble()) rawValue = value.getDouble();
        else if (value.isDecimal()) rawValue = value.getDecimal();
        else if (value.isBoolean()) rawValue = value.getBoolean();
        else if (value.isString()) rawValue = value.getString();
        else if (value.isDate()) rawValue = value.getDate();
        else if (value.isDatetime()) rawValue = value.getDatetime();
        else if (value.isDatetimeTZ()) rawValue = value.getDatetimeTZ();
        else if (value.isDuration()) rawValue = value.getDuration();
        else if (value.isStruct()) rawValue = "Structs are not supported in console now";
        else throw new TypeDBConsoleException(ILLEGAL_CAST);
        return rawValue.toString();
    }

    private String isaDisplayString(Instance instance) {
        return colorKeyword("isa") + " " + colorType(instance.getType().getLabel());
    }

    private String entityDisplayKeyString(Entity entity) {
        return colorKeyword("iid") + " " + entity.getIID();
    }

    private String relationDisplayKeyString(Relation relation) {
        return colorKeyword("iid") + " " + relation.getIID();
    }

    private String typeDisplayString(Type type, Transaction tx) {
        StringBuilder sb = new StringBuilder();

        sb.append(colorKeyword("type"))
                .append(" ")
                .append(colorType(type.getLabel().toString()));

        return sb.toString();
    }

    private String attributeDisplayString(Attribute attribute) {
        return attribute.getValue().toString();
    }

    private String colorKeyword(String s) {
        return new AttributedString(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE)).toAnsi();
    }

    private String colorType(String s) {
        return new AttributedString(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA)).toAnsi();
    }

    private String colorError(String s) {
        return new AttributedString(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)).toAnsi();
    }

    private String colorJsonKey(String s) {
        return new AttributedString(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE)).toAnsi();
    }
}
