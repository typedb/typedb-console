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
        commit = "fb6e02f964e8f1742febe788ba3dbbcc17551cb0", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_build_tools
    )

def graknlabs_common():
     git_repository(
         name = "graknlabs_common",
         remote = "https://github.com/graknlabs/common",
         commit = "9e590131790cd6a79326a31dbe687d77ea629194", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
     )

def graknlabs_graql():
     git_repository(
         name = "graknlabs_graql",
         remote = "https://github.com/graknlabs/graql",
         commit = "4d2a3afd0b8e5c8ed43677610e74780ecaaacba2", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
     )

def graknlabs_grakn_core():
     git_repository(
         name = "graknlabs_grakn_core",
         remote = "https://github.com/graknlabs/grakn",
         commit = "abf625542e956f03f4ccfbcf5de8513477eaa786", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grakn_core
     )

def graknlabs_protocol():
    git_repository(
        name = "graknlabs_protocol",
        remote = "https://github.com/graknlabs/protocol",
        commit = "7af1e68d30335db509d220dcdc2f050177101227", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_protocol
    )

def graknlabs_client_java():
     git_repository(
         name = "graknlabs_client_java",
         remote = "https://github.com/graknlabs/client-java",
         commit = "006ef347514bcca72e8a292db4615a0a8fc2fd4e", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
     )

def graknlabs_grabl_tracing():
    git_repository(
        name = "graknlabs_grabl_tracing",
        remote = "https://github.com/graknlabs/grabl-tracing",
        commit = "555b090293cf98cd57e590090e355612cde662b5" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grabl_tracing
    )