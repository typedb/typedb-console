/*
 * Copyright (C) 2020 Grakn Labs
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

import grakn.client.Grakn;
import grakn.client.concept.Concept;
import grakn.client.concept.answer.ConceptMap;
import grakn.client.concept.thing.Attribute;
import grakn.client.concept.thing.Relation;
import grakn.client.concept.thing.Thing;
import grakn.client.concept.type.RoleType;
import grakn.client.concept.type.Type;
import graql.lang.common.GraqlToken;
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

    public void conceptMap(ConceptMap conceptMap, Grakn.Transaction tx) {
        out.println(getConceptMapDisplayString(conceptMap, tx));
    }

    private String getConceptMapDisplayString(ConceptMap conceptMap, Grakn.Transaction tx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (Map.Entry<String, Concept> entry: conceptMap.map().entrySet()) {
            String variable = entry.getKey();
            Concept concept = entry.getValue();

            sb.append(variable);
            sb.append(" ");
            sb.append(getConceptDisplayString(concept, tx));
        }
        sb.append(" }");
        return sb.toString();
    }

    private String getConceptDisplayString(Concept concept, Grakn.Transaction tx) {
        StringBuilder sb = new StringBuilder();
        if (concept instanceof Attribute<?>) {
            String value = concept.asThing().asAttribute().toString();
            sb.append(value);
        } else if (concept instanceof Type) {
            sb.append(colorKeyword(GraqlToken.Constraint.TYPE.toString()))
                .append(" ")
                .append(colorType(concept.asType().getLabel()));
            Type superType = concept.asType().asRemote(tx).getSupertype();
            if (superType != null) {
                sb.append(" ")
                    .append(colorKeyword(GraqlToken.Constraint.SUB.toString()))
                    .append(" ")
                    .append(colorType(superType.getLabel()));
            }
        } else {
            sb.append(colorKeyword(GraqlToken.Constraint.IID.toString()))
                .append(" ")
                .append(concept.asThing().getIID());
        }

        if (concept instanceof Relation) {
            List<String> rolePlayerStrings = new ArrayList<>();
            Map<? extends RoleType, ? extends List<? extends Thing>> rolePlayers = concept.asThing().asRelation().asRemote(tx).getPlayersByRoleType();
            for (Map.Entry<? extends RoleType, ? extends List<? extends Thing>> rolePlayer : rolePlayers.entrySet()) {
                RoleType role = rolePlayer.getKey();
                List<? extends Thing> things = rolePlayer.getValue();
                for (Thing thing : things) {
                    String rolePlayerString = colorType(role.getLabel()) + ": " + colorKeyword(GraqlToken.Constraint.IID.toString()) + " " + thing.getIID();
                    rolePlayerStrings.add(rolePlayerString);
                }
            }
            sb.append(" (").append(String.join(", ", rolePlayerStrings)).append(")");
        }

        if (concept instanceof Thing) {
            Type type = concept.asThing().asRemote(tx).getType();
            sb.append(" ").append(colorKeyword(GraqlToken.Constraint.ISA.toString())).append(" ").append(colorType(type.getLabel()));
        }

        return sb.toString();
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
}
