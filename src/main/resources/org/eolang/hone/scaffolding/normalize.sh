#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

rules=$1
xmirIn=$2
from=$3
to=$4
xmirOut=$5

if [ ! -d "${xmirIn}" ]; then
  echo "The source directory '${xmirIn}' does not exist!"
  exit 1
fi

mkdir -p "${from}"
mkdir -p "${to}"
mkdir -p "${xmirOut}"

phino --version

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
  cp "${from}/${f}.phi" "${to}/${f}.phi"
  for rule in "${array[@]}"; do
    pos=$(( pos + 1 ))
    phino rewrite --sweet --rule "${rule}" "${to}/${f}.phi" > "${to}/${f}.phi.${pos}"
    echo "Applied '${rule}', saved to ${to}/${f}.phi.${pos}"
    if diff -q "${to}/${f}.phi" "${to}/${f}.phi.${pos}"; then
      echo "No changes made by '${rule}'"
    else
      echo "$(diff "${to}/${f}.phi" "${to}/${f}.phi.${pos}" | grep -E '^[+-]' | grep -cvE '^\+\+\+|^---') lines changed by '${rule}'"
    fi
    cp "${to}/${f}.phi.${pos}" "${to}/${f}.phi"
  done
  phino rewrite --nothing --output=xmir --omit-listing --omit-comments "${to}/${f}.phi" > "${xmirOut}/${f}.xmir"
  echo "Converted phi to ${xmirOut}/${f}.xmir ($(du -sh "${xmirOut}/${f}.xmir" | cut -f1))"
  if diff -q "${xmirIn}/${f}.xmir" "${xmirOut}/${f}.xmir"; then
    echo "No changes made to ${f}.xmir"
  else
    echo "Some changes were made to ${f}.xmir"
  fi
done < <(find "$(realpath "${xmirIn}")" -name '*.xmir' -type f -exec realpath --relative-to="${xmirIn}" {} \;)
