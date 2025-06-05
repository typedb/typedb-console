# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

def typedb_dependencies():
    # TODO: Return typedb
    git_repository(
        name = "typedb_dependencies",
        remote = "https://github.com/farost/typedb-dependencies",
        commit = "b198f8d11a0b437ee9f9cf1e4eead8bedcdb7312", # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_dependencies
    )

def typedb_driver():
    # TODO: Return typedb
    git_repository(
        name = "typedb_driver",
        remote = "https://github.com/farost/typedb-driver",
        commit = "e3b3ab1df21c7bbeef6b3bfd9b1a040343fbb54a",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )

def typeql():
    git_repository(
        name = "typeql",
        remote = "https://github.com/typedb/typeql",
        tag = "3.2.0",  # sync-marker: do not remove this comment, this is used for sync-dependencies by @typedb_driver
    )
