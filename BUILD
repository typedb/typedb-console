# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

package(default_visibility = ["//visibility:__subpackages__"])

load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@typedb_dependencies//tool/release/deps:rules.bzl", "release_validate_deps")

exports_files(["VERSION"])

release_validate_deps(
    name = "release-validate-deps",
    refs = "@typedb_console_workspace_refs//:refs.json",
    tagged_deps = [
        "@typedb_driver+",
        "@typeql+",
    ],
    tags = ["manual"], # in order for bazel test //... to not fail
    version_file = "VERSION",
)

checkstyle_test(
    name = "checkstyle",
    include = [
        "BUILD",
        "Cargo.toml",
        "Cargo.lock",
        "MODULE.bazel",
        "WORKSPACE",
        "deployment.bzl",
        "console.sh",
    ] + glob([
        ".circleci/**/*",
        ".factory/*",
    ]),
    exclude = [
        ".bazelversion",
        ".bazel-remote-cache.rc",
        ".bazel-cache-credential.json",
        ".circleci/windows/short_workspace.patch",
        ".circleci/windows/package_binary_as_exe.patch",
        "MODULE.bazel.lock",
    ],
    license_type = "mpl-header",
)

checkstyle_test(
    name = "checkstyle-license",
    include = ["LICENSE"],
    license_type = "mpl-fulltext",
)

filegroup(
    name = "rustfmt_config",
    srcs = ["rustfmt.toml"],
)

# Force tools to be built during `build //...`
filegroup(
    name = "tools",
    data = [
        "@typedb_dependencies//tool/checkstyle:test-coverage",
        "@typedb_dependencies//tool/bazelinstall:remote_cache_setup.sh",
        "@typedb_dependencies//tool/release/notes:create",
        "@typedb_dependencies//tool/ide:rust_sync",
        "@typedb_dependencies//tool/sonarcloud:code-analysis",
        "@typedb_dependencies//tool/unuseddeps:unused-deps",
        # TODO(bzlmod): @rust_analyzer_toolchain_tools is not available in Bzlmod
        # "@rust_analyzer_toolchain_tools//lib/rustlib/src:rustc_srcs",
        "@typedb_dependencies//tool/sync:dependencies",
    ],
)
