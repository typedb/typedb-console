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

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def graknlabs_build_tools():
    git_repository(
        name = "graknlabs_build_tools",
        remote = "https://github.com/graknlabs/build-tools",
        commit = "715e4802ea375abda44e24b2bb092ee14de72e42", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_build_tools
    )

def graknlabs_common():
    git_repository(
        name = "graknlabs_common",
        remote = "https://github.com/graknlabs/common",
        tag = "0.2.2", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
    )

def graknlabs_graql():
    git_repository(
        name = "graknlabs_graql",
        remote = "https://github.com/graknlabs/graql",
        commit = "4e37a87cb562e47f04c270c6147eac2567d4a223",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
    )

def graknlabs_grakn_core():
    git_repository(
        name = "graknlabs_grakn_core",
        remote = "https://github.com/graknlabs/grakn",
        commit = "ec71af64e03ea737377680e6ecae648d4406a1e7", # do not sync @graknlabs_grakn_core, it will create a cyclic dependency
    )

def graknlabs_protocol():
    git_repository(
        name = "graknlabs_protocol",
        remote = "https://github.com/graknlabs/protocol",
        commit = "4ad2dfeaeca85f4ba2f2b42c263d3adf3e36acc3", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_protocol
    )

def graknlabs_client_java():
    git_repository(
        name = "graknlabs_client_java",
        remote = "https://github.com/graknlabs/client-java",
        commit = "dfb601484afeb8ee622828072c565479e74da2b1", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
    )

def graknlabs_grabl_tracing():
    git_repository(
        name = "graknlabs_grabl_tracing",
        remote = "https://github.com/graknlabs/grabl-tracing",
        commit = "c533775da0af19c5913e725486d68cc849b0dbdb" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grabl_tracing
    )
