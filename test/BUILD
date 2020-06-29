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

package(default_visibility = ["//visibility:__pkg__"])

load("@graknlabs_dependencies//tool/checkstyle:rules.bzl", "checkstyle_test")

java_test(
    name = "grakn-console-it",
    srcs = ["GraknConsoleIT.java"],
    test_class = "grakn.console.test.GraknConsoleIT",
    deps = [
        # Package dependencies
        "//:console",

        # Internal dependencies # TODO: should be removed with #1
        "@graknlabs_grakn_core//test/rule:grakn-test-server",

        # External dependencies from @graknlabs
        "@graknlabs_graql//java:graql",
        "@graknlabs_client_java//:client-java",

        # External dependencies
        "//dependencies/maven/artifacts/com/google/auto/value:auto-value",
        "@maven//:com_google_guava_guava",
        "@maven//:commons_io_commons_io",
        "@maven//:io_grpc_grpc_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
    data = [
        "file-(with-parentheses).gql",
        "invalid-data.gql",
        "//test/resources:grakn-properties",
        "//test/resources:cassandra-embedded"],
    size = "large",
    classpath_resources = [
        # TODO: should be removed with #1
        "@graknlabs_grakn_core//test/resources:logback-test",
    ]
)

checkstyle_test(
    name = "checkstyle",
    targets = [
        ":grakn-console-it",
    ],
)
