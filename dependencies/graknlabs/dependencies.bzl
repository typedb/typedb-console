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
        commit = "1d61502f02df6491d2f35d0a373e2d64950b6dba", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_build_tools
    )

def graknlabs_common():
     git_repository(
         name = "graknlabs_common",
         remote = "https://github.com/graknlabs/common",
         commit = "e754519308cd1ff3f68f77747d255b691746f2b8", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
     )

def graknlabs_graql():
     git_repository(
         name = "graknlabs_graql",
         remote = "https://github.com/graknlabs/graql",
         commit = "0ea7826a36c99f999e8a79a2af9293648ece5409", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
     )

def graknlabs_grakn_core():
     git_repository(
         name = "graknlabs_grakn_core",
         remote = "https://github.com/flyingsilverfin/grakn",
         commit = "5ab7c3f012d3cce9be12c300c5f1e965d6e08cce", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grakn_core
     )

def graknlabs_protocol():
    git_repository(
        name = "graknlabs_protocol",
        remote = "https://github.com/graknlabs/protocol",
        commit = "f87b586aa646889052cfe9dc17fc4ab2eee8aa0a", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_protocol
    )

def graknlabs_client_java():
     git_repository(
         name = "graknlabs_client_java",
#         remote = "https://github.com/graknlabs/client-java",
#         commit = "f72f7e3d21fde14c05abd6a47adcd03212d69eca", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
         remote = "https://github.com/flyingsilverfin/client-java",
         commit = "5c4159b38686a43dc8a2c25fed7f6af2ade15a8e", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
     )

def graknlabs_grabl_tracing():
    git_repository(
        name = "graknlabs_grabl_tracing",
        remote = "https://github.com/graknlabs/grabl-tracing",
        commit = "42f507d6b973cbc87d18a27ee83121c791295184" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grabl_tracing
    )