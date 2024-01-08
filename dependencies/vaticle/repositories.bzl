#
# Copyright (C) 2022 Vaticle
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

def vaticle_dependencies():
    git_repository(
        name = "vaticle_dependencies",
        remote = "https://github.com/krishnangovindraj/dependencies",
        commit = "89cbbc4e9807b10835591559bd77bd6a9a754a8a", # sync-marker: do not remove this comment, this is used for sync-dependencies by @vaticle_dependencies

    )

def vaticle_typedb_common():
    git_repository(
        name = "vaticle_typedb_common",
        remote = "https://github.com/krishnangovindraj/typedb-common",
        commit = "fd9ec61f9cc8bd0ae7a70097257d3191731316e9",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @vaticle_typedb_common
    )

def vaticle_typedb_driver():
    git_repository(
        name = "vaticle_typedb_driver",
        remote = "https://github.com/krishnangovindraj/typedb-client-java", # typedb-driver",
        commit = "c66084ff31d06beb17bd9501f404bf3b1be0429b",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @vaticle_typedb_driver
    )
