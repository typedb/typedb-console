# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

package(default_visibility = ["//visibility:__subpackages__"])

load("@rules_pkg//:pkg.bzl", "pkg_tar")
load("@typedb_bazel_distribution//artifact:rules.bzl", "deploy_artifact")
load("@typedb_bazel_distribution//common:rules.bzl", "assemble_targz", "assemble_zip")
load("@typedb_dependencies//distribution:deployment.bzl", "deployment")
load("@typedb_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@typedb_bazel_distribution//platform:constraints.bzl", "constraint_linux_arm64", "constraint_linux_x86_64",
     "constraint_mac_arm64", "constraint_mac_x86_64", "constraint_win_x86_64")

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

pkg_tar(
    name = "typeql-check-artifact-native",
    files = {":typeql-check-native" : "typeql-check"},
    extension = "tar.gz",
    visibility = ["//visibility:public"]
)

assemble_targz(
    name = "assemble-linux-x86_64-targz",
    output_filename = "typeql-check-linux-x86_64",
    targets = [":typeql-check-artifact-native"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_linux_x86_64,
)

assemble_targz(
    name = "assemble-linux-arm64-targz",
    output_filename = "typeql-check-linux-arm64",
    targets = [":typeql-check-artifact-native"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_linux_arm64
)

assemble_zip(
    name = "assemble-mac-x86_64-zip",
    output_filename = "typeql-check-mac-x86_64",
    targets = [":typeql-check-artifact-native"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_mac_x86_64
)

assemble_zip(
    name = "assemble-mac-arm64-zip",
    output_filename = "typeql-check-mac-arm64",
    targets = [":typeql-check-artifact-native"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_mac_arm64
)

assemble_zip(
    name = "assemble-windows-x86_64-zip",
    output_filename = "typeql-check-windows-x86_64",
    targets = [":typeql-check-artifact-native"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_win_x86_64
)

deploy_artifact(
    name = "deploy-linux-x86_64-targz",
    target = ":assemble-linux-x86_64-targz",
    artifact_group = "typeql-check-linux-x86_64",
    artifact_name = "typeql-check-linux-x86_64-{version}.tar.gz",
    snapshot = deployment['artifact']['snapshot']['upload'],
    release = deployment['artifact']['release']['upload'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-linux-arm64-targz",
    target = ":assemble-linux-arm64-targz",
    artifact_group = "typeql-check-linux-arm64",
    artifact_name = "typeql-check-linux-arm64-{version}.tar.gz",
    snapshot = deployment['artifact']['snapshot']['upload'],
    release = deployment['artifact']['release']['upload'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-mac-x86_64-zip",
    target = ":assemble-mac-x86_64-zip",
    artifact_group = "typeql-check-mac-x86_64",
    artifact_name = "typeql-check-mac-x86_64-{version}.zip",
    snapshot = deployment['artifact']['snapshot']['upload'],
    release = deployment['artifact']['release']['upload'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-mac-arm64-zip",
    target = ":assemble-mac-arm64-zip",
    artifact_group = "typeql-check-mac-arm64",
    artifact_name = "typeql-check-mac-arm64-{version}.zip",
    snapshot = deployment['artifact']['snapshot']['upload'],
    release = deployment['artifact']['release']['upload'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-windows-x86_64-zip",
    target = ":assemble-windows-x86_64-zip",
    artifact_group = "typeql-check-windows-x86_64",
    artifact_name = "typeql-check-windows-x86_64-{version}.zip",
    snapshot = deployment['artifact']['snapshot']['upload'],
    release = deployment['artifact']['release']['upload'],
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
