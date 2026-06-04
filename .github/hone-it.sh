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

count_classes() {
  local base=$1
  find "${base}" -type f -path '*/target/classes/*.class' | wc -l | tr -d ' '
}

count_streams() {
  local base=$1
  find "${base}" -type f -path '*/target/classes/*.class' \
    -exec grep -lE 'java/util/stream/(Int|Long|Double)?Stream' {} + 2>/dev/null \
    | wc -l | tr -d ' '
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
  local base=$1
  local -a stats=()
  while IFS= read -r -d '' f; do
    stats+=("${f}")
  done < <(find "${base}" -type f -path '*/target/hone-statistics.csv' -print0)
  if [ "${#stats[@]}" -eq 0 ]; then
    echo 0
    return
  fi
  awk -F',' 'FNR == 1 { next } $(NF-1) + 0 > 0 { modified += 1 } END { print modified + 0 }' "${stats[@]}"
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
echo "warming up and building ${repo}"
rc=0
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" test || rc=$?
streams=$(count_streams "${dir}" || true)
streams=${streams:-0}
echo "classes referencing java.util.stream in ${repo}: ${streams}"
outcome=$(build_outcome "${rc}")
if [ "${outcome}" != "pass" ]; then
  printf '%s,%s,0,0,0,0,skipped,0,%s,%s\n' "${row}" "${outcome}" "${loc}" "${streams}" >> "${csv}"
  rm -rf "${dir}"
  exit 1
fi
start=$(date +%s)
rc=0
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" initialize surefire:test || rc=$?
outcome=$(build_outcome "${rc}")
seconds=$(( $(date +%s) - start ))
row="${row},${outcome},${seconds}"

if [ "${outcome}" != "pass" ]; then
  printf '%s,0,0,0,skipped,0,%s,%s\n' "${row}" "${loc}" "${streams}" >> "${csv}"
  rm -rf "${dir}"
  exit 1
fi

total=$(count_classes "${dir}")
hstart=$(date +%s)
apply_hone "${dir}"
hone_seconds=$(( $(date +%s) - hstart ))
count=$(count_modified "${dir}")
row="${row},${total},${count},${hone_seconds}"
echo "warming up the test runner for honed ${repo}"
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" initialize surefire:test || true
start=$(date +%s)
rc=0
timeout "${budget}" mvn "${flags[@]}" -f "${dir}" initialize surefire:test || rc=$?
outcome=$(build_outcome "${rc}")
seconds=$(( $(date +%s) - start ))
printf '%s,%s,%s,%s,%s\n' "${row}" "${outcome}" "${seconds}" "${loc}" "${streams}" >> "${csv}"
rm -rf "${dir}"
test "${outcome}" = "pass"
