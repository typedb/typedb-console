
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

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def graknlabs_dependencies():
    git_repository(
        name = "graknlabs_dependencies",
        remote = "https://github.com/graknlabs/dependencies",
        commit = "adb0d08bf56a64ae7d731800cfc0f85c63733cf1",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_dependencies
    )

def graknlabs_common():
    git_repository(
        name = "graknlabs_common",
        remote = "https://github.com/graknlabs/common",
        tag = "2.0.0" # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_common
    )

def graknlabs_client_java():
    git_repository(
        name = "graknlabs_client_java",
        remote = "https://github.com/alexjpwalker/client-java",
        commit = "46e46fe12eaf3d2454b28fe7838edd0fdc8e29e5",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @graknlabs_client_java
    )
