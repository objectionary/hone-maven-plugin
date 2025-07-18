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
  mkdir -p "$(dirname "${from}/${f}")"
  mkdir -p "$(dirname "${to}/${f}")"
  mkdir -p "$(dirname "${xmirOut}/${f}")"
  phino rewrite --input=xmir --sweet --nothing "${xmirIn}/${f}.xmir" > "${from}/${f}.phi"
  echo "Converted XMIR ($(du -sh "${xmirIn}/${f}.xmir" | cut -f1)) to ${from}/${f}.phi ($(du -sh "${from}/${f}.phi" | cut -f1))"
  rm -f "${to}/${f}.phi.*"
  pos=0
  IFS=' ' read -r -a array <<< "${rules}"
  if [ "${smallSteps}" == "true" ]; then
    echo "Running in small-steps mode, applying ${#array[@]} rule(s) one by one to ${from}/${f}.phi"
    s="${to}/${f}.phi"
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
    phino rewrite --max-depth "${maxDepth}" --sweet "${opts[@]}" "${from}/${f}.phi" > "${to}/${f}.phi"
    if cmp -s "${from}/${f}.phi" "${to}/${f}.phi"; then
      echo "No changes made by ${#array[@]} rule(s) to ${to}/${f}.phi"
    else
      echo "All ${#array[@]} rule(s) made some changes to ${from}/${f}.phi, saved to ${to}/${f}.phi: $(diff "${from}/${f}.phi" "${to}/${f}.phi" | grep -cE '^[><]') lines"
    fi
  fi
  phino rewrite --nothing --output=xmir --omit-listing --omit-comments "${to}/${f}.phi" > "${xmirOut}/${f}.xmir"
  echo "Converted phi to ${xmirOut}/${f}.xmir ($(du -sh "${xmirOut}/${f}.xmir" | cut -f1))"
  if cmp -s "${xmirIn}/${f}.xmir" "${xmirOut}/${f}.xmir"; then
    echo "No changes made to ${f}.xmir"
  else
    echo "Some changes were made to ${f}.xmir: $(diff "${xmirIn}/${f}.xmir" "${xmirOut}/${f}.xmir" | grep -cE '^[><]') lines"
  fi
done < <(find "$(realpath "${xmirIn}")" -name '*.xmir' -type f -exec realpath --relative-to="${xmirIn}" {} \;)
