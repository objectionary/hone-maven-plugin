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

mvn -B -q --batch-mode install -DskipTests -Dinvoker.skip
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
  (cd "${base}" && find . -type d -path '*/target/classes' -print0 \
    | while IFS= read -r -d '' cdir; do
    module=$(dirname "$(dirname "${cdir}")")
    echo "applying hone in ${module}"
    if ! (cd "${module}" && mvn -B -q --batch-mode \
        "org.eolang:hone-maven-plugin:${version}:build" \
        "org.eolang:hone-maven-plugin:${version}:optimize" \
        -Dhone.rules='streams/*'); then
      echo "hone failed in ${module}" >&2
      rc=1
    fi
  done) || rc=1
  return "${rc}"
}

count_modified() {
  local before=$1 after=$2
  awk '/^[<>]/ {print $NF}' < <(diff "${before}" "${after}" 2>/dev/null) \
    | sort -u | wc -l | tr -d ' '
}

run_repo() {
  local repo=$1
  local name
  name=$(basename "${repo}")
  local repo_dir="${work}/${name}"
  printf '\n=== %s ===\n' "${repo}"
  rm -rf "${repo_dir}"
  if ! git clone --depth 1 "https://github.com/${repo}.git" "${repo_dir}"; then
    printf '%s;clone_failed;0;0;skipped;0\n' "${repo}" >> "${csv}"
    return 0
  fi
  local before_start before_end time_before build_before
  before_start=$(date +%s)
  if (cd "${repo_dir}" && mvn -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip clean test); then
    build_before="pass"
  else
    build_before="fail"
  fi
  before_end=$(date +%s)
  time_before=$((before_end - before_start))
  if [ "${build_before}" != "pass" ]; then
    printf '%s;fail;%s;0;skipped;0\n' "${repo}" "${time_before}" >> "${csv}"
    return 0
  fi
  local snap_before="${repo_dir}/.cov-before" snap_after="${repo_dir}/.cov-after"
  snapshot_classes "${repo_dir}" "${snap_before}"
  local hone_ok="yes"
  apply_hone "${repo_dir}" || hone_ok="no"
  snapshot_classes "${repo_dir}" "${snap_after}"
  local modified=0
  if [ -s "${snap_before}" ] && [ -s "${snap_after}" ]; then
    modified=$(count_modified "${snap_before}" "${snap_after}")
  fi
  local after_start after_end time_after build_after
  after_start=$(date +%s)
  if [ "${hone_ok}" != "yes" ]; then
    build_after="hone_failed"
    time_after=0
  else
    if (cd "${repo_dir}" && mvn -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip surefire:test); then
      build_after="pass"
    else
      build_after="fail"
    fi
    after_end=$(date +%s)
    time_after=$((after_end - after_start))
  fi
  printf '%s;%s;%s;%s;%s;%s\n' "${repo}" "${build_before}" "${time_before}" "${modified}" "${build_after}" "${time_after}" >> "${csv}"
  rm -rf "${repo_dir}"
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
  tail -n +2 "${csv}" | while IFS=';' read -r row_repo before_status before_time changed after_status after_time; do
    printf '| [%s](https://github.com/%s) | %s | %s | %s | %s | %s |\n' \
      "${row_repo}" "${row_repo}" "${before_status}" "${before_time}" "${changed}" "${after_status}" "${after_time}"
  done
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
