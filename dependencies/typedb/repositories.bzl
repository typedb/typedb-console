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
    # TODO: Return typedb after merges
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/farost/typedb-driver",
        commit = "f1f0ac3725436cd180534f95b000ad8c04e79dc9",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
