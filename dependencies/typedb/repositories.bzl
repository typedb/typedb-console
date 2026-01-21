# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
     # TODO: Return ref after merge to master
     git_repository(
         name = "typedb_dependencies",
         remote = "https://github.com/typedb/typedb-dependencies",
         commit = "7453ed5a7ef08a824285f87062896df69ba60886",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
     )

def typedb_driver():
     # TODO: Return ref after merge to master
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/typedb/typedb-driver",
        commit = "78283bf9dc8603aa60294311de1dd147c5a9f765",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )

def typeql():
    git_repository(
        name = "typeql",
        remote = "https://github.com/typedb/typeql",
        tag = "3.7.0",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
