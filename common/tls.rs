/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use std::path::Path;

use typedb_driver::DriverTlsConfig;

/// Builds a `DriverTlsConfig` from the user's CLI choices:
/// - `disabled = true` returns a config with TLS disabled (plaintext connection).
/// - `disabled = false` with a `root_ca` path uses that file as the trust anchor.
/// - `disabled = false` with no `root_ca` uses the platform's native root CAs.
pub fn build_tls_config(disabled: bool, root_ca: Option<&Path>) -> Result<DriverTlsConfig, String> {
    if disabled {
        return Ok(DriverTlsConfig::disabled());
    }
    match root_ca {
        Some(ca) => DriverTlsConfig::enabled_with_root_ca(ca)
            .map_err(|err| format!("failed to load TLS root CA '{}': {err}", ca.display())),
        None => Ok(DriverTlsConfig::enabled_with_native_root_ca()),
    }
}
