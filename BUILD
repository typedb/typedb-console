#
# Copyright (C) 2021 Grakn Labs
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

load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_tar")
load("@graknlabs_bazel_distribution//artifact:rules.bzl", "deploy_artifact")
load("@graknlabs_bazel_distribution//common:rules.bzl", "assemble_targz", "java_deps", "assemble_zip", "assemble_versioned")
load("@graknlabs_bazel_distribution//github:rules.bzl", "deploy_github")
load("@graknlabs_bazel_distribution//apt:rules.bzl", "assemble_apt", "deploy_apt")
load("@graknlabs_dependencies//distribution:deployment.bzl", "deployment")
load("@graknlabs_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")
load("@graknlabs_dependencies//tool/release:rules.bzl", "release_validate_deps")
load("//:deployment.bzl", deployment_console = "deployment")

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
    name = "console",
    srcs = glob(["*.java", "command/*.java", "common/*.java", "common/exception/*.java"]) + [":version"],
    deps = [
        "@graknlabs_client_java//:client-java",
        "@graknlabs_graql//java:graql",
        "@graknlabs_graql//java/common:common",
        "@graknlabs_graql//java/query",
        "@graknlabs_graql//java/pattern",
        "@graknlabs_common//:common",

        # External dependencies
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:io_grpc_grpc_core",
        "@maven//:io_grpc_grpc_api",
        "@maven//:org_jline_jline",
        "@maven//:info_picocli_picocli",
        "@maven//:org_slf4j_slf4j_api",
    ],
    visibility = ["//visibility:public"],
    resources = ["LICENSE"],
    tags = ["maven_coordinates=io.grakn.console:grakn-console:{pom_version}"],
)

java_binary(
    name = "console-binary",
    main_class = "grakn.console.GraknConsole",
    runtime_deps = [":console"],
    visibility = ["//:__pkg__"],
    resource_strip_prefix = "conf/logback",
    resources = ["//conf/logback:logback.xml"]
)

java_deps(
    name = "console-deps",
    target = ":console-binary",
    java_deps_root = "console/services/lib/",
    visibility = ["//visibility:public"],
)

pkg_tar(
    name = "console-artifact",
    deps = [":console-deps"],
    extension = "tar.gz",
    files = {
        "//conf/logback:logback.xml": "console/conf/logback.xml"
    },
    visibility = ["//visibility:public"]
)

assemble_targz(
    name = "assemble-linux-targz",
    output_filename = "grakn-console-linux",
    targets = [":console-artifact", "@graknlabs_common//binary:assemble-bash-targz"],
    visibility = ["//visibility:public"]
)

assemble_zip(
    name = "assemble-mac-zip",
    output_filename = "grakn-console-mac",
    targets = [":console-artifact", "@graknlabs_common//binary:assemble-bash-targz"],
    visibility = ["//visibility:public"]
)

assemble_zip(
    name = "assemble-windows-zip",
    output_filename = "grakn-console-windows",
    targets = [":console-artifact", "@graknlabs_common//binary:assemble-bat-targz"],
    visibility = ["//visibility:public"]
)

deploy_artifact(
    name = "deploy-linux-targz",
    target = ":assemble-linux-targz",
    artifact_group = "graknlabs_console",
    artifact_name = "grakn-console-linux-{version}.tar.gz",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-mac-zip",
    target = ":assemble-mac-zip",
    artifact_group = "graknlabs_console",
    artifact_name = "grakn-console-mac-{version}.zip",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

deploy_artifact(
    name = "deploy-windows-zip",
    target = ":assemble-windows-zip",
    artifact_group = "graknlabs_console",
    artifact_name = "grakn-console-windows-{version}.zip",
    snapshot = deployment['artifact.snapshot'],
    release = deployment['artifact.release'],
    visibility = ["//visibility:public"],
)

assemble_versioned(
    name = "assemble-versioned-all",
    targets = [
        ":assemble-linux-targz",
        ":assemble-mac-zip",
        ":assemble-windows-zip",
    ],
)

deploy_github(
    name = "deploy-github",
    organisation = deployment_console["github.organisation"],
    repository = deployment_console["github.repository"],
    title = "Grakn Console",
    title_append_version = True,
    release_description = "//:RELEASE_TEMPLATE.md",
    archive = ":assemble-versioned-all",
    draft = False
)

assemble_apt(
    name = "assemble-linux-apt",
    package_name = "grakn-console",
    maintainer = "Grakn Labs <community@grakn.ai>",
    description = "Grakn Core (console)",
    depends = [
      "openjdk-8-jre",
      "grakn-bin (>=%{@graknlabs_common})"
    ],
    workspace_refs = "@graknlabs_console_workspace_refs//:refs.json",
    files = {
        "//conf/logback:logback.xml": "console/conf/logback.xml"
    },
    archives = [":console-deps"],
    installation_dir = "/opt/grakn/core/",
    empty_dirs = [
         "opt/grakn/core/console/services/lib/",
    ],
)

deploy_apt(
    name = "deploy-apt",
    target = ":assemble-linux-apt",
    snapshot = deployment['apt.snapshot'],
    release = deployment['apt.release'],
)

release_validate_deps(
    name = "release-validate-deps",
    refs = "@graknlabs_console_workspace_refs//:refs.json",
    tagged_deps = [
        "@graknlabs_common",
        "@graknlabs_graql",
        "@graknlabs_client_java",
    ],
    tags = ["manual"]  # in order for bazel test //... to not fail
)

checkstyle_test(
    name = "checkstyle",
    include = glob(["*", "command/*", "common/*", "common/exception/*", ".grabl/*"]),
    license_type = "agpl",
)

# CI targets that are not declared in any BUILD file, but are called externally
filegroup(
    name = "ci",
    data = [
        "@graknlabs_dependencies//library/maven:update",
        "@graknlabs_dependencies//tool/bazelrun:rbe",
        "@graknlabs_dependencies//distribution/artifact:create-netrc",
        "@graknlabs_dependencies//tool/checkstyle:test-coverage",
        "@graknlabs_dependencies//tool/sonarcloud:code-analysis",
        "@graknlabs_dependencies//tool/release:approval",
        "@graknlabs_dependencies//tool/release:create-notes",
        "@graknlabs_dependencies//tool/sync:dependencies",
    ],
)
