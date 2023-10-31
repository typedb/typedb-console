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

import com.vaticle.typedb.driver.api.TypeDBTransaction;
import com.vaticle.typedb.driver.api.answer.ConceptMap;
import com.vaticle.typedb.driver.api.answer.ConceptMapGroup;
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
import com.vaticle.typeql.lang.common.TypeQLToken;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static com.vaticle.typedb.console.common.exception.ErrorMessage.Internal.ILLEGAL_STATE;
import static com.vaticle.typeql.lang.common.TypeQLToken.Constraint.ISA;
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

    public void conceptMap(ConceptMap conceptMap, TypeDBTransaction tx) {
        out.println(conceptMapDisplayString(conceptMap, tx));
    }

    public void conceptMapGroup(ConceptMapGroup answer, TypeDBTransaction tx) {
        out.println(conceptDisplayString(answer.owner(), tx) + " => {");
        for (ConceptMap conceptMap : answer.conceptMaps().collect(Collectors.toList())) {
            out.println(indent(conceptMapDisplayString(conceptMap, tx)));
        }
        out.println("}");
    }

    public void json(JSON json) {
        out.println(JSONDisplayString(json));
    }

    public void value(Value answer) {
        out.println(stringifyNumericValue(answer));
    }

    public void valueGroup(ValueGroup answer, TypeDBTransaction tx) {
        out.println(conceptDisplayString(answer.owner(), tx) + " => " + stringifyNumericValue(answer.value()));
    }

    private static String stringifyNumericValue(Value value) {
        if (value == null) return "NaN";
        else return value.toString();
    }

    public void databaseReplica(Database.Replica replica) {
        String s = "{ " +
                colorJsonKey("address: ") + replica.address() + ";" +
                colorJsonKey(" role: ") + (replica.isPrimary() ? "primary" : "secondary") + ";" +
                colorJsonKey(" term: ") + replica.term() +
                " }";
        out.println(s);
    }

    private String conceptMapDisplayString(ConceptMap conceptMap, TypeDBTransaction tx) {
        String content = conceptMap.variables()
                .map(key -> {
                    Concept value = conceptMap.get(key);
                    if (value.isValue()) {
                        return TypeQLToken.Char.QUESTION_MARK + key + " = " + conceptDisplayString(value.asValue(), tx) + ";";
                    } else {
                        return TypeQLToken.Char.$ + key + " " + conceptDisplayString(value, tx) + ";";
                    }
                }).collect(joining("\n"));
        StringBuilder sb = new StringBuilder("{");
        if (content.lines().count() > 1) sb.append("\n").append(indent(content)).append("\n");
        else sb.append(" ").append(content).append(" ");
        sb.append("}");
        return sb.toString();
    }

    private static String JSONDisplayString(JSON json) {
        if (json.isBoolean()) return Boolean.toString(json.asBoolean());
        else if (json.isNumber()) return Double.toString(json.asNumber());
        else if (json.isString()) return '"' + json.asString() + '"';
        else if (json.isArray()) {
            String content = json.asArray().stream().map(Printer::JSONDisplayString).collect(joining(",\n"));

            StringBuilder sb = new StringBuilder("[");
            if (content.lines().count() > 1) sb.append("\n").append(indent(content)).append("\n");
            else sb.append(" ").append(content).append(" ");
            sb.append("]");

            return sb.toString();
        } else if (json.isObject()) {
            return JSONObjectDisplayString(json.asObject());
        } else throw new TypeDBConsoleException(ILLEGAL_STATE);
    }

    private static String JSONObjectDisplayString(Map<String, JSON> jsonObject) {
        boolean singleLine = jsonObject.containsKey("root") || jsonObject.containsKey("value");

        List<String> orderedKeys = jsonObject.keySet().stream().sorted((s1, s2) -> {
            if (s1.equals("type")) return 1; // type always comes last
            else if (s2.equals("type")) return -1;
            else return s1.compareTo(s2);
        }).collect(Collectors.toList());

        String content = orderedKeys.stream().map(key -> {
            StringBuilder sb = new StringBuilder("\"").append(key).append("\":");
            var valueString = JSONDisplayString(jsonObject.get(key));
            sb.append(" ").append(valueString);
            return sb.toString();
        }).collect(joining(singleLine ? ", " : ",\n"));

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
        return com.vaticle.typeql.lang.common.util.Strings.valueToString(rawValue);
    }

    private String isaDisplayString(Thing thing) {
        return colorKeyword(ISA.toString()) + " " + colorType(thing.getType().getLabel().scopedName());
    }

    private String relationDisplayString(Relation relation, TypeDBTransaction tx) {
        StringBuilder sb = new StringBuilder();
        List<String> rolePlayerStrings = new ArrayList<>();
        Map<? extends RoleType, ? extends List<? extends Thing>> rolePlayers = relation.getPlayers(tx);
        for (Map.Entry<? extends RoleType, ? extends List<? extends Thing>> rolePlayer : rolePlayers.entrySet()) {
            RoleType role = rolePlayer.getKey();
            List<? extends Thing> things = rolePlayer.getValue();
            for (Thing thing : things) {
                String rolePlayerString = colorType(role.getLabel().name()) + ": " + colorKeyword(TypeQLToken.Constraint.IID.toString()) + " " + thing.getIID();
                rolePlayerStrings.add(rolePlayerString);
            }
        }
        sb.append("(").append(String.join(", ", rolePlayerStrings)).append(")");
        return sb.toString();
    }

    private String iidDisplayString(Thing thing) {
        return colorKeyword(TypeQLToken.Constraint.IID.toString()) + " " + thing.getIID();
    }

    private String typeDisplayString(Type type, TypeDBTransaction tx) {
        StringBuilder sb = new StringBuilder();

        sb.append(colorKeyword(TypeQLToken.Constraint.TYPE.toString()))
                .append(" ")
                .append(colorType(type.getLabel().toString()));

        if (!type.isRoot()) {
            Type superType = type.getSupertype(tx).resolve();
            sb.append(" ")
                    .append(colorKeyword(TypeQLToken.Constraint.SUB.toString()))
                    .append(" ")
                    .append(colorType(superType.getLabel().scopedName()));
        }
        return sb.toString();
    }

    private String attributeDisplayString(Attribute attribute) {
        return com.vaticle.typeql.lang.common.util.Strings.valueToString(attribute.getValue());
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
