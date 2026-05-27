/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::collections::HashMap;

use typedb_driver::Addresses;

/// Parses a comma-separated list of `host:port` addresses.
pub fn parse_addresses(addresses: &str) -> Result<Addresses, String> {
    let split = addresses.split(',').map(str::to_string).collect::<Vec<_>>();
    Addresses::try_from_addresses_str(split).map_err(|err| format!("invalid addresses '{addresses}': {err}"))
}

/// Parses a comma-separated list of `public=private` address pairs. Public addresses are the
/// user-facing addresses; private addresses are the ones the servers use to talk to each other.
pub fn parse_address_translation(translation: &str) -> Result<Addresses, String> {
    let mut map = HashMap::new();
    for pair in translation.split(',') {
        let (public_address, private_address) = pair
            .split_once('=')
            .ok_or_else(|| format!("invalid address pair '{pair}', must be of form '<public=private,...>'"))?;
        map.insert(public_address.to_string(), private_address.to_string());
    }
    Addresses::try_from_translation_str(map).map_err(|err| format!("invalid addresses '{translation}': {err}"))
}
