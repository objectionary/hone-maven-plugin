#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -u -o pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 <owner/repo> <sha>" >&2
  exit 1
fi

repo=$1
sha=$2

root=$(pwd)
csv="${root}/target/smoke.csv"
mkdir -p "$(dirname "${csv}")"
printf 'repo;sha;build_before;time_before;classes_modified;build_after;time_after\n' > "${csv}"

mvn -ntp -B -q --batch-mode install -DskipTests -Dinvoker.skip
echo "hone-maven-plugin installed into local Maven repository"

"${root}/.github/hone-it.sh" "${repo}" "${sha}" "${csv}"

cat "${csv}"
