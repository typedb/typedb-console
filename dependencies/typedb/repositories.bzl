# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
    git_repository(
        name = "typedb_dependencies",
        remote = "https://github.com/typedb/typedb-dependencies",
        commit = "fac1121c903b0c9e5924d391a883e4a0749a82a2", # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
    )

def typedb_driver():
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/typedb/typedb-driver",
        commit = "3b171274a42751376b2a480baa40315ebbf80fce",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )

def typeql():
    git_repository(
        name = "typeql",
        remote = "https://github.com/typedb/typeql",
        tag = "3.2.0",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
