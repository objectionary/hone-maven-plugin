#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT
set -e -o pipefail
version=$(xargs < src/main/resources/org/eolang/hone/default-phino-version.txt)
expected=$(grep -v '^#' .github/phino-sha512.txt | xargs)
if command -v phino > /dev/null 2>&1 && phino --pin="${version}" --version > /dev/null 2>&1; then
  echo "phino ${version} is already installed"
  exit 0
fi
# The ubuntu-24.04 leg of phino's release workflow has been failing since
# 0.0.116, so no binary is published for that platform and the download
# returns 403 (see objectionary/phino#1141). The ubuntu-22.04 build of the
# same tag runs fine on 24.04 runners, so we pull that one until upstream
# publishes 24.04 again.
url="http://phino.objectionary.com/releases/ubuntu-22.04/phino-${version}"
tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT
curl --silent --show-error --fail --location --output "${tmp}" "${url}"
echo "${expected}  ${tmp}" | sha512sum --check --strict
sudo mv "${tmp}" /usr/bin/phino
sudo chmod a+x /usr/bin/phino
phino --pin="${version}" --version