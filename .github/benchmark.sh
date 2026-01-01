#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

git fetch --tags --force
latest=$(git tag --sort=creatordate | tail -1)
docker pull "yegor256/hone:${latest}"
sum=$(
  printf "\`\`\`text\n"
  cat target/jna-summary.txt
  printf "\n\n"
  tail -n +2 < target/timings.csv | tr -d '"' | cut -d ';' -f2,3 | sed 's/\(default-cli\)//g' | tr -d ';' | awk '{ a[$1]+=$2; s+=$2; } END { for (k in a) printf("%s\t%s\t%0.2f%%\n", k, a[k], 100 * a[k] / s)}' | sort -g -k 2 | tac | column -t
  printf "\`\`\`\n\n"
  echo "The results were calculated in [this GHA job][benchmark-gha]"
  echo "on $(date +'%Y-%m-%d') at $(date +'%H:%M'),"
  echo "on $(uname) with $(nproc --all) CPUs."
)
export sum
perl -i -0777 -pe 's/(?<=<!-- benchmark_begin -->).*(?=<!-- benchmark_end -->)/\n$ENV{sum}\n/gs;' README.md
url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
export url
perl -i -0777 -pe 's/(?<=\[benchmark-gha\]: )[^\n]+(?=\n)/$ENV{url}/gs;' README.md
