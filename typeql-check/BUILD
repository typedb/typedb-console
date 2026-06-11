# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

package(default_visibility = ["//visibility:__subpackages__"])

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@rules_rust//rust:defs.bzl", "rust_binary", "rustfmt_test")

rust_binary(
    name = "typeql-check-native",
    srcs = glob(["*.rs"]),
    deps = [
        "@typeql//rust:typeql",
    ],
    tags = [
        "crate-name=typeql-check"
    ],
    visibility = ["//visibility:public"],
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*"]),
    exclude = glob([
        "*.md",
        "Cargo.*"
    ]),
    license_type = "mpl-header",
)

rustfmt_test(
    name = "rustfmt_test",
    targets = [":typeql-check-native"],
    size = "small",
)
