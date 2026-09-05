#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT
set -e -o pipefail
version=$(xargs < src/main/resources/org/eolang/hone/default-phino-version.txt)
expected=$(xargs < .github/phino-sha512.txt)
if command -v phino > /dev/null 2>&1 && phino --pin="${version}" --version > /dev/null 2>&1; then
  echo "phino ${version} is already installed"
  exit 0
fi
url="http://phino.objectionary.com/releases/ubuntu-24.04/phino-${version}"
tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT
curl --silent --show-error --fail --location --output "${tmp}" "${url}"
echo "${expected}  ${tmp}" | sha512sum --check --strict
sudo mv "${tmp}" /usr/bin/phino
sudo chmod a+x /usr/bin/phino
phino --pin="${version}" --version