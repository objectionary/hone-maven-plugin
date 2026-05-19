#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -u -o pipefail

repos=(
  "apache/commons-cli"
  "apache/commons-csv"
  "apache/commons-codec"
  "apache/commons-text"
  "apache/commons-io"
  "apache/commons-validator"
  "apache/commons-net"
  "apache/commons-pool"
  "apache/commons-email"
  "jhy/jsoup"
)

root=$(pwd)
work="${root}/target/coverage"
csv="${work}/coverage.csv"
mkdir -p "${work}"

version=$(mvn -B -q -DforceStdout help:evaluate -Dexpression=project.version --batch-mode 2>/dev/null | tail -n 1)
test -n "${version}" || { echo "Failed to read project version from pom.xml" >&2; exit 1; }
echo "hone-maven-plugin version: ${version}"

if ! mvn -B -q --batch-mode install -DskipTests -Dinvoker.skip; then
  echo "Failed to install hone-maven-plugin into local Maven repository" >&2
  exit 1
fi
echo "hone-maven-plugin installed into local Maven repository"

printf 'repo;build_before;time_before;classes_modified;build_after;time_after\n' > "${csv}"

snapshot_classes() {
  local base=$1 out=$2
  (cd "${base}" && find . -type f -path '*/target/classes/*.class' -print0 \
    | xargs -0 md5sum 2>/dev/null \
    | sort > "${out}") || true
}

apply_hone() {
  local base=$1
  local rc=0
  local cdir module
  while IFS= read -r -d '' cdir; do
    module=$(dirname "$(dirname "${cdir}")")
    echo "applying hone in ${module}"
    if ! (cd "${module}" && mvn -B -q --batch-mode \
        "org.eolang:hone-maven-plugin:${version}:build" \
        "org.eolang:hone-maven-plugin:${version}:optimize" \
        -Dhone.rules='streams/*'); then
      echo "hone failed in ${module}" >&2
      rc=1
    fi
  done < <(find "${base}" -type d -path '*/target/classes' -print0)
  return "${rc}"
}

count_modified() {
  local before=$1 after=$2
  awk '/^[<>]/ {print $NF}' < <(diff "${before}" "${after}" 2>/dev/null) \
    | sort -u | wc -l | tr -d ' '
}

run_repo() {
  local repo=$1
  local name dir row start outcome seconds snap count hone
  name=$(basename "${repo}")
  dir="${work}/${name}"
  printf '\n=== %s ===\n' "${repo}"
  rm -rf "${dir}"
  if ! git clone --depth 1 "https://github.com/${repo}.git" "${dir}"; then
    printf '%s;clone_failed;0;0;skipped;0\n' "${repo}" >> "${csv}"
    return 0
  fi
  row="${repo}"
  start=$(date +%s)
  if (cd "${dir}" && mvn -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip clean test); then
    outcome="pass"
  else
    outcome="fail"
  fi
  seconds=$(( $(date +%s) - start ))
  row="${row};${outcome};${seconds}"
  if [ "${outcome}" != "pass" ]; then
    printf '%s;0;skipped;0\n' "${row}" >> "${csv}"
    return 0
  fi
  snap="${dir}/.snap"
  snapshot_classes "${dir}" "${snap}.before"
  hone="yes"
  apply_hone "${dir}" || hone="no"
  snapshot_classes "${dir}" "${snap}.after"
  count=0
  if [ -s "${snap}.before" ] && [ -s "${snap}.after" ]; then
    count=$(count_modified "${snap}.before" "${snap}.after")
  fi
  row="${row};${count}"
  if [ "${hone}" != "yes" ]; then
    printf '%s;hone_failed;0\n' "${row}" >> "${csv}"
    return 0
  fi
  start=$(date +%s)
  if (cd "${dir}" && mvn -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip surefire:test); then
    outcome="pass"
  else
    outcome="fail"
  fi
  seconds=$(( $(date +%s) - start ))
  printf '%s;%s;%s\n' "${row}" "${outcome}" "${seconds}" >> "${csv}"
  rm -rf "${dir}"
}

for repo in "${repos[@]}"; do
  run_repo "${repo}" || true
done

echo ""
echo "Final CSV:"
cat "${csv}"

table=$(
  printf '| Repository | Build Before | Time Before (s) | Classes Modified | Build After | Time After (s) |\n'
  printf '|---|---|---|---|---|---|\n'
  tail -n +2 "${csv}" | awk -F';' '{ printf "| [%s](https://github.com/%s) | %s | %s | %s | %s | %s |\n", $1, $1, $2, $3, $4, $5, $6 }'
)

sum=$(
  printf '%s\n\n' "${table}"
  printf 'The results were calculated in [this GHA job][coverage-gha]\n'
  printf 'on %s at %s,\n' "$(date +'%Y-%m-%d')" "$(date +'%H:%M')"
  printf 'on %s with %s CPUs.\n' "$(uname)" "$(nproc --all)"
)
export sum
perl -i -0777 -pe 's/(?<=<!-- coverage_begin -->).*(?=<!-- coverage_end -->)/\n$ENV{sum}\n/gs;' README.md

if [ -n "${GITHUB_RUN_ID:-}" ]; then
  url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
  export url
  perl -i -0777 -pe 's/(?<=\[coverage-gha\]: )[^\n]+(?=\n)/$ENV{url}/gs;' README.md
fi
