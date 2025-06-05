#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex -o pipefail

rules=$1
from=$2
to=$3

phino --version

mkdir -p "${to}"
while IFS= read -r f; do
  for rule in ${rules}; do
    phino rewrite --rule "${rule}" "${from}/${f}" --single -o "${to}/${f}"
  done
done < <(find "$(realpath "${from}")" -name '*.phi' -type f -exec realpath --relative-to="${from}" {} \;)
