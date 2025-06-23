#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex -o pipefail

rules=$1
from=$2
to=$3
xmir=$4

if [ ! -d "${from}" ]; then
  echo "The source directory '${from}' does not exist!"
  exit 1
fi

mkdir -p "${to}"
mkdir -p "${xmir}"

while IFS= read -r f; do
  mkdir -p "$(dirname "${to}/${f}")"
  for rule in ${rules}; do
    phino rewrite --rule "${rule}" < "${from}/${f}" > "${to}/${f}"
    phino rewrite --nothing --output=xmir < "${to}/${f}" > "${xmir}/${f}"
  done
done < <(find "$(realpath "${from}")" -name '*.phi' -type f -exec realpath --relative-to="${from}" {} \;)
