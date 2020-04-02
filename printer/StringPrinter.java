/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2019 Grakn Labs Ltd
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

package grakn.console.printer;

import grakn.client.GraknClient;
import grakn.client.answer.AnswerGroup;
import grakn.client.answer.ConceptMap;
import grakn.client.answer.ConceptSetMeasure;
import grakn.client.answer.Void;
import grakn.client.concept.Attribute;
import grakn.client.concept.AttributeType;
import grakn.client.concept.Concept;
import grakn.client.concept.ConceptId;
import grakn.client.concept.Label;
import grakn.client.concept.Role;
import grakn.client.concept.SchemaConcept;
import grakn.client.concept.Thing;
import grakn.client.concept.Type;
import grakn.client.concept.remote.RemoteAttribute;
import grakn.client.concept.remote.RemoteRole;
import grakn.client.concept.remote.RemoteSchemaConcept;
import grakn.client.concept.remote.RemoteThing;
import graql.lang.Graql.Token.Char;
import graql.lang.Graql.Token.Property;
import graql.lang.statement.Variable;
import graql.lang.util.StringUtil;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default printer that prints results in Graql syntax
 *
 */
public class StringPrinter extends Printer<StringBuilder> {

    private final AttributeType<?, ?, ?>[] attributeTypes;
    private final boolean colorize;

    StringPrinter(boolean colorize, AttributeType<?, ?, ?>... attributeTypes) {
        this.colorize = colorize;
        this.attributeTypes = attributeTypes;
    }

    /**
     * @param id an ID of a concept
     * @return
     * The id of the concept correctly escaped in graql.
     * If the ID doesn't begin with a number and is only comprised of alphanumeric characters, underscores and dashes,
     * then it will be returned as-is, otherwise it will be quoted and escaped.
     */
    public static String conceptId(ConceptId id) {
        return id.getValue();
    }

    /**
     * @param label a label of a type
     * @return
     * The label of the type correctly escaped in graql.
     * If the label doesn't begin with a number and is only comprised of alphanumeric characters, underscores and dashes,
     * then it will be returned as-is, otherwise it will be quoted and escaped.
     */
    public static String label(Label label) {
        return label.getValue();
    }

    @Override
    protected String complete(StringBuilder output) {
        return output.toString();
    }

    @Override
    protected StringBuilder concept(GraknClient.Transaction tx, Concept<?> concept) {
        StringBuilder output = new StringBuilder();

        // Display values for resources and ids for everything else
        if (concept.isAttribute()) {
            output.append(StringUtil.valueToString(concept.asAttribute().value()));
        } else if (concept.isSchemaConcept()) {
            SchemaConcept<?> ontoConcept = concept.asSchemaConcept();
            output.append(colorKeyword(Property.TYPE.toString()))
                    .append(Char.SPACE)
                    .append(colorType(ontoConcept));

            SchemaConcept<?> superConcept = ontoConcept.asRemote(tx).sup();

            if (superConcept != null) {
                output.append(Char.SPACE)
                        .append(colorKeyword(Property.SUB.toString()))
                        .append(Char.SPACE)
                        .append(colorType(superConcept));
            }
        } else {
            output.append(colorKeyword(Property.ID.toString()))
                    .append(Char.SPACE)
                    .append(conceptId(concept.id()));
        }

        if (concept.isRelation()) {
            List<String> rolePlayerList = new LinkedList<>();
            for (Map.Entry<RemoteRole, Set<RemoteThing<?, ?>>> rolePlayers
                    : concept.asRelation().asRemote(tx).rolePlayersMap().entrySet()) {
                RemoteRole role = rolePlayers.getKey();
                Set<RemoteThing<?, ?>> things = rolePlayers.getValue();

                for (RemoteThing<?, ?> thing : things) {
                    rolePlayerList.add(
                            colorType(role) + Char.COLON + Char.SPACE +
                                    Property.ID + Char.SPACE + conceptId(thing.id()));
                }
            }

            String relationString = rolePlayerList.stream().collect(Collectors.joining(Char.COMMA_SPACE.toString()));
            output.append(Char.SPACE).append(Char.PARAN_OPEN).append(relationString).append(Char.PARAN_CLOSE);
        }

        // Display type of each instance
        if (concept.isThing()) {
            Type<?, ?> type = concept.asThing().type();
            output.append(Char.SPACE)
                    .append(colorKeyword(Property.ISA.toString()))
                    .append(Char.SPACE)
                    .append(colorType(type));
        }

        // Display when and then for rules
        if (concept.isRule()) {
            output.append(Char.SPACE).append(colorKeyword(Property.WHEN.toString())).append(Char.SPACE)
                    .append(Char.CURLY_OPEN).append(Char.SPACE)
                    .append(concept.asRule().asRemote(tx).when())
                    .append(Char.SPACE).append(Char.CURLY_CLOSE);
            output.append(Char.SPACE).append(colorKeyword(Property.THEN.toString())).append(Char.SPACE)
                    .append(Char.CURLY_OPEN).append(Char.SPACE)
                    .append(concept.asRule().asRemote(tx).then())
                    .append(Char.SPACE).append(Char.CURLY_CLOSE);
        }

        // Display any requested resources
        if (concept.isThing() && attributeTypes.length > 0) {
            Stream<RemoteAttribute<?>> attributeStream = ((RemoteThing<?, ?>) concept.asThing().asRemote(tx))
                    .attributes(attributeTypes);
            attributeStream.forEach(resource -> {
                String attributeType = colorType(resource.type());
                String value = StringUtil.valueToString(resource.value());
                output.append(Char.SPACE).append(colorKeyword(Property.HAS.toString())).append(Char.SPACE)
                        .append(attributeType).append(Char.SPACE).append(value);
            });
        }

        return output;
    }

    @Override
    protected StringBuilder bool(boolean bool) {
        StringBuilder builder = new StringBuilder();

        if (bool) {
            return builder.append(ANSI.color("true", ANSI.GREEN));
        } else {
            return builder.append(ANSI.color("false", ANSI.RED));
        }
    }

    @Override
    protected StringBuilder collection(GraknClient.Transaction tx, Collection<?> collection) {
        StringBuilder builder = new StringBuilder();

        builder.append(Char.CURLY_OPEN);
        collection.stream().findFirst().ifPresent(item -> builder.append(build(tx, item)));
        collection.stream().skip(1).forEach(item -> builder.append(Char.COMMA_SPACE).append(build(tx, item)));
        builder.append(Char.CURLY_CLOSE);

        return builder;
    }

    @Override
    protected StringBuilder map(GraknClient.Transaction tx, Map<?, ?> map) {
        return collection(tx, map.entrySet());
    }

    @Override
    protected StringBuilder answerGroup(GraknClient.Transaction tx, AnswerGroup<?> answer) {
        StringBuilder builder = new StringBuilder();
        return builder.append(Char.CURLY_OPEN)
                .append(concept(tx, answer.owner()))
                .append(Char.COLON).append(Char.SPACE)
                .append(build(tx, answer.answers()))
                .append(Char.CURLY_CLOSE);
    }

    @Override
    protected StringBuilder conceptMap(GraknClient.Transaction tx, ConceptMap answer) {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<Variable, Concept<?>> entry : answer.map().entrySet()) {
            Variable name = entry.getKey();
            Concept<?> concept = entry.getValue();
            builder.append(name).append(Char.SPACE)
                    .append(concept(tx, concept)).append(Char.SEMICOLON).append(Char.SPACE);
        }
        return new StringBuilder(Char.CURLY_OPEN + builder.toString().trim() + Char.CURLY_CLOSE);
    }

    @Override
    protected StringBuilder conceptSetMeasure(GraknClient.Transaction tx,ConceptSetMeasure answer) {
        StringBuilder builder = new StringBuilder();
        return builder.append(answer.measurement()).append(Char.COLON).append(Char.SPACE).append(collection(tx, answer.set()));
    }

    @Override
    protected StringBuilder object(GraknClient.Transaction tx, Object object) {
        StringBuilder builder = new StringBuilder();

        if (object instanceof Map.Entry<?, ?>) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) object;

            builder.append(build(tx, entry.getKey()));
            builder.append(Char.COLON).append(Char.SPACE);
            builder.append(build(tx, entry.getValue()));
        } else if (object != null) {
            builder.append(object);
        }

        return builder;
    }

    @Override
    protected StringBuilder voidAnswer(Void answer) {
        StringBuilder builder = new StringBuilder();
        return builder.append(answer.message());
    }


    /**
     * Color-codes the keyword if colorization enabled
     * @param keyword a keyword to color-code using ANSI colors
     * @return the keyword, color-coded
     */
    private String colorKeyword(String keyword) {
        if(colorize) {
            return ANSI.color(keyword, ANSI.BLUE);
        } else {
            return keyword;
        }
    }

    /**
     * Color-codes the given type if colorization enabled
     * @param schemaConcept a type to color-code using ANSI colors
     * @return the type, color-coded
     */
    private String colorType(SchemaConcept<?> schemaConcept) {
        if(colorize) {
            return ANSI.color(label(schemaConcept.label()), ANSI.PURPLE);
        } else {
            return label(schemaConcept.label());
        }
    }

    /**
     * Includes ANSI unicode commands for different colours
     *
     */
    @SuppressWarnings("unused")
    public static class ANSI {

        private static final String RESET = "\u001B[0m";
        public static final String BLACK = "\u001B[30m";
        public static final String RED = "\u001B[31m";
        public static final String GREEN = "\u001B[32m";
        public static final String YELLOW = "\u001B[33m";
        public static final String BLUE = "\u001B[34m";
        public static final String PURPLE = "\u001B[35m";
        public static final String CYAN = "\u001B[36m";
        public static final String WHITE = "\u001B[37m";

        /**
         * @param string the string to set the color on
         * @param color the color to set on the string
         * @return a new string with the color set
         */
        public static String color(String string, String color) {
            return color + string + RESET;
        }
    }
}
