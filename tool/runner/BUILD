# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")

load("@rules_rust//rust:defs.bzl", "rust_library")

rust_library(
    name = "typedb_binary_runner",
    srcs = glob(["*.rs"]),
    crate_root = "lib.rs",
    deps = [
        "@crates//:clap",
        "@crates//:tempdir",
    ],
    visibility = ["//visibility:public"],
    tags = ["crate-name=typedb_binary_runner"],
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*", "*/*"]),
    exclude = glob(["Cargo.*", "target/**"]),
    license_type = "mpl-header",
)
