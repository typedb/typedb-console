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
        commit = "f17e730b628ff9957cbd31bfa3e1780857ac86c9", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_build_tools
    )

def graknlabs_common():
     git_repository(
         name = "graknlabs_common",
         remote = "https://github.com/graknlabs/common",
         commit = "80e7505bd01afc6e048891e0b6f88ab80239f3f0", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
     )

def graknlabs_graql():
     git_repository(
         name = "graknlabs_graql",
         remote = "https://github.com/graknlabs/graql",
         commit = "5bf8ea37b0555abb16e66c8d1e90e980347ac46e", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
     )

def graknlabs_grakn_core():
     git_repository(
         name = "graknlabs_grakn_core",
         remote = "https://github.com/graknlabs/grakn",
         commit = "23390e71f2f003af3de3f1106a64ecfb8eabe700", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_grakn_core
     )

def graknlabs_protocol():
    git_repository(
        name = "graknlabs_protocol",
        remote = "https://github.com/graknlabs/protocol",
        commit = "246139d83ad8d3b5b3f37e2bf26d3b56fad4d8bc", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_protocol
    )

def graknlabs_client_java():
     git_repository(
         name = "graknlabs_client_java",
         remote = "https://github.com/graknlabs/client-java",
         commit = "9f706f035562afe6558eb48f0e05320f898cb7d7", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
     )
