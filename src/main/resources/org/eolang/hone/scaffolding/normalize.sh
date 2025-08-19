#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

function verbose {
  if [ "${HONE_VERBOSE}" == 'true' ]; then
    echo "$@"
  fi
}

if [ "${HONE_DEBUG}" == 'true' ]; then
  set -x
fi

IFS=' ' read -r -a rules <<< "${HONE_RULES}"

function rewrite {
  idx=${1}
  phi=${2}
  pho=${3}
  xi=${4}
  xo=${5}
  phinopts=()
  if [ "${HONE_DEBUG}" == 'true' ]; then
    phinopts+=(--log-level=debug)
  fi
  mkdir -p "$(dirname "${phi}")"
  mkdir -p "$(dirname "${pho}")"
  mkdir -p "$(dirname "${xo}")"
  verbose "Next ${idx} XMIR is ${xi} ($(du -sh "${xi}" | cut -f1))"
  if [ -n "${HONE_GREP_IN}" ] && ! grep -qE "${HONE_GREP_IN}" "${xi}"; then
    cp "${xi}" "${xo}"
    echo "No grep-in match for $(basename "${xi}"), skipping"
    return
  fi
  phino rewrite "${phinopts[@]}" --input=xmir --sweet --nothing "${xi}" > "${phi}"
  verbose "Converted XMIR ($(du -sh "${xi}" | cut -f1)) to $(basename "${phi}") ($(du -sh "${phi}" | cut -f1))"
  rm -f "${pho}.*"
  pos=0
  start=$(date '+%s.%N')
  if [ "${HONE_SMALL_STEPS}" == "true" ]; then
    verbose "Applying ${#rules[@]} rule(s) one by one to $(basename "${phi}")..."
    cp "${phi}" "${pho}"
    for rule in "${rules[@]}"; do
      m=$(basename "${rule}" .yml)
      pos=$(( pos + 1 ))
      t="${pho}.${pos}"
      phino rewrite "${phinopts[@]}" --max-depth "${HONE_MAX_DEPTH}" --sweet --rule "${rule}" "${pho}" > "${t}"
      if cmp -s "${pho}" "${t}"; then
        verbose "  No changes made by '${m}' to $(basename "${t}")"
      else
        verbose "  $(diff "${pho}" "${t}" | grep -cE '^[><]') lines changed by '${m}' in $(basename "${t}")"
      fi
      cp "${t}" "${pho}"
    done
  else
    opts=()
    for rule in "${rules[@]}"; do
      opts+=("--rule=${rule}")
    done
    phino rewrite "${phinopts[@]}" --max-depth "${HONE_MAX_DEPTH}" --sweet "${opts[@]}" "${phi}" > "${pho}"
  fi
  s_size=$(du -sh "${xi}" | cut -f1)
  s_lines=$(wc -l < "${pho}")
  per=$(perl -E "say int( ${s_lines} / ( $(date '+%s.%N') - ${start} ) )")
  if cmp -s "${phi}" "${pho}"; then
    echo "No changes in ${idx} $(basename "${pho}"): ${s_size}, ${s_lines} lines, ${per} lps"
  else
    echo "Modified ${idx} $(basename "${phi}") (${s_size}): $(diff "${phi}" "${pho}" | grep -cE '^[><]')/${s_lines} lines changed, ${per} lps"
  fi
  phino rewrite "${phinopts[@]}" --nothing --output=xmir --omit-listing --omit-comments "${pho}" > "${xo}"
  verbose "Converted PHI to $(basename "${xo}") ($(du -sh "${xo}" | cut -f1))"
  if cmp -s "${xi}" "${xo}"; then
    verbose "No changes made to $(basename "${xi}")"
  else
    verbose "Changes made to $(basename "${xi}"): $(diff "${xi}" "${xo}" | grep -cE '^[><]') lines"
  fi
}

if [ -n "$*" ]; then
  rewrite "$@"
  exit
fi

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

echo "Timeout: ${HONE_TIMEOUT} seconds"

echo "Phino version: $(phino --version | xargs)"

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
  phi="${HONE_FROM}/${f}.phi"
  pho="${HONE_TO}/${f}.phi"
  xi="${HONE_XMIR_IN}/${f}.xmir"
  xo="${HONE_XMIR_OUT}/${f}.xmir"
  if ! timeout "${HONE_TIMEOUT}" "${0}" "${idx}/${total}" "${phi}" "${pho}" "${xi}" "${xo}"; then
    cp "${xi}" "${xo}"
  fi
done <<< "${files}"
