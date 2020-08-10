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
        remote = "https://github.com/graknlabs/dependencies",
        commit = "1f7e4a696cc7b7492354750c337ae3f67ff39f06",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_dependencies
    )

def graknlabs_common():
    git_repository(
        name = "graknlabs_common",
        remote = "https://github.com/graknlabs/common",
        tag = "0.2.3" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
    )

def graknlabs_graql():
    git_repository(
        name = "graknlabs_graql",
        remote = "https://github.com/graknlabs/graql",
        tag = "1.0.8" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_graql
    )

def graknlabs_client_java():
    git_repository(
        name = "graknlabs_client_java",
        remote = "https://github.com/graknlabs/client-java",
        tag = "1.8.2",
    )
