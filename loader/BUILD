# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

package(default_visibility = ["//visibility:__subpackages__"])

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")

load("@rules_rust//rust:defs.bzl", "rust_binary", "rustfmt_test")

rust_binary(
    name = "loader-native",
    srcs = glob(["*.rs"]),
    deps = [
        "@crates//:clap",
    ],
    tags = [
        "crate-name=typedb-console-loader"
    ],
    visibility = ["//visibility:public"],
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*"]),
    exclude = glob(["Cargo.*"]),
    license_type = "mpl-header",
)

rustfmt_test(
    name = "rustfmt_test",
    targets = [":loader-native"],
    size = "small",
)
