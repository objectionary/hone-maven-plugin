#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

if [ ! -d "${HONE_XMIR_IN}" ]; then
  echo "The source directory '${HONE_XMIR_IN}' does not exist!"
  exit 1
fi

if [ -z "${HONE_RULES}" ]; then
  echo "No rules specified in HONE_RULES environment variable!"
  exit 1
fi

function verbose {
  if [ "${HONE_VERBOSE}" == 'true' ]; then
    echo "$@"
  fi
}

verbose "We are in verbose mode, printing all messages..."

mkdir -p "${HONE_FROM}"
mkdir -p "${HONE_TO}"
mkdir -p "${HONE_XMIR_OUT}"

echo "Phino version: $(phino --version | xargs)"

echo "Using ${#array[@]} rule(s)"

while IFS= read -r f; do
  f=${f%.*}
  r="${HONE_FROM}/${f}.phi"
  s="${HONE_TO}/${f}.phi"
  xo=${HONE_XMIR_OUT}/${f}.xmir
  xi=${HONE_XMIR_IN}/${f}.xmir
  mkdir -p "$(dirname "${r}")"
  mkdir -p "$(dirname "${s}")"
  mkdir -p "$(dirname "${HONE_XMIR_OUT}/${f}")"
  phino rewrite --input=xmir --sweet --nothing "${xi}" > "${r}"
  verbose "Converted XMIR ($(du -sh "${xi}" | cut -f1)) to $(basename "${r}") ($(du -sh "${r}" | cut -f1))"
  rm -f "${s}.*"
  pos=0
  IFS=' ' read -r -a array <<< "${HONE_RULES}"
  if [ "${HONE_SMALL_STEPS}" == "true" ]; then
    verbose "Applying ${#array[@]} rule(s) one by one to $(basename "${r}")..."
    cp "${HONE_FROM}/${f}.phi" "${s}"
    for rule in "${array[@]}"; do
      m=$(basename "${rule}" .yml)
      pos=$(( pos + 1 ))
      t="${HONE_TO}/${f}.phi.${pos}"
      phino rewrite --max-depth "${HONE_MAX_DEPTH}" --sweet --rule "${rule}" "${s}" > "${t}"
      if cmp -s "${s}" "${t}"; then
        verbose "  No changes made by '${m}' to $(basename "${t}")"
      else
        verbose "  $(diff "${s}" "${t}" | grep -cE '^[><]') lines changed by '${m}' to $(basename "${t}")"
      fi
      cp "${t}" "${s}"
    done
  else
    opts=()
    for rule in "${array[@]}"; do
      opts+=("--rule=${rule}")
    done
    phino rewrite --max-depth "${HONE_MAX_DEPTH}" --sweet "${opts[@]}" "${r}" > "${s}"
  fi
  s_size=$(du -sh "${xi}" | cut -f1)
  s_lines=$(wc -l < "${s}")
  if cmp -s "${r}" "${s}"; then
    echo "No changes made to $(basename "${s}") (${s_size}, ${s_lines} lines)"
  else
    echo "Modified $(basename "${r}") saved to $(basename "${s}") (${s_size}): $(diff "${r}" "${s}" | grep -cE '^[><]')/${s_lines} lines changed"
  fi
  phino rewrite --nothing --output=xmir --omit-listing --omit-comments "${s}" > "${xo}"
  verbose "Converted phi to $(basename "${xo}") ($(du -sh "${xo}" | cut -f1))"
  if cmp -s "${xi}" "${xo}"; then
    verbose "No changes made to $(basename "${xi}")"
  else
    verbose "Some changes made to $(basename "${xi}"): $(diff "${xi}" "${xo}" | grep -cE '^[><]') lines"
  fi
done < <(find "$(realpath "${HONE_XMIR_IN}")" -name '*.xmir' -type f -exec realpath --relative-to="${HONE_XMIR_IN}" {} \;)
