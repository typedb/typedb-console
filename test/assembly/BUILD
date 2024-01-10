#
# Copyright (C) 2022 Vaticle
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

package(default_visibility = ["//visibility:__subpackages__"])

load("@vaticle_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@vaticle_dependencies//builder/java:rules.bzl", "typedb_java_test")

typedb_java_test(
    name = "test-assembly-native",
    test_class = "com.vaticle.typedb.console.test.assembly.AssemblyTest",
    srcs = ["AssemblyTest.java"],
    deps = [
        "//test/runner:typedb-console-runner",
        "@maven//:com_vaticle_typedb_typedb_runner",
    ],
    server_artifacts = {
        "@vaticle_bazel_distribution//platform:is_linux_arm64": "@vaticle_typedb_artifact_linux-arm64//file",
        "@vaticle_bazel_distribution//platform:is_linux_x86_64": "@vaticle_typedb_artifact_linux-x86_64//file",
        "@vaticle_bazel_distribution//platform:is_mac_arm64": "@vaticle_typedb_artifact_mac-arm64//file",
        "@vaticle_bazel_distribution//platform:is_mac_x86_64": "@vaticle_typedb_artifact_mac-86_64//file",
        "@vaticle_bazel_distribution//platform:is_windows_x86_64": "@vaticle_typedb_artifact_windows-x86_64//file",
    },
    console_artifacts = {
        "@vaticle_bazel_distribution//platform:is_linux_arm64": "//:assemble-linux-arm64-targz",
        "@vaticle_bazel_distribution//platform:is_linux_x86_64": "//:assemble-linux-x86_64-targz",
        "@vaticle_bazel_distribution//platform:is_mac_arm64": "//:assemble-mac-arm64-zip",
        "@vaticle_bazel_distribution//platform:is_mac_x86_64": "//:assemble-mac-x86_64-zip",
        "@vaticle_bazel_distribution//platform:is_windows_x86_64": "//:assemble-windows-x86_64-zip",
    },
)


checkstyle_test(
    name = "checkstyle",
    include = glob(["*"]),
    license_type = "agpl-header",
)
