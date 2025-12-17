/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

use clap::Parser;

pub const ADDRESS_VALUE_NAME: &str = "host:port";
pub const USERNAME_VALUE_NAME: &str = "username";

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

    /// TypeDB address to connect to (host:port). If using TLS encryption, this must start with "https://".
    #[arg(long, value_name = ADDRESS_VALUE_NAME, conflicts_with_all = ["addresses", "address_translation"])]
    pub address: Option<String>,

    /// A comma-separated list of TypeDB replica addresses of a single cluster to connect to.
    #[arg(long, value_name = "host1:port1,host2:port2", conflicts_with_all = ["address", "address_translation"])]
    pub addresses: Option<String>,

    /// A comma-separated list of public=private address pairs. Public addresses are the user-facing
    /// addresses of the replicas, and private addresses are the addresses used for the server-side
    /// connection between replicas.
    #[arg(long, value_name = "public=private,...", conflicts_with_all = ["address", "addresses"])]
    pub address_translation: Option<String>,

    /// If used in a Cluster environment (Cloud or Enterprise), disables attempts to redirect 
    /// requests to server replicas, limiting Console to communicate only with the single address 
    /// specified in the `address` argument.
    /// Use for administrative / debug purposes to test a specific replica only: this option will
    /// lower the success rate of Console's operations in production.
    #[arg(long = "replication-disabled", default_value = "false")]
    pub replication_disabled: bool,

    /// Username for authentication
    #[arg(long, value_name = USERNAME_VALUE_NAME)]
    pub username: Option<String>,

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
    #[arg(long = "diagnostics-disabled", default_value = "false")]
    pub diagnostics_disabled: bool,

    /// Print the Console binary version
    #[arg(long = "version")]
    pub version: bool,
}
