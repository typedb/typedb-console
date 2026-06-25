#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

./tool/release/create_notes_console.sh
./tool/release/create_notes_loader.sh
./tool/release/create_notes_typeql_check.sh
