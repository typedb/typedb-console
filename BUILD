#
# Copyright (C) 2020 Grakn Labs
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
load("@graknlabs_build_tools//distribution/maven:rules.bzl", "assemble_maven", "deploy_maven")
load("@graknlabs_bazel_distribution//common:rules.bzl", "assemble_targz", "java_deps", "assemble_zip", "assemble_versioned")
load("@graknlabs_bazel_distribution//github:rules.bzl", "deploy_github")
load("@graknlabs_bazel_distribution//apt:rules.bzl", "assemble_apt", "deploy_apt")
load("@graknlabs_bazel_distribution//rpm:rules.bzl", "assemble_rpm", "deploy_rpm")
load("@graknlabs_bazel_distribution//distribution:rules.bzl", "deploy_distribution")
load("@graknlabs_build_tools//checkstyle:rules.bzl", "checkstyle_test")

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
    srcs = glob([
        "*.java",
        "exception/*.java",
        "printer/*.java",
    ]) + [":version"],
    deps = [
        "@graknlabs_client_java//:client-java",
        "@graknlabs_graql//java:graql",


        # External dependencies
        "//dependencies/maven/artifacts/commons-cli",
        "//dependencies/maven/artifacts/commons-lang:commons-lang", # PREVIOUSLY UNDECLARED
        "//dependencies/maven/artifacts/com/google/code/findbugs:jsr305",
        "//dependencies/maven/artifacts/io/grpc:grpc-core",
        "//dependencies/maven/artifacts/io/grpc:grpc-api",
        "//dependencies/maven/artifacts/jline:jline",
        "//dependencies/maven/artifacts/org/slf4j:slf4j-api",
    ],
    visibility = ["//visibility:public"],
    resources = ["LICENSE"],
    tags = ["maven_coordinates=io.grakn.console:grakn-console:{pom_version}"],
)

checkstyle_test(
    name = "checkstyle",
    targets = [
        ":console",
    ],
)

java_binary(
    name = "console-binary",
    main_class = "grakn.console.GraknConsole",
    runtime_deps = [":console"],
    visibility = ["//:__pkg__"],
)

java_deps(
    name = "console-deps",
    target = ":console-binary",
    java_deps_root = "console/services/lib/",
    visibility = ["//visibility:public"],
)

pkg_tar(
    name = "console-distribution",
    deps = [":console-deps"],
    files = {
        "//config/logback:logback.xml": "console/conf/logback.xml"
    },
    visibility = ["//visibility:public"]
)

deploy_distribution(
    name = "deploy-console-distribution",
    target = ":console-distribution",
    artifact_group = "graknlabs_console",
    deployment_properties = "@graknlabs_build_tools//:deployment.properties",
    visibility = ["//visibility:public"],
)

assemble_targz(
    name = "assemble-linux-targz",
    output_filename = "grakn-console-linux",
    targets = [":console-distribution", "@graknlabs_common//bin:assemble-bash-targz"],
    visibility = ["//visibility:public"]
)

assemble_zip(
    name = "assemble-mac-zip",
    output_filename = "grakn-console-mac",
    targets = [":console-distribution", "@graknlabs_common//bin:assemble-bash-targz"],
    visibility = ["//visibility:public"]
)

assemble_zip(
    name = "assemble-windows-zip",
    output_filename = "grakn-console-windows",
    targets = [":console-distribution", "@graknlabs_common//bin:assemble-bash-targz"],
    visibility = ["//visibility:public"]
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
    deployment_properties = "//:deployment.properties",
    title = "Grakn Console",
    title_append_version = True,
    release_description = "//:RELEASE_TEMPLATE.md",
    archive = ":assemble-versioned-all",
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
        "//config/logback:logback.xml": "console/conf/logback.xml"
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
    deployment_properties = "@graknlabs_build_tools//:deployment.properties",
)

assemble_rpm(
    name = "assemble-linux-rpm",
    package_name = "grakn-console",
    installation_dir = "/opt/grakn/core/",
    spec_file = "//config/rpm:grakn-console.spec",
    workspace_refs = "@graknlabs_console_workspace_refs//:refs.json",
    archives = [":console-deps"],
    files = {
        "//config/logback:logback.xml": "console/conf/logback.xml"
    },
    empty_dirs = [
         "opt/grakn/core/console/services/lib/",
    ],
)

deploy_rpm(
    name = "deploy-rpm",
    target = ":assemble-linux-rpm",
    deployment_properties = "@graknlabs_build_tools//:deployment.properties",
)