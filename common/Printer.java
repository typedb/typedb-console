/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console.common;

import com.vaticle.typedb.console.common.exception.TypeDBConsoleException;
import com.vaticle.typedb.driver.api.TypeDBQueryType;
import com.vaticle.typedb.driver.api.TypeDBTransaction;
import com.vaticle.typedb.driver.api.answer.ConceptRow;
import com.vaticle.typedb.driver.api.answer.JSON;
import com.vaticle.typedb.driver.api.answer.ValueGroup;
import com.vaticle.typedb.driver.api.concept.Concept;
import com.vaticle.typedb.driver.api.concept.thing.Attribute;
import com.vaticle.typedb.driver.api.concept.thing.Entity;
import com.vaticle.typedb.driver.api.concept.thing.Relation;
import com.vaticle.typedb.driver.api.concept.thing.Thing;
import com.vaticle.typedb.driver.api.concept.type.Type;
import com.vaticle.typedb.driver.api.concept.value.Value;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static java.util.stream.Collectors.joining;

public class Printer {
    private static final int TABLE_DASHES = 4;

    public static final String QUERY_SUCCESS = "Success";
    public static final String QUERY_COMPILATION_SUCCESS = "Completed validation and compilation...";
    public static final String QUERY_WRITE_SUCCESS = "Finished writes";
    public static final String QUERY_STREAMING_ANSWERS = "Streaming answers...";
    public static final String TOTAL_ANSWERS = "Total answers: ";

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

    public void conceptRow(ConceptRow conceptRow, TypeDBTransaction tx, boolean first) {
        List<String> columnNames = conceptRow.columnNames().collect(Collectors.toList());
        int columnsWidth = columnNames.stream().map(String::length).max(Comparator.comparingInt(Integer::intValue)).orElse(0);
        if (first) {
            out.println(conceptRowDisplayStringHeader(conceptRow.getQueryType(), columnsWidth));
        }
        out.println(conceptRowDisplayString(conceptRow, columnNames, columnsWidth, tx));
    }

    public void json(JSON json) {
        out.println(json.toString());
    }

    public void value(Value answer) {
        out.println(stringifyNumericValue(answer));
    }

    public void valueGroup(ValueGroup answer, TypeDBTransaction tx) {
        out.println(conceptDisplayString(answer.owner(), tx) + " => " + stringifyNumericValue(answer.value().orElse(null)));
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

    private String conceptRowDisplayStringHeader(TypeDBQueryType queryType, int columnsWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append(QUERY_COMPILATION_SUCCESS);
        sb.append("\n");

        if (queryType.isWrite()) {
            sb.append(QUERY_WRITE_SUCCESS);
            sb.append(". ");
        }

        assert !queryType.isSchema(); // expected to return another type of answer
        sb.append(QUERY_STREAMING_ANSWERS);
        sb.append("\n\n");

        if (columnsWidth != 0) {
            sb.append(lineDashSeparator(columnsWidth));
        }

        return sb.toString();
    }

    private String conceptRowDisplayString(ConceptRow conceptRow, List<String> columnNames, int columnsWidth, TypeDBTransaction tx) {
        String content = columnNames
                .stream()
                .map(columnName -> {
                    Concept concept = conceptRow.get(columnName);
                    return columnName + " ".repeat(columnsWidth - columnName.length() + 1) + "| " + conceptDisplayString(concept.isValue() ? concept.asValue() : concept, tx);
                }).collect(joining("\n"));

        StringBuilder sb = new StringBuilder(indent(content));
        sb.append("\n");
        sb.append(lineDashSeparator(columnsWidth));
        return sb.toString();
    }

    private static String indent(String string) {
        return Arrays.stream(string.split("\n")).map(s -> "    " + s).collect(joining("\n"));
    }

    private static String lineDashSeparator(int additionalDashesNum) {
        return indent("-".repeat(TABLE_DASHES + additionalDashesNum));
    }

    private String conceptDisplayString(Concept concept, TypeDBTransaction tx) {
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

        if (concept.isThing()) {
            sb.append(" ").append(isaDisplayString(concept.asThing()));
        }

        return sb.toString();
    }

    private String valueDisplayString(Value value) {
        Object rawValue;
        if (value.isLong()) rawValue = value.asLong();
        else if (value.isDouble()) rawValue = value.asDouble();
        else if (value.isDecimal()) rawValue = value.asDecimal();
        else if (value.isBoolean()) rawValue = value.asBoolean();
        else if (value.isString()) rawValue = value.asString();
        else if (value.isDate()) rawValue = value.asDate();
        else if (value.isDatetime()) rawValue = value.asDatetime();
        else if (value.isDatetimeTZ()) rawValue = value.asDatetimeTZ();
        else if (value.isDuration()) rawValue = value.asDuration();
        else if (value.isStruct()) rawValue = "Structs are not supported in console now";
        else throw new TypeDBConsoleException(ILLEGAL_CAST);
        return rawValue.toString();
    }

    private String isaDisplayString(Thing thing) {
        return colorKeyword("isa") + " " + colorType(thing.getType().getLabel().scopedName());
    }

    private String entityDisplayKeyString(Entity entity) {
        return colorKeyword("iid") + " " + entity.getIID();
    }

    private String relationDisplayKeyString(Relation relation) {
        return colorKeyword("iid") + " " + relation.getIID();
    }

    private String typeDisplayString(Type type, TypeDBTransaction tx) {
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
