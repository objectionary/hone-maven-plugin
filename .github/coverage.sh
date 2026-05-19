#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -u -o pipefail

root=$(pwd)
repos="${root}/.github/coverage.csv"
test -f "${repos}" || { echo "Repo list ${repos} not found" >&2; exit 1; }
work="${root}/target/coverage"
csv="${work}/coverage.csv"
mkdir -p "${work}"

mvn -ntp -B -q --batch-mode install -DskipTests -Dinvoker.skip
echo "hone-maven-plugin installed into local Maven repository"

printf 'repo;sha;build_before;time_before;classes_modified;build_after;time_after\n' > "${csv}"

while IFS=';' read -r repo sha; do
  test -n "${repo}" || continue
  printf '\n=== %s @ %s ===\n' "${repo}" "${sha}"
  "${root}/.github/hone-it.sh" "${repo}" "${sha}" "${csv}" || true
done < <(tail -n +2 "${repos}")

echo ""
echo "Final CSV:"
cat "${csv}"

table=$(
  printf '| Repository | Build Before | Time Before (s) | Classes Modified | Build After | Time After (s) |\n'
  printf '|---|---|---|---|---|---|\n'
  tail -n +2 "${csv}" | awk -F';' '
    function badge(v) {
      if (v == "pass") return "👍🏻"
      if (v == "fail") return "⚠️"
      return v
    }
    {
      printf "| [%s](https://github.com/%s/commit/%s) | %s | %s | %s | %s | %s |\n", $1, $1, $2, badge($3), $4, $5, badge($6), $7
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
