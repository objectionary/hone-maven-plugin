#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

rules=$1
xmirIn=$2
from=$3
to=$4
xmirOut=$5
smallSteps=$6
maxDepth=$7

if [ ! -d "${xmirIn}" ]; then
  echo "The source directory '${xmirIn}' does not exist!"
  exit 1
fi

mkdir -p "${from}"
mkdir -p "${to}"
mkdir -p "${xmirOut}"

echo "Phino version: $(phino --version | xargs)"

while IFS= read -r f; do
  f=${f%.*}
  r="${from}/${f}.phi"
  s="${to}/${f}.phi"
  xo=${xmirOut}/${f}.xmir
  xi=${xmirIn}/${f}.xmir
  mkdir -p "$(dirname "${r}")"
  mkdir -p "$(dirname "${s}")"
  mkdir -p "$(dirname "${xmirOut}/${f}")"
  phino rewrite --input=xmir --sweet --nothing "${xi}" > "${r}"
  echo "Converted XMIR ($(du -sh "${xi}" | cut -f1)) to $(basename "${r}") ($(du -sh "${r}" | cut -f1))"
  rm -f "${s}.*"
  pos=0
  IFS=' ' read -r -a array <<< "${rules}"
  if [ "${smallSteps}" == "true" ]; then
    echo "Running in small-steps mode, applying ${#array[@]} rule(s) one by one to ${r}"
    cp "${from}/${f}.phi" "${s}"
    for rule in "${array[@]}"; do
      m=$(basename "${rule}" .yml)
      pos=$(( pos + 1 ))
      t="${to}/${f}.phi.${pos}"
      phino rewrite --max-depth "${maxDepth}" --sweet --rule "${rule}" "${s}" > "${t}"
      if cmp -s "${s}" "${t}"; then
        echo ".. No changes made by '${m}' to $(basename "${t}")"
      else
        echo ".. $(diff "${s}" "${f}" | grep -cE '^[><]') lines changed by '${m}' to $(basename "${t}")"
      fi
      cp "${t}" "${s}"
    done
  else
    opts=()
    for rule in "${array[@]}"; do
      opts+=("--rule=${rule}")
    done
    phino rewrite --max-depth "${maxDepth}" --sweet "${opts[@]}" "${r}" > "${s}"
    if cmp -s "${r}" "${s}"; then
      echo "No changes made by ${#array[@]} rule(s) to $(basename "${s}")"
    else
      echo "All ${#array[@]} rule(s) made some changes to $(basename "${r}"), saved to $(basename "${s}"): $(diff "${r}" "${s}" | grep -cE '^[><]') lines"
    fi
  fi
  phino rewrite --nothing --output=xmir --omit-listing --omit-comments "${s}" > "${xo}"
  echo "Converted phi to $(basename "${xo}") ($(du -sh "${xo}" | cut -f1))"
  if cmp -s "${xi}" "${xo}"; then
    echo "No changes made to $(basename "${xi}")"
  else
    echo "Some changes were made to $(basename "${xi}"): $(diff "${xi}" "${xo}" | grep -cE '^[><]') lines"
  fi
done < <(find "$(realpath "${xmirIn}")" -name '*.xmir' -type f -exec realpath --relative-to="${xmirIn}" {} \;)
