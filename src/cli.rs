/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use clap::Parser;

#[derive(Parser, Debug)]
#[command(author, about)]
pub struct Args {
    /// Executes all console commands in the order specified, then exits.
    /// Exits early on any error.
    #[arg(long, value_name = "command")]
    pub command: Vec<String>,

    /// Executes all console commands directly from the script(s) in the order of each specified script.
    /// Exits if any script errors at any point.
    /// Files must follow the convention of terminating queries with an empty newline.
    /// File path can be absolute or relative to the current directory
    #[arg(long, value_name = "path to script file")]
    pub script: Vec<String>,

    /// TypeDB address to connect to. If using TLS encryption, this must start with "https://"
    #[arg(long, value_name = "host:port")]
    pub address: String,

    /// Username for authentication
    #[arg(long, value_name = "username")]
    pub username: String,

    /// Password for authentication. Will be requested safely by default.
    #[arg(long, value_name = "password")]
    pub password: Option<String>,

    /// Connect to TypeDB with TLS encryption. Disable with caution.
    /// On all production systems it should be enabled, otherwise username/password
    /// will be sent in plaintext over the network.
    #[arg(long = "tls-disabled", default_value = "false")]
    pub tls_disabled: bool,

    /// Path to the TLS encryption root CA file
    #[arg(long = "tls-root-ca", value_name = "path")]
    pub tls_root_ca: Option<String>,

    /// Disable error reporting. Error reporting helps TypeDB improve by reporting
    /// errors and crashes to the development team.
    #[arg(long = "diagnostics-disable", default_value = "false")]
    pub diagnostics_disable: bool,

    /// Print the Console binary version
    #[arg(long = "version")]
    pub version: bool,
}
