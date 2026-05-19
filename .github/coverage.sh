#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -u -o pipefail

repos=(
  "apache/commons-cli@17de58009bf9dada031a7b3891014c6de5a089bf"
  "apache/commons-csv@6f93c7edfa0f758f757227b1d30588411fdbf669"
  "apache/commons-codec@77fcf89711a0e20393105a1247c41968f6eb58d4"
  "apache/commons-text@283eaf49586331a7adc0b28fdfa5e8f09df87123"
  "apache/commons-io@5ea874ee07d081a69e4ff2971d3d8fc8cace9179"
  "apache/commons-validator@a3e7627ffd1e633e93b45749650c5a6262da8e6c"
  "apache/commons-net@ab2b19fee5e76984843fce6481f0c7724f629920"
  "apache/commons-pool@6b9c542ac9746eeec00a497712e41b014d51fdb3"
  "apache/commons-email@c300a6d66dcd5708b6bb5d1a1eb5f9f99f4d9090"
  "jhy/jsoup@a7ec14364e2f9f84ecb795814b4fd05d028f709d"
)

root=$(pwd)
work="${root}/target/coverage"
csv="${work}/coverage.csv"
mkdir -p "${work}"

version=$(mvn -ntp -B -q -DforceStdout help:evaluate -Dexpression=project.version --batch-mode 2>/dev/null | tail -n 1)
test -n "${version}" || { echo "Failed to read project version from pom.xml" >&2; exit 1; }
echo "hone-maven-plugin version: ${version}"

mvn -ntp -B -q --batch-mode install -DskipTests -Dinvoker.skip
echo "hone-maven-plugin installed into local Maven repository"

printf 'repo;sha;build_before;time_before;classes_modified;build_after;time_after\n' > "${csv}"

shallow_checkout() {
  local repo=$1 sha=$2 dir=$3
  mkdir -p "${dir}"
  git -C "${dir}" init -q
  git -C "${dir}" remote add origin "https://github.com/${repo}.git"
  git -C "${dir}" fetch --depth 1 -q origin "${sha}"
  git -C "${dir}" checkout -q FETCH_HEAD
}

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
    (cd "${module}" && mvn -ntp -B -q --batch-mode \
      "org.eolang:hone-maven-plugin:${version}:build" \
      "org.eolang:hone-maven-plugin:${version}:optimize" \
      -Dhone.rules='streams/*')
  done < <(find "${base}" -type d -path '*/target/classes' -print0)
}

count_modified() {
  local before=$1 after=$2
  { diff "${before}" "${after}" || true; } \
    | awk '/^[<>]/ {print $NF}' \
    | sort -u | wc -l | tr -d ' '
}

run_repo() {
  local repo=$1 sha=$2
  local name dir row start outcome seconds snap count
  name=$(basename "${repo}")
  dir="${work}/${name}"
  printf '\n=== %s @ %s ===\n' "${repo}" "${sha}"
  rm -rf "${dir}"
  shallow_checkout "${repo}" "${sha}" "${dir}"
  row="${repo};${sha}"
  start=$(date +%s)
  if (cd "${dir}" && mvn -ntp -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip clean test); then
    outcome="pass"
  else
    outcome="fail"
  fi
  seconds=$(( $(date +%s) - start ))
  row="${row};${outcome};${seconds}"
  if [ "${outcome}" != "pass" ]; then
    printf '%s;0;skipped;0\n' "${row}" >> "${csv}"
    rm -rf "${dir}"
    return 0
  fi
  snap="${dir}/.snap"
  snapshot_classes "${dir}" "${snap}.before"
  apply_hone "${dir}"
  snapshot_classes "${dir}" "${snap}.after"
  count=$(count_modified "${snap}.before" "${snap}.after")
  row="${row};${count}"
  start=$(date +%s)
  if (cd "${dir}" && mvn -ntp -B -q --batch-mode -Dlicense.skip -Drat.skip -Dspotbugs.skip -Dcheckstyle.skip -Dpmd.skip -Denforcer.skip surefire:test); then
    outcome="pass"
  else
    outcome="fail"
  fi
  seconds=$(( $(date +%s) - start ))
  printf '%s;%s;%s\n' "${row}" "${outcome}" "${seconds}" >> "${csv}"
  rm -rf "${dir}"
}

for entry in "${repos[@]}"; do
  run_repo "${entry%@*}" "${entry#*@}"
done

echo ""
echo "Final CSV:"
cat "${csv}"

table=$(
  printf '| Repository | Build Before | Time Before (s) | Classes Modified | Build After | Time After (s) |\n'
  printf '|---|---|---|---|---|---|\n'
  tail -n +2 "${csv}" | awk -F';' '{
    short = substr($2, 1, 7)
    printf "| [%s@%s](https://github.com/%s/commit/%s) | %s | %s | %s | %s | %s |\n", $1, short, $1, $2, $3, $4, $5, $6, $7
  }'
)

cpus=$(nproc --all 2>/dev/null || echo "?")
sum=$(
  printf '%s\n\n' "${table}"
  printf 'The results were calculated in [this GHA job][coverage-gha]\n'
  printf 'on %s at %s,\n' "$(date +'%Y-%m-%d')" "$(date +'%H:%M')"
  printf 'on %s with %s CPUs.\n' "$(uname)" "${cpus}"
)
export sum
perl -i -0777 -pe 's/(?<=<!-- coverage_begin -->).*(?=<!-- coverage_end -->)/\n$ENV{sum}\n/gs;' README.md

if [ -n "${GITHUB_RUN_ID:-}" ]; then
  url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
  export url
  perl -i -0777 -pe 's/(?<=\[coverage-gha\]: )[^\n]+(?=\n)/$ENV{url}/gs;' README.md
fi
