#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

function verbose {
  if [ "${HONE_VERBOSE}" == 'true' ]; then
    echo "$@"
  fi
}

verbose "Running in verbose mode, printing all messages..."

if [ ! -d "${HONE_XMIR_IN}" ]; then
  echo "The source directory '${HONE_XMIR_IN}' does not exist"
  exit 1
fi
verbose "Source directory with XMIR files: ${HONE_XMIR_IN}"

if [ -z "${HONE_RULES}" ]; then
  echo "No rules specified in the \$HONE_RULES environment variable"
  exit 1
fi

mkdir -p "${HONE_FROM}"
verbose "Source directory for PHI files: ${HONE_FROM}"

mkdir -p "${HONE_TO}"
verbose "Target directory for PHI files: ${HONE_TO}"

mkdir -p "${HONE_XMIR_OUT}"
verbose "Output directory for XMIR files: ${HONE_XMIR_OUT}"

echo "Phino version: $(phino --version | xargs)"

IFS=' ' read -r -a rules <<< "${HONE_RULES}"
echo "Using ${#rules[@]} rewriting rule(s)"

if [ -n "${HONE_GREP_IN}" ]; then
  echo "Grep-in: ${HONE_GREP_IN}"
fi

files=$(find "$(realpath "${HONE_XMIR_IN}")" -name '*.xmir' -type f -exec realpath --relative-to="${HONE_XMIR_IN}" {} \; | sort)
total=$(echo "${files}" | wc -l | xargs)
verbose "Found ${total} file(s) to process"
idx=0
while IFS= read -r f; do
  idx=$(( idx + 1 ))
  f=${f%.*}
  r="${HONE_FROM}/${f}.phi"
  s="${HONE_TO}/${f}.phi"
  xo=${HONE_XMIR_OUT}/${f}.xmir
  xi=${HONE_XMIR_IN}/${f}.xmir
  mkdir -p "$(dirname "${r}")"
  mkdir -p "$(dirname "${s}")"
  mkdir -p "$(dirname "${HONE_XMIR_OUT}/${f}")"
  verbose "Next ${idx}/${total} XMIR is ${xi} ($(du -sh "${xi}" | cut -f1))"
  if [ -n "${HONE_GREP_IN}" ] && ! grep -qE "${HONE_GREP_IN}" "${xi}"; then
    cp "${xi}" "${xo}"
    echo "No grep-in match for $(basename "${xi}"), skipping"
    continue
  fi
  phino rewrite --input=xmir --sweet --nothing "${xi}" > "${r}"
  verbose "Converted XMIR ($(du -sh "${xi}" | cut -f1)) to $(basename "${r}") ($(du -sh "${r}" | cut -f1))"
  rm -f "${s}.*"
  pos=0
  start=$(date '+%s.%N')
  if [ "${HONE_SMALL_STEPS}" == "true" ]; then
    verbose "Applying ${#rules[@]} rule(s) one by one to $(basename "${r}")..."
    cp "${HONE_FROM}/${f}.phi" "${s}"
    for rule in "${rules[@]}"; do
      m=$(basename "${rule}" .yml)
      pos=$(( pos + 1 ))
      t="${HONE_TO}/${f}.phi.${pos}"
      phino rewrite --max-depth "${HONE_MAX_DEPTH}" --sweet --rule "${rule}" "${s}" > "${t}"
      if cmp -s "${s}" "${t}"; then
        verbose "  No changes made by '${m}' to $(basename "${t}")"
      else
        verbose "  $(diff "${s}" "${t}" | grep -cE '^[><]') lines changed by '${m}' in $(basename "${t}")"
      fi
      cp "${t}" "${s}"
    done
  else
    opts=()
    for rule in "${rules[@]}"; do
      opts+=("--rule=${rule}")
    done
    phino rewrite --max-depth "${HONE_MAX_DEPTH}" --sweet "${opts[@]}" "${r}" > "${s}"
  fi
  s_size=$(du -sh "${xi}" | cut -f1)
  s_lines=$(wc -l < "${s}")
  per=$(perl -E "say int(${s_lines} / ( $(date '+%s.%N') - ${start} ))")
  if cmp -s "${r}" "${s}"; then
    echo "No changes in ${idx}/${total} $(basename "${s}"): ${s_size}, ${s_lines} lines, ${per} lps"
  else
    echo "Modified ${idx}/${total} $(basename "${r}") (${s_size}): $(diff "${r}" "${s}" | grep -cE '^[><]')/${s_lines} lines changed, ${per} lps"
  fi
  phino rewrite --nothing --output=xmir --omit-listing --omit-comments "${s}" > "${xo}"
  verbose "Converted PHI to $(basename "${xo}") ($(du -sh "${xo}" | cut -f1))"
  if cmp -s "${xi}" "${xo}"; then
    verbose "No changes made to $(basename "${xi}")"
  else
    verbose "Changes made to $(basename "${xi}"): $(diff "${xi}" "${xo}" | grep -cE '^[><]') lines"
  fi
done <<< "${files}"
