#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -u -o pipefail

if [ $# -ne 3 ]; then
  echo "usage: $0 <owner/repo> <sha> <csv>" >&2
  exit 1
fi

repo=$1
sha=$2
csv=$3

root=$(pwd)
work="${root}/target/hone-it"
mkdir -p "${work}"

version=$(mvn -ntp -B -q -DforceStdout help:evaluate -Dexpression=project.version --batch-mode 2>/dev/null | tail -n 1)
test -n "${version}" || { echo "Failed to read project version from pom.xml" >&2; exit 1; }

name=$(basename "${repo}")
dir="${work}/${name}"
rm -rf "${dir}"
mkdir -p "${dir}"
git -C "${dir}" init -q
git -C "${dir}" remote add origin "https://github.com/${repo}.git"
git -C "${dir}" fetch --depth 1 -q origin "${sha}"
git -C "${dir}" checkout -q FETCH_HEAD
echo "checked out ${repo} at ${sha}"

loc=$(cloc --quiet --csv --include-lang=Java "${dir}" 2>/dev/null | awk -F',' '$2=="Java"{print $5; exit}')
loc=${loc:-0}
echo "Java LoC in ${repo}: ${loc}"

snapshot_classes() {
  local base=$1 out=$2
  (cd "${base}" && find . -type f -path '*/target/classes/*.class' -exec md5sum {} + | sort > "${out}")
}

apply_hone() {
  local base=$1
  local cdir module
  while IFS= read -r -d '' cdir; do
    module=$(dirname "$(dirname "${cdir}")")
    echo "applying hone in ${module}"
    mvn -ntp -B -q --batch-mode -f "${module}" \
      "org.eolang:hone-maven-plugin:${version}:build" \
      "org.eolang:hone-maven-plugin:${version}:optimize" \
      -Dhone.rules='streams/*'
  done < <(find "${base}" -type d -path '*/target/classes' -print0)
}

count_modified() {
  local before=$1 after=$2
  { diff "${before}" "${after}" || true; } \
    | awk '/^[<>]/ {print $NF}' \
    | sort -u | wc -l | tr -d ' '
}

build_outcome() {
  local rc=$1
  if [ "${rc}" -eq 0 ]; then
    echo "pass"
  elif [ "${rc}" -eq 124 ] || [ "${rc}" -eq 137 ]; then
    echo "timeout"
  else
    echo "fail"
  fi
}

budget=1200

flags=(-ntp -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip)

row="${repo},${sha}"
echo "warming up Maven dependency cache for ${repo}"
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" test || true
start=$(date +%s)
rc=0
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" clean test || rc=$?
outcome=$(build_outcome "${rc}")
seconds=$(( $(date +%s) - start ))
row="${row},${outcome},${seconds}"

if [ "${outcome}" != "pass" ]; then
  printf '%s,0,0,skipped,0,%s\n' "${row}" "${loc}" >> "${csv}"
  rm -rf "${dir}"
  exit 1
fi

snap="${dir}/.snap"
snapshot_classes "${dir}" "${snap}.before"
total=$(wc -l < "${snap}.before" | tr -d ' ')
apply_hone "${dir}"
snapshot_classes "${dir}" "${snap}.after"
count=$(count_modified "${snap}.before" "${snap}.after")
row="${row},${total},${count}"
start=$(date +%s)
rc=0
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" initialize surefire:test || rc=$?
outcome=$(build_outcome "${rc}")
seconds=$(( $(date +%s) - start ))
printf '%s,%s,%s,%s\n' "${row}" "${outcome}" "${seconds}" "${loc}" >> "${csv}"
rm -rf "${dir}"
test "${outcome}" = "pass"
