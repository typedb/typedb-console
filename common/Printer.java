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

import grakn.client.GraknClient;
import grakn.client.concept.Concept;
import grakn.client.concept.answer.ConceptMap;
import grakn.client.concept.answer.ConceptMapGroup;
import grakn.client.concept.answer.Numeric;
import grakn.client.concept.answer.NumericGroup;
import grakn.client.concept.thing.Attribute;
import grakn.client.concept.thing.Relation;
import grakn.client.concept.thing.Thing;
import grakn.client.concept.type.RoleType;
import grakn.client.concept.type.Type;
import graql.lang.common.GraqlToken;
import graql.lang.pattern.variable.Reference;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public void conceptMap(ConceptMap conceptMap, GraknClient.Transaction tx) {
        out.println(conceptMapDisplayString(conceptMap, tx));
    }

    public void conceptMapGroup(ConceptMapGroup answer, GraknClient.Transaction tx) {
        for (ConceptMap conceptMap : answer.conceptMaps()) {
            out.println(conceptDisplayString(answer.owner(), tx) + " => " + conceptMapDisplayString(conceptMap, tx));
        }
    }

    public void numeric(Numeric answer) {
        out.println(answer.asNumber());
    }

    public void numericGroup(NumericGroup answer, GraknClient.Transaction tx) {
        out.println(conceptDisplayString(answer.owner(), tx) + " => " + answer.numeric().asNumber());
    }

    public void databaseReplica(GraknClient.Database.Replica replica) {
        String s = "{ " +
                colorJsonKey("address: ") + replica.address().client() + ";" +
                colorJsonKey(" role: ") + (replica.isPrimary() ? "primary" : "secondary") + ";" +
                colorJsonKey(" term: ") + replica.term() +
                " }";
        out.println(s);
    }

    private String conceptMapDisplayString(ConceptMap conceptMap, GraknClient.Transaction tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (Map.Entry<String, Concept> entry : conceptMap.map().entrySet()) {
            Reference.Name variable = Reference.name(entry.getKey());
            Concept concept = entry.getValue();
            sb.append(variable.syntax());
            sb.append(" ");
            sb.append(conceptDisplayString(concept, tx));
            sb.append("; ");
        }
        sb.append("}");
        return sb.toString();
    }

    private String conceptDisplayString(Concept concept, GraknClient.Transaction tx) {
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
            sb.append(" ").append(isaDisplayString(concept.asThing(), tx));
        }
        return sb.toString();
    }

    private String isaDisplayString(Thing thing, GraknClient.Transaction tx) {
        StringBuilder sb = new StringBuilder();
        Type type = thing.asRemote(tx).getType();
        sb.append(colorKeyword(GraqlToken.Constraint.ISA.toString())).append(" ").append(colorType(type.getLabel()));
        return sb.toString();
    }

    private String relationDisplayString(Relation relation, GraknClient.Transaction tx) {
        StringBuilder sb = new StringBuilder();
        List<String> rolePlayerStrings = new ArrayList<>();
        Map<? extends RoleType, ? extends List<? extends Thing>> rolePlayers = relation.asRemote(tx).getPlayersByRoleType();
        for (Map.Entry<? extends RoleType, ? extends List<? extends Thing>> rolePlayer : rolePlayers.entrySet()) {
            RoleType role = rolePlayer.getKey();
            List<? extends Thing> things = rolePlayer.getValue();
            for (Thing thing : things) {
                String rolePlayerString = colorType(role.getLabel()) + ": " + colorKeyword(GraqlToken.Constraint.IID.toString()) + " " + thing.getIID();
                rolePlayerStrings.add(rolePlayerString);
            }
        }
        sb.append("(").append(String.join(", ", rolePlayerStrings)).append(")");
        return sb.toString();
    }

    private String iidDisplayString(Thing thing) {
        StringBuilder sb = new StringBuilder();
        sb.append(colorKeyword(GraqlToken.Constraint.IID.toString()))
                .append(" ")
                .append(thing.getIID());
        return sb.toString();
    }

    private String typeDisplayString(Type type, GraknClient.Transaction tx) {
        StringBuilder sb = new StringBuilder();

        String label = type.isRoleType() ? type.asRoleType().getScopedLabel() : type.asThingType().getLabel();

        sb.append(colorKeyword(GraqlToken.Constraint.TYPE.toString()))
                .append(" ")
                .append(colorType(label));

        Type superType = type.asRemote(tx).getSupertype();
        if (superType != null) {
            sb.append(" ")
                    .append(colorKeyword(GraqlToken.Constraint.SUB.toString()))
                    .append(" ")
                    .append(colorType(superType.getLabel()));
        }
        return sb.toString();
    }

    private String attributeDisplayString(Attribute<?> attribute) {
        return graql.lang.common.util.Strings.valueToString(attribute.getValue());
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
