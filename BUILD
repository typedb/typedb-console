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

load("@rules_pkg//:pkg.bzl", "pkg_tar")
load("@vaticle_bazel_distribution//artifact:rules.bzl", "deploy_artifact")
load("@vaticle_bazel_distribution//common:rules.bzl", "assemble_targz", "java_deps", "assemble_zip", "assemble_versioned")
load("@vaticle_bazel_distribution//github:rules.bzl", "deploy_github")
load("@vaticle_bazel_distribution//apt:rules.bzl", "assemble_apt", "deploy_apt")
load("@vaticle_dependencies//distribution:deployment.bzl", "deployment")
load("@vaticle_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@vaticle_dependencies//tool/release/deps:rules.bzl", "release_validate_deps")
load("//:deployment.bzl", deployment_console = "deployment")
load("@vaticle_dependencies//util/platform:constraints.bzl", "constraint_linux_aarch64", "constraint_linux_x86_64",
     "constraint_mac_aarch64", "constraint_mac_x86_64", "constraint_win_aarch64")

genrule(
    name = "version",
    srcs = [
        "//templates:Version.java",
        ":VERSION",
    ],
    cmd = "VERSION=`cat $(location :VERSION)`;sed -e \"s/{version}/$$VERSION/g\" $(location //templates:Version.java) >> $@",
    outs = ["Version.java"],
    visibility = ["//visibility:public"]
)

java_library(
    name = "console-native",
    srcs = glob(["*.java", "*/*.java", "*/*/*.java"], exclude=["bazel-*/*"]) + [":version"],
    deps = [
        "@vaticle_typedb_driver//java:driver-java",
        "@vaticle_typedb_driver//java/api",
        "@vaticle_typedb_driver//java/common",
        "@vaticle_typeql//java:typeql-lang",
        "@vaticle_typeql//java/common:common",
        "@vaticle_typeql//java/query",
        "@vaticle_typeql//java/pattern",
        "@vaticle_typedb_common//:common",

        # External dependencies
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:io_grpc_grpc_core",
        "@maven//:io_grpc_grpc_api",
        "@maven//:org_jline_jline",
        "@maven//:org_jline_jline_terminal_jansi",
        "@maven//:info_picocli_picocli",
        "@maven//:org_slf4j_slf4j_api",
    ],
    visibility = ["//visibility:public"],
    resources = ["LICENSE"],
)

java_binary(
    name = "console-binary-native",
    main_class = "com.vaticle.typedb.console.TypeDBConsole",
    runtime_deps = [":console-native"],
    visibility = ["//:__pkg__"],
    # If running the console binary directly, include the following logback to reduce noise
#    resource_strip_prefix = "conf/logback",
#    resources = ["//conf/logback:logback.xml"]
)

java_deps(
    name = "console-deps-native",
    target = ":console-binary-native",
    java_deps_root = "console/lib/",
    visibility = ["//visibility:public"],
)

pkg_tar(
    name = "console-artifact-native",
    deps = [":console-deps-native"],
    extension = "tar.gz",
    files = {
        "//conf/logback:logback.xml": "console/conf/logback.xml"
    },
    visibility = ["//visibility:public"]
)

assemble_targz(
    name = "assemble-linux-x86_64-targz",
    output_filename = "typedb-console-linux-x86_64",
    targets = [":console-artifact-native", "@vaticle_typedb_common//binary:assemble-bash-targz"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_linux_x86_64,
)

assemble_targz(
    name = "assemble-linux-aarch64-targz",
    output_filename = "typedb-console-linux-aarch64",
    targets = [":console-artifact-native", "@vaticle_typedb_common//binary:assemble-bash-targz"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_linux_aarch64
)

assemble_zip(
    name = "assemble-mac-x86_64-zip",
    output_filename = "typedb-console-mac-x86_64",
    targets = [":console-artifact-native", "@vaticle_typedb_common//binary:assemble-bash-targz"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_mac_x86_64
)

assemble_zip(
    name = "assemble-mac-aarch64-zip",
    output_filename = "typedb-console-mac-aarch64",
    targets = [":console-artifact-native", "@vaticle_typedb_common//binary:assemble-bash-targz"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_mac_aarch64
)

assemble_zip(
    name = "assemble-windows-x86_64-zip",
    output_filename = "typedb-console-windows-x86_64",
    targets = [":console-artifact-native", "@vaticle_typedb_common//binary:assemble-bat-targz"],
    visibility = ["//visibility:public"],
    target_compatible_with = constraint_win_aarch64
)

deploy_artifact(
    name = "deploy-linux-x86_64-targz",
    target = ":assemble-linux-x86_64-targz",
    artifact_group = "vaticle_typedb_console",
    artifact_name = "typedb-console-linux-x86_64-{version}.tar.gz",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-linux-aarch64-targz",
    target = ":assemble-linux-aarch64-targz",
    artifact_group = "vaticle_typedb_console",
    artifact_name = "typedb-console-linux-aarch64-{version}.tar.gz",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-mac-x86_64-zip",
    target = ":assemble-mac-x86_64-zip",
    artifact_group = "vaticle_typedb_console",
    artifact_name = "typedb-console-mac-x86_64-{version}.zip",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-mac-aarch64-zip",
    target = ":assemble-mac-aarch64-zip",
    artifact_group = "vaticle_typedb_console",
    artifact_name = "typedb-console-mac-aarch64-{version}.zip",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-windows-x86_64-zip",
    target = ":assemble-windows-x86_64-zip",
    artifact_group = "vaticle_typedb_console",
    artifact_name = "typedb-console-windows-x86_64-{version}.zip",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)
#
#assemble_versioned(
#    name = "assemble-versioned-all",
#    targets = [
#        ":assemble-linux-targz",
#        ":assemble-mac-zip",
#        ":assemble-windows-zip",
#    ],
#)
#
#deploy_github(
#    name = "deploy-github",
#    organisation = deployment_console["github.organisation"],
#    repository = deployment_console["github.repository"],
#    title = "TypeDB Console",
#    title_append_version = True,
#    release_description = "//:RELEASE_NOTES_LATEST.md",
#    archive = ":assemble-versioned-all",
#    draft = False
#)

assemble_apt(
    name = "assemble-linux-x86_64-apt",
    package_name = "typedb-console",
    maintainer = "Vaticle <community@vaticle.com>",
    description = "TypeDB (console)",
    depends = [
      "openjdk-11-jre",
      "typedb-bin (>=%{@vaticle_typedb_common})"
    ],
    workspace_refs = "@vaticle_typedb_console_workspace_refs//:refs.json",
    files = {
        "//conf/logback:logback.xml": "console/conf/logback.xml"
    },
    archives = [":console-deps-native"],
    installation_dir = "/opt/typedb/core/",
    empty_dirs = [
         "opt/typedb/core/console/lib/",
    ],
    architecture = "x86_64",
)

assemble_apt(
    name = "assemble-linux-aarch64-apt",
    package_name = "typedb-console",
    maintainer = "Vaticle <community@vaticle.com>",
    description = "TypeDB (console)",
    depends = [
      "openjdk-11-jre",
      "typedb-bin (>=%{@vaticle_typedb_common})"
    ],
    workspace_refs = "@vaticle_typedb_console_workspace_refs//:refs.json",
    files = {
        "//conf/logback:logback.xml": "console/conf/logback.xml"
    },
    archives = [":console-deps-native"],
    installation_dir = "/opt/typedb/core/",
    empty_dirs = [
         "opt/typedb/core/console/lib/",
    ],
    architecture = "aarch64",
)

deploy_apt(
    name = "deploy-apt-x86_64",
    target = ":assemble-linux-x86_64-apt",
    snapshot = deployment['apt.snapshot'],
    release = deployment['apt.release'],
)

deploy_apt(
    name = "deploy-apt-aarch64",
    target = ":assemble-linux-aarch64-apt",
    snapshot = deployment['apt.snapshot'],
    release = deployment['apt.release'],
)

release_validate_deps(
    name = "release-validate-deps",
    refs = "@vaticle_typedb_console_workspace_refs//:refs.json",
    tagged_deps = [
        "@vaticle_typedb_common",
        "@vaticle_typeql",
        "@vaticle_typedb_driver",
    ],
    tags = ["manual"]  # in order for bazel test //... to not fail
)

checkstyle_test(
    name = "checkstyle",
    include = glob([
        "*",
        ".factory/*",
        "command/*",
        "common/*",
        "common/exception/*",
    ]),
    exclude = [
        ".bazelversion",
        ".bazel-remote-cache.rc",
        ".bazel-cache-credential.json",
        "LICENSE",
        "VERSION",
    ] + glob(["*.md"]),
    license_type = "agpl-header",
)

checkstyle_test(
    name = "checkstyle-license",
    include = ["LICENSE"],
    license_type = "agpl-fulltext",
)

# CI targets that are not declared in any BUILD file, but are called externally
filegroup(
    name = "ci",
    data = [
        "@vaticle_dependencies//library/maven:update",
        "@vaticle_dependencies//distribution/artifact:create-netrc",
        "@vaticle_dependencies//tool/checkstyle:test-coverage",
        "@vaticle_dependencies//tool/sonarcloud:code-analysis",
        "@vaticle_dependencies//tool/release/notes:create",
        "@vaticle_dependencies//tool/release/notes:validate",
    ],
)
