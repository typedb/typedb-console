# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
    git_repository(
        name = "typedb_dependencies",
        remote = "https://github.com/typedb/typedb-dependencies",
        commit = "3348848e9455c1e4984a91c98436f77a8807717a", # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
    )

def typedb_driver():
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/typedb/typedb-driver",
        commit = "c0653ca0615f4075492f0b098960d291468ebd91",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
