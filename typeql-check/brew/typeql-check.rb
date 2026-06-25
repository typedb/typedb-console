# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

class TypeqlCheck < Formula
  desc "Validate TypeQL query syntax from the command line"
  homepage "https://typedb.com"

  on_arm do
    url "https://repo.typedb.com/public/public-release/raw/names/typeql-check-mac-arm64/versions/{version}/typeql-check-mac-arm64-{version}.zip"
    sha256 "{sha256-arm64}"
  end

  on_intel do
    url "https://repo.typedb.com/public/public-release/raw/names/typeql-check-mac-x86_64/versions/{version}/typeql-check-mac-x86_64-{version}.zip"
    sha256 "{sha256-x86_64}"
  end

  license "MPL-2.0"

  def install
    bin.install "typeql-check"
  end
end
