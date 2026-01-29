#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

if ! realpath --version >/dev/null; then
  echo "The system doesn't have GNU Coreutils installed, can't rewrite"
  exit 1
fi

function verbose {
  if [ "${HONE_VERBOSE}" == 'true' ]; then
    echo "$@"
  fi
}

if [ "${HONE_DEBUG}" == 'true' ]; then
  set -x
fi

if [ "${LANG}" != 'en_US.UTF-8' ]; then
  echo "Setting locale to en_US.UTF-8 from '${LANG}'"
  LANG=en_US.UTF-8
  export LANG
  LC_ALL=en_US.UTF-8
  export LC_ALL
  LANGUAGE=en_US.UTF-8
  export LANGUAGE
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
  if [ -f "${pho}" ] && [ "${pho}" -nt "${phi}" ]; then
    echo "Target $(basename "${pho}") is newer than source $(basename "${phi}"); skipping transformation for ${idx}"
    return
  fi
  verbose "Next ${idx} XMIR is ${xi} ($(du -sh "${xi}" | cut -f1))"
  if [ -n "${HONE_GREP_IN}" ] && ! grep -qE "${HONE_GREP_IN}" "${xi}"; then
    cp "${xi}" "${xo}"
    echo "No grep-in match for ${idx} $(basename "${xi}") ($(du -sh "${xi}" | cut -f1)), skipping"
    return
  fi
  phino rewrite "${phinopts[@]}" --input=xmir --sweet "${xi}" > "${phi}"
  verbose "Converted ${idx} XMIR ($(du -sh "${xi}" | cut -f1)) to $(basename "${phi}") ($(du -sh "${phi}" | cut -f1))"
  rm -f "${pho}.*"
  pos=0
  start=$(date '+%s.%N')
  if [ "${HONE_SMALL_STEPS}" == "true" ]; then
    verbose "Applying ${#rules[@]} rule(s) one by one to ${idx} $(basename "${phi}")..."
    cp "${phi}" "${pho}"
    for rule in "${rules[@]}"; do
      m=$(basename "${rule}" .yml)
      pos=$(( pos + 1 ))
      t="${pho}.$(printf '%002d' "${pos}")"
      phino rewrite "${phinopts[@]}" --max-cycles "${HONE_MAX_CYCLES}" --max-depth "${HONE_MAX_DEPTH}" --sweet --rule "${rule}" "${pho}" > "${t}"
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
    phino rewrite "${phinopts[@]}" --max-cycles "${HONE_MAX_CYCLES}" --max-depth "${HONE_MAX_DEPTH}" --sweet "${opts[@]}" "${phi}" > "${pho}"
  fi
  s_size=$(du -sh "${xi}" | cut -f1)
  s_lines=$(wc -l < "${pho}" | xargs)
  per=$(perl -E "say int(${s_lines} / ($(date '+%s.%N') - ${start}))")
  if cmp -s "${phi}" "${pho}"; then
    echo "No changes in ${idx} $(basename "${pho}"): ${s_size}, ${s_lines} lines, ${per} lps"
  else
    echo "Modified ${idx} $(basename "${phi}") (${s_size}): $(diff "${phi}" "${pho}" | grep -cE '^[><]')/${s_lines} lines changed, ${per} lps"
  fi
  phino rewrite "${phinopts[@]}" --output=xmir --omit-listing --omit-comments "${pho}" > "${xo}"
  verbose "Converted PHI to ${idx} $(basename "${xo}") ($(du -sh "${xo}" | cut -f1))"
  if cmp -s "${xi}" "${xo}"; then
    verbose "No changes made to ${idx} $(basename "${xi}")"
  else
    verbose "Changes made to ${idx} $(basename "${xi}"): $(diff "${xi}" "${xo}" | grep -cE '^[><]') lines"
  fi
}

function rewrite_with_timeout {
  idx=${1}
  phi=${2}
  pho=${3}
  xi=${4}
  xo=${5}
  start=$(date '+%s.%N')
  if ! timeout "${HONE_TIMEOUT}" "${0}" rewrite "$@"; then
    sec=$(perl -E "say int($(date '+%s.%N') - ${start})")
    if [ "${sec}" -eq 0 ]; then
      echo "Failure in ${idx} $(basename "${xi}") ($(du -sh "${xi}" | cut -f1))"
      exit 1
    fi
    echo "Timeout in ${idx} $(basename "${xi}") ($(du -sh "${xi}" | cut -f1)) after ${sec} seconds"
    cp "${xi}" "${xo}"
  fi
}

if [ "${1}" == 'rewrite' ]; then
  rewrite "${@:2}"
  exit
fi

if [ "${1}" == 'rewrite_with_timeout' ]; then
  rewrite_with_timeout "${@:2}"
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

echo "Hone version: ${HONE_VERSION}"

echo "Phino version: $(phino --version | xargs)"

if [ "${HONE_TIMEOUT}" -lt 5 ]; then
  echo "Timeout is too low: ${HONE_TIMEOUT} seconds"
  exit 1
fi
echo "Timeout: ${HONE_TIMEOUT} seconds"

echo "Using ${#rules[@]} rewriting rule(s)"

if [ -n "${HONE_GREP_IN}" ]; then
  echo "Grep-in: ${HONE_GREP_IN}"
fi

files=$(find "$(realpath "${HONE_XMIR_IN}")" -name '*.xmir' -type f -exec realpath --relative-to="${HONE_XMIR_IN}" {} \; | sort)
total=$(echo "${files}" | wc -l | xargs)
tasks=${TARGET}/hone-tasks.txt
mkdir -p "$(dirname "${tasks}")"
rm -f "${tasks}"
verbose "Found ${total} XMIR file(s) to process"
idx=0
while IFS= read -r f; do
  idx=$(( idx + 1 ))
  f=${f%.*}
  phi="${HONE_FROM}/${f}.phi"
  pho="${HONE_TO}/${f}.phi"
  xi="${HONE_XMIR_IN}/${f}.xmir"
  xo="${HONE_XMIR_OUT}/${f}.xmir"
  i="${idx}/${total}"
  printf "%s rewrite_with_timeout %s %s %s %s %s\n" "${0@Q}" "${i@Q}" "${phi@Q}" "${pho@Q}" "${xi@Q}" "${xo@Q}" >> "${tasks}"
done <<< "${files}"

threads=${HONE_THREADS}
if [ -z "${threads}" ] || [ "${threads}" == '0' ]; then
  threads=$(nproc)
  echo "Using ${threads} threads, by the number of CPU cores"
fi

start=$(date '+%s.%N')
if [ "${threads}" -eq 1 ]; then
  echo "Starting to rewrite ${total} file(s)..."
  while IFS= read -r cmd; do
    /bin/bash -c "${cmd}"
  done <<< "$(cat "${tasks}")"
else
  if ! parallel --version >/dev/null; then
    echo "The system doesn't have GNU Parallel installed, can't rewrite in ${threads} threads"
    exit 1
  fi
  echo "Starting to rewrite ${total} file(s) in ${threads} thread(s)..."
  export PARALLEL_HOME=${TARGET}/parallel
  mkdir -p "${PARALLEL_HOME}"
  parallel --record-env
  parallel --retries=0 "--joblog=${PARALLEL_HOME}/tasks.log" --will-cite \
    "--max-procs=${threads}" \
    "--tmpdir=${PARALLEL_HOME}/tmp" \
    --env _ \
    --halt-on-error=now,fail=1 --halt=now,fail=1 < "${tasks}"
fi
echo "Finished rewriting ${total} file(s) in $(perl -E "say int($(date '+%s.%N') - ${start})") seconds"
