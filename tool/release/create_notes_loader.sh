#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

bazel run @typedb_dependencies//tool/release/notes:create -- typedb typedb-console HEAD $(cat VERSION) ./loader/RELEASE_TEMPLATE.md ./loader/RELEASE_NOTES_LATEST.md \
  --include loader --exclude console --exclude typeql-check --tag-prefix loader-
