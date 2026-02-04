# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
    git_repository(
        name = "typedb_dependencies",
        remote = "https://github.com/typedb/typedb-dependencies",
        commit = "f6e710f9857b1c30ad1764c1c41afce4e4e02981", # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
    )

def typedb_driver():
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/typedb/typedb-driver",
        tag = "3.8.0",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )

def typeql():
    git_repository(
        name = "typeql",
        remote = "https://github.com/typedb/typeql",
        tag = "3.8.0",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
