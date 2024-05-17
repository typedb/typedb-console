# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def vaticle_dependencies():
    git_repository(
        name = "vaticle_dependencies",
        remote = "https://github.com/vaticle/dependencies",
        commit = "0a54a3551ef149346832d65a9c844429fff76f12", # sync-marker: do not remove this comment, this is used for sync-dependencies by @vaticle_dependencies
    )

def vaticle_typedb_driver():
    git_repository(
        name = "vaticle_typedb_driver",
        remote = "https://github.com/vaticle/typedb-driver",
        commit = "a7227fa9d22b97314a943b535abc1a6330f9a71c",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @vaticle_typedb_driver
    )
