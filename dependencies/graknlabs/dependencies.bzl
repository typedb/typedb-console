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

def graknlabs_dependencies():
    git_repository(
        name = "graknlabs_dependencies",
        remote = "https://github.com/lolski/dependencies",
        commit = "dd58b33370129658aaaf0b2ea25d583fa6b7f06f", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_dependencies
    )

def graknlabs_common():
    git_repository(
        name = "graknlabs_common",
        remote = "https://github.com/lolski/common",
        commit = "60c5f9b2929452ba3011e6bc2f8e67da645eeee1", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
    )

def graknlabs_graql():
    git_repository(
        name = "graknlabs_graql",
        remote = "https://github.com/lolski/graql",
        commit = "0a46b5da5fb09756299057010d65f6d563bb5210" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
    )

def graknlabs_grakn_core():
    git_repository(
        name = "graknlabs_grakn_core",
        remote = "https://github.com/lolski/grakn",
        commit = "923ef5abd24e22d00c842f579dc33bfba41bb35d", # do not sync @graknlabs_grakn_core, it will create a cyclic dependency
    )

def graknlabs_client_java():
    git_repository(
        name = "graknlabs_client_java",
        remote = "https://github.com/lolski/client-java",
        commit = "86c02cbee353b3f22bf5c8b4a357b25250c13c56", # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
    )
