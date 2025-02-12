/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use typedb_driver::answer::{ConceptDocument, ConceptRow};
use typedb_driver::concept::{Concept, Value};
use typedb_driver::IID;

const TABLE_INDENT: &'static str = "   ";
const CONTENT_INDENT: &'static str = "    ";
const TABLE_DASHES: usize = 7;

fn println(string: &str) {
    println!("{}", string)
}

pub(crate) fn print_document(document: ConceptDocument) {
    // Note: inefficient, but easy...
    let parsed: serde_json::Value = serde_json::from_str(&document.into_json().to_string()).unwrap();
    println(&serde_json::to_string_pretty(&parsed).unwrap());
}

pub(crate) fn print_row(row: ConceptRow, is_first: bool) {
    let variable_column_width = row.get_column_names()
        .iter()
        .map(|s| s.len())
        .max()
        .unwrap_or(0);
    if is_first {
        println(&line_dash_separator(variable_column_width));
    }
    println(&concept_row_display_string(&row, variable_column_width));
}

fn concept_row_display_string(
    concept_row: &ConceptRow,
    variable_column_width: usize,
) -> String {
    let column_names = concept_row.get_column_names();
    let content = column_names
        .iter()
        .map(|column_name| {
            let concept = concept_row.get(column_name).unwrap();
            let mut string = String::new();
            string.push('$');
            string.push_str(column_name);
            string.push_str(&" ".repeat(variable_column_width - column_name.len() + 1));
            string.push_str("| ");
            string.push_str(&concept_display_string(concept));
            string
        })
        .collect::<Vec<_>>()
        .join("\n");

    let mut string = String::new();
    string.push_str(&indent(CONTENT_INDENT, &content));
    string.push('\n');
    string.push_str(&line_dash_separator(variable_column_width));
    string
}

fn concept_display_string(concept: Option<&Concept>) -> String {
    match concept {
        None => "".to_owned(),
        Some(concept) => {
            match concept {
                Concept::EntityType(type_) => {
                    format!("{}", format_type(&type_.label))
                }
                Concept::RelationType(type_) => {
                    format!("{}", format_type(&type_.label))
                }
                Concept::RoleType(type_) => {
                    format!("{}", format_type(&type_.label))
                }
                Concept::AttributeType(type_) => {
                    format!("{}", format_type(&type_.label))
                }
                Concept::Entity(entity) => {
                    format!(
                        "{}, {}",
                        entity.type_.as_ref().map(|t| format_isa(t.label())).unwrap_or(String::new()),
                        format_iid(&entity.iid),
                    )
                }
                Concept::Relation(relation) => {
                    format!(
                        "{}, {}",
                        relation.type_.as_ref().map(|t| format_isa(t.label())).unwrap_or(String::new()),
                        format_iid(&relation.iid),
                    )
                }
                Concept::Attribute(attribute) => {
                    format!(
                        "{} {}",
                        attribute.type_.as_ref().map(|t| format_isa(t.label())).unwrap_or(String::new()),
                        format_value(&attribute.value),
                    )
                }
                Concept::Value(value) => {
                    format_value(&value)
                }
            }
        }
    }
}

fn format_type(label: &str) -> String {
    format!("\x1b[95m{}\x1b[0m", label)
}

fn format_iid(iid: &IID) -> String {
    format!("{} {}", format_keyword("iid"), iid)
}

fn format_isa(label: &str) -> String {
    format!("{} \x1b[95m{}\x1b[0m", format_keyword("isa"), label)
}

fn format_value(value: &Value) -> String {
    format!("{}", value)
}

fn format_keyword(keyword: &str) -> String {
    format!("\x1b[94m{}\x1b[0m", keyword)
}

fn indent(indent: &str, string: &str) -> String {
    string
        .split('\n')
        .map(|s| format!("{}{}", indent, s))
        .collect::<Vec<_>>()
        .join("\n")
}

fn line_dash_separator(additional_dashes_num: usize) -> String {
    indent(
        TABLE_INDENT,
        &"-".repeat(TABLE_DASHES + additional_dashes_num),
    )
}
