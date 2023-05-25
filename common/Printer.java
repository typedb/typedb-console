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

import com.vaticle.typedb.client.api.TypeDBTransaction;
import com.vaticle.typedb.client.api.answer.ConceptMap;
import com.vaticle.typedb.client.api.answer.ConceptMapGroup;
import com.vaticle.typedb.client.api.answer.Numeric;
import com.vaticle.typedb.client.api.answer.NumericGroup;
import com.vaticle.typedb.client.api.concept.Concept;
import com.vaticle.typedb.client.api.concept.thing.Attribute;
import com.vaticle.typedb.client.api.concept.thing.Relation;
import com.vaticle.typedb.client.api.concept.thing.Thing;
import com.vaticle.typedb.client.api.concept.type.RoleType;
import com.vaticle.typedb.client.api.concept.type.Type;
import com.vaticle.typedb.client.api.concept.value.Value;
import com.vaticle.typedb.client.api.database.Database;
import com.vaticle.typeql.lang.common.TypeQLToken;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
        for (ConceptMap conceptMap : answer.conceptMaps()) {
            out.println(indent(conceptMapDisplayString(conceptMap, tx)));
        }
        out.println("}");
    }

    public void numeric(Numeric answer) {
        out.println(answer.asNumber());
    }

    public void numericGroup(NumericGroup answer, TypeDBTransaction tx) {
        out.println(conceptDisplayString(answer.owner(), tx) + " => " + answer.numeric().asNumber());
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
        Comparator<Map.Entry<String, Concept>> comparator = Comparator.comparing(e -> e.getValue().isValue());
        comparator = comparator.thenComparing(e -> e.getKey().toLowerCase());
        String content = conceptMap.map().entrySet().stream().sorted(comparator)
                .map(e -> {
                    if (e.getValue().isValue()) {
                        return TypeQLToken.Char.QUESTION_MARK + e.getKey() + " = " + valueDisplayString(e.getValue().asValue()) + ";";
                    } else {
                        return TypeQLToken.Char.$ + e.getKey() + " " + conceptDisplayString(e.getValue(), tx) + ";";
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
        StringBuilder sb = new StringBuilder();
        if (concept instanceof Attribute<?>) {
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

    private String valueDisplayString(Value<?> value) {
        return com.vaticle.typeql.lang.common.util.Strings.valueToString(value.getValue());
    }

    private String isaDisplayString(Thing thing) {
        StringBuilder sb = new StringBuilder();
        sb.append(colorKeyword(ISA.toString())).append(" ").append(colorType(thing.getType().getLabel().scopedName()));
        return sb.toString();
    }

    private String relationDisplayString(Relation relation, TypeDBTransaction tx) {
        StringBuilder sb = new StringBuilder();
        List<String> rolePlayerStrings = new ArrayList<>();
        Map<? extends RoleType, ? extends List<? extends Thing>> rolePlayers = relation.asRemote(tx).getPlayersByRoleType();
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
        StringBuilder sb = new StringBuilder();
        sb.append(colorKeyword(TypeQLToken.Constraint.IID.toString()))
                .append(" ")
                .append(thing.getIID());
        return sb.toString();
    }

    private String typeDisplayString(Type type, TypeDBTransaction tx) {
        StringBuilder sb = new StringBuilder();

        sb.append(colorKeyword(TypeQLToken.Constraint.TYPE.toString()))
                .append(" ")
                .append(colorType(type.getLabel().toString()));

        Type superType = type.asRemote(tx).getSupertype();
        if (superType != null) {
            sb.append(" ")
                    .append(colorKeyword(TypeQLToken.Constraint.SUB.toString()))
                    .append(" ")
                    .append(colorType(superType.getLabel().scopedName()));
        }
        return sb.toString();
    }

    private String attributeDisplayString(Attribute<?> attribute) {
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
