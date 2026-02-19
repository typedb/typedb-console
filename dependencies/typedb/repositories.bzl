# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
     # TODO: Return ref after merge to master
     git_repository(
         name = "typedb_dependencies",
         remote = "https://github.com/typedb/typedb-dependencies",
         commit = "efacbdb7cc0731714586951ff83a19efa27b6e3e",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
     )

def typedb_driver():
     # TODO: Return ref after merge to master
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/typedb/typedb-driver",
        tag = "3.8.0-alpha-1",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )

def typeql():
    git_repository(
        name = "typeql",
        remote = "https://github.com/typedb/typeql",
        commit = "bff6f17c22650f7f540100180b869c88b2f82270",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
