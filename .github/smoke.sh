#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -u -o pipefail

repo="apache/commons-cli"

root=$(pwd)
work="${root}/target/smoke"
mkdir -p "${work}"

version=$(mvn -B -q -DforceStdout help:evaluate -Dexpression=project.version --batch-mode 2>/dev/null | tail -n 1)
test -n "${version}" || { echo "Failed to read project version from pom.xml" >&2; exit 1; }
echo "hone-maven-plugin version: ${version}"

mvn -B -q --batch-mode install -DskipTests -Dinvoker.skip
echo "hone-maven-plugin installed into local Maven repository"

name=$(basename "${repo}")
dir="${work}/${name}"
rm -rf "${dir}"
git clone --depth 1 "https://github.com/${repo}.git" "${dir}"

printf '\n=== first run: tests of %s without hone ===\n' "${repo}"
(cd "${dir}" && mvn -B --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip clean test) \
  || { echo "tests of ${repo} failed before hone was applied" >&2; exit 1; }

printf '\n=== applying hone to %s ===\n' "${repo}"
while IFS= read -r -d '' cdir; do
  module=$(dirname "$(dirname "${cdir}")")
  echo "applying hone in ${module}"
  (cd "${module}" && mvn -B --batch-mode \
    "org.eolang:hone-maven-plugin:${version}:build" \
    "org.eolang:hone-maven-plugin:${version}:optimize" \
    -Dhone.rules='streams/*')
done < <(find "${dir}" -type d -path '*/target/classes' -print0)

printf '\n=== second run: tests of %s after hone ===\n' "${repo}"
(cd "${dir}" && mvn -B --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip surefire:test) \
  || { echo "tests of ${repo} failed after hone was applied" >&2; exit 1; }

echo ""
echo "smoke test passed: ${repo} tests still pass after hone optimization"
