/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.vaticle.typedb.console.common;

import com.vaticle.typedb.driver.api.TypeDBTransaction;
import com.vaticle.typedb.driver.api.answer.JSON;
import com.vaticle.typedb.driver.api.answer.ValueGroup;
import com.vaticle.typedb.driver.api.concept.Concept;
import com.vaticle.typedb.driver.api.concept.thing.Attribute;
import com.vaticle.typedb.driver.api.concept.thing.Relation;
import com.vaticle.typedb.driver.api.concept.thing.Thing;
import com.vaticle.typedb.driver.api.concept.type.RoleType;
import com.vaticle.typedb.driver.api.concept.type.Type;
import com.vaticle.typedb.driver.api.concept.value.Value;
import com.vaticle.typedb.driver.api.database.Database;
import com.vaticle.typedb.console.common.exception.TypeDBConsoleException;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static java.util.stream.Collectors.joining;

public class Printer {
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

    public void conceptRow(ConceptRow conceptRow, TypeDBTransaction tx) {
        out.println(conceptRowDisplayString(conceptRow, tx));
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

    public void databaseReplica(Database.Replica replica) {
        String s = "{ " +
                colorJsonKey(" server: ") + replica.server() + ";" +
                colorJsonKey(" role: ") + (replica.isPrimary() ? "primary" : "secondary") + ";" +
                colorJsonKey(" term: ") + replica.term() +
                " }";
        out.println(s);
    }

    private String conceptRowDisplayString(ConceptRow conceptRow, TypeDBTransaction tx) {
        String content = conceptRow.header()
                .map(columnName -> {
                    Concept value = conceptRow.get(columnName);
                    if (value.isValue()) {
                        return "?" + key + " = " + conceptDisplayString(value.asValue(), tx) + ";";
                    } else {
                        return "$" + key + " " + conceptDisplayString(value, tx) + ";";
                    }
                }).collect(joining("\n"));
        StringBuilder sb = new StringBuilder("{");
        if (content.lines().count() > 1) sb.append("\n").append(indent(content)).append("\n");
        else sb.append(" ").append(content).append(" ");
        sb.append("}");
        return sb.toString();
    }

    private static String indent(String string) {
        return Arrays.stream(string.split("\n")).map(s -> "    " + s).collect(joining("\n"));
    }

    private String conceptDisplayString(Concept concept, TypeDBTransaction tx) {
        if (concept.isValue()) return valueDisplayString(concept.asValue());

        StringBuilder sb = new StringBuilder();
        if (concept instanceof Attribute) {
            sb.append(attributeDisplayString(concept.asThing().asAttribute()));
        } else if (concept instanceof Type) {
            sb.append(typeDisplayString(concept.asType(), tx));
        } else {
            sb.append(iidDisplayString(concept.asThing()));
        }
        if (concept instanceof Relation) {
            sb.append(" ").append(relationDisplayString(concept.asThing().asRelation(), tx));
        }
        if (concept instanceof Thing) {
            sb.append(" ").append(isaDisplayString(concept.asThing()));
        }

        return sb.toString();
    }

    private String valueDisplayString(Value value) {
        Object rawValue;
        if (value.isLong()) rawValue = value.asLong();
        else if (value.isDouble()) rawValue = value.asDouble();
        else if (value.isBoolean()) rawValue = value.asBoolean();
        else if (value.isString()) rawValue = value.asString();
        else if (value.isDateTime()) rawValue = value.asDateTime();
        else throw new TypeDBConsoleException(ILLEGAL_CAST);
        return rawValue.toString();
    }

    private String isaDisplayString(Thing thing) {
        return colorKeyword("isa") + " " + colorType(thing.getType().getLabel().scopedName());
    }

    private String relationDisplayString(Relation relation, TypeDBTransaction tx) {
        StringBuilder sb = new StringBuilder();
        List<String> rolePlayerStrings = new ArrayList<>();
        Map<? extends RoleType, ? extends List<? extends Thing>> rolePlayers = relation.getPlayers(tx);
        for (Map.Entry<? extends RoleType, ? extends List<? extends Thing>> rolePlayer : rolePlayers.entrySet()) {
            RoleType role = rolePlayer.getKey();
            List<? extends Thing> things = rolePlayer.getValue();
            for (Thing thing : things) {
                String rolePlayerString = colorType(role.getLabel().name()) + ": " + colorKeyword("IID") + " " + thing.getIID();
                rolePlayerStrings.add(rolePlayerString);
            }
        }
        sb.append("(").append(String.join(", ", rolePlayerStrings)).append(")");
        return sb.toString();
    }

    private String iidDisplayString(Thing thing) {
        return colorKeyword("IID") + " " + thing.getIID();
    }

    private String typeDisplayString(Type type, TypeDBTransaction tx) {
        StringBuilder sb = new StringBuilder();

        sb.append(colorKeyword("type"))
                .append(" ")
                .append(colorType(type.getLabel().toString()));

        if (!type.isRoot()) {
            Type superType = type.getSupertype(tx).resolve();
            sb.append(" ")
                    .append(colorKeyword("sub"))
                    .append(" ")
                    .append(colorType(superType.getLabel().scopedName()));
        }
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
