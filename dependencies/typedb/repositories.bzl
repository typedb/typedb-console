# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
     # TODO: Return ref after merge to master, currently points to 'raft-dependencies-addition'
     git_repository(
         name = "typedb_dependencies",
         remote = "https://github.com/typedb/typedb-dependencies",
         commit = "a7f714591ac8da4e8a281ebd1542190346a56da1",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
     )

def typedb_driver():
    # TODO: Return typedb
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/farost/typedb-driver",
        commit = "d2ce9ab4f85f8316a9087c130a20c7b1e731e8c4",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )

def typeql():
    git_repository(
        name = "typeql",
        remote = "https://github.com/typedb/typeql",
        tag = "3.2.0",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
