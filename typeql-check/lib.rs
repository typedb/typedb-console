/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn check(query: &str) -> Result<(), JsError> {
    typeql::parse_query(query).map(|_| ()).map_err(|e| JsError::new(&e.to_string()))
}
