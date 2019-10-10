#
# GRAKN.AI - THE KNOWLEDGE GRAPH
# Copyright (C) 2019 Grakn Labs Ltd
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

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def graknlabs_build_tools():
    git_repository(
        name = "graknlabs_build_tools",
        remote = "https://github.com/graknlabs/build-tools",
        commit = "a898bbb0d88932409addbba2a18846ddaaf18f91", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_build_tools
    )

def graknlabs_common():
     git_repository(
         name = "graknlabs_common",
         remote = "https://github.com/graknlabs/common",
         commit = "80d0a15c25fbc8c2ace6470c216648a3455a2f28", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
     )

def graknlabs_graql():
     git_repository(
         name = "graknlabs_graql",
         remote = "https://github.com/graknlabs/graql",
         commit = "d337d35e72a7fe363dbe91b2f85f737fb2be7d2f", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
     )

def graknlabs_grakn_core():
     git_repository(
         name = "graknlabs_grakn_core",
         remote = "https://github.com/graknlabs/grakn",
         commit = "7753f10ec1e8e36a6d102136add64771a4a964d5", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grakn_core
     )

def graknlabs_protocol():
    git_repository(
        name = "graknlabs_protocol",
        remote = "https://github.com/graknlabs/protocol",
        commit = "90f6d3f40ee4eb2311836370665dcf808c015957", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_protocol
    )

def graknlabs_client_java():
     git_repository(
         name = "graknlabs_client_java",
         remote = "https://github.com/graknlabs/client-java",
         commit = "b53ecdb9dbf2fda71e6588cf1334d6ecb3056dfb", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
     )
