#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -u -o pipefail

repo="apache/commons-cli"
sha="17de58009bf9dada031a7b3891014c6de5a089bf"

root=$(pwd)
work="${root}/target/smoke"
mkdir -p "${work}"

version=$(mvn -ntp -B -q -DforceStdout help:evaluate -Dexpression=project.version --batch-mode 2>/dev/null | tail -n 1)
echo "hone-maven-plugin version: ${version}"

mvn -ntp -B -q --batch-mode install -DskipTests -Dinvoker.skip

name=$(basename "${repo}")
dir="${work}/${name}"
rm -rf "${dir}"
git init -q "${dir}"
git -C "${dir}" remote add origin "https://github.com/${repo}.git"
git -C "${dir}" fetch --depth 1 origin "${sha}"
git -C "${dir}" checkout -q FETCH_HEAD
echo "checked out ${repo} at ${sha}"

mvn_flags=(-ntp -B --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip)

mvn -f "${dir}" "${mvn_flags[@]}" clean test

while IFS= read -r -d '' cdir; do
  module=$(dirname "$(dirname "${cdir}")")
  mvn -f "${module}" -B --batch-mode \
    "org.eolang:hone-maven-plugin:${version}:build" \
    "org.eolang:hone-maven-plugin:${version}:optimize" \
    -Dhone.rules='streams/*'
done < <(find "${dir}" -type d -path '*/target/classes' -print0)

mvn -f "${dir}" "${mvn_flags[@]}" surefire:test
