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

printf 'repo,sha,build_before,time_before,classes_total,classes_modified,time_hone,build_after,time_after,loc,streams\n' > "${csv}"

while IFS=',' read -r -u 3 repo sha; do
  test -n "${repo}" || continue
  printf '\n=== %s @ %s ===\n' "${repo}" "${sha}"
  "${root}/.github/hone-it.sh" "${repo}" "${sha}" "${csv}" </dev/null || true
done 3< <(tail -n +2 "${repos}")

echo ""
echo "Final CSV:"
cat "${csv}"

table=$(
  printf '| Repository | Forks | LoC | Streams | Classes | Before | Edits | Hone | After |\n'
  printf '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n'
  while IFS=',' read -r repo sha; do
    test -n "${repo}" || continue
    forks=$(gh api "repos/${repo}" --jq '.forks_count' 2>/dev/null || echo '?')
    row=$(grep -F "${repo},${sha}," "${csv}" || true)
    if [ -n "${row}" ]; then
      IFS=',' read -r _ _ bbuild btime total modified htime abuild atime loc streams <<< "${row}"
      bmark=""
      amark=""
      [ "${bbuild}" = "pass" ] || bmark=" ⚠️"
      [ "${abuild}" = "pass" ] || amark=" ⚠️"
      printf '| [%s](https://github.com/%s/commit/%s) | %s | %s | %s | %s | %ss%s | %s | %ss | %ss%s |\n' \
        "${repo}" "${repo}" "${sha}" "${forks}" "${loc}" "${streams}" "${total}" "${btime}" "${bmark}" "${modified}" "${htime}" "${atime}" "${amark}"
    else
      printf '| [%s](https://github.com/%s/commit/%s) | %s | ? | ? | ? | ? ⚠️ | ? | ? | ? ⚠️ |\n' \
        "${repo}" "${repo}" "${sha}" "${forks}"
    fi
  done < <(tail -n +2 "${repos}")
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
