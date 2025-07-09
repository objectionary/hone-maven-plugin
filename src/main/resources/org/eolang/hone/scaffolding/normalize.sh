#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex -o pipefail

rules=$1
xmirIn=$2
from=$3
to=$4
xmirOut=$5

if [ ! -d "${xmirIn}" ]; then
  echo "The source directory '${xmirIn}' does not exist!"
  exit 1
fi

mkdir -p "${from}"
mkdir -p "${to}"
mkdir -p "${xmirOut}"

opts=()
IFS=' ' read -r -a array <<< "${rules}"
for rule in "${array[@]}"; do
  opts+=(--rule "${rule}")
done

while IFS= read -r f; do
  f=${f%.*}
  mkdir -p "$(dirname "${from}/${f}")"
  mkdir -p "$(dirname "${to}/${f}")"
  mkdir -p "$(dirname "${xmirOut}/${f}")"
  phino rewrite --input=xmir --sweet --nothing < "${xmirIn}/${f}.xmir" > "${from}/${f}.phi"
  phino rewrite --sweet "${opts[@]}" < "${from}/${f}.phi" > "${to}/${f}.phi"
  phino rewrite --nothing --output=xmir --omit-listing < "${to}/${f}.phi" > "${xmirOut}/${f}.xmir"
done < <(find "$(realpath "${xmirIn}")" -name '*.xmir' -type f -exec realpath --relative-to="${xmirIn}" {} \;)
