# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
    git_repository(
        name = "typedb_dependencies",
        remote = "https://github.com/typedb/typedb-dependencies",
        commit = "959bcdbfac995582812b334ba719b190367e4625", # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
    )

def typedb_driver():
    # TODO: Return typedb
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/farost/typedb-driver",
        commit = "be485ccb2f6c851791ce9c4a9b53d10dd5d41bb3",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
