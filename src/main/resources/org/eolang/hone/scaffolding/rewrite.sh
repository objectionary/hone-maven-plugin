#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

RP=""
if command -v realpath >/dev/null 2>&1 && realpath --version >/dev/null 2>&1 && realpath --version 2>&1 | head -1 | grep -q 'GNU coreutils'; then
  RP="realpath"
elif command -v grealpath >/dev/null 2>&1 && grealpath --version >/dev/null 2>&1; then
  RP="grealpath"
else
  echo "No suitable realpath utility found (need GNU realpath or grealpath), can't rewrite"
  exit 1
fi

if ! "${RP}" --version >/dev/null; then
  echo "The system doesn't have GNU Coreutils installed, can't rewrite"
  exit 1
fi

SETSID=""
if command -v setsid >/dev/null 2>&1; then
  SETSID="setsid"
elif command -v gsetsid >/dev/null 2>&1; then
  SETSID="gsetsid"
else
  for cand in /opt/homebrew/opt/util-linux/bin/setsid /usr/local/opt/util-linux/bin/setsid; do
    if [ -x "${cand}" ]; then
      SETSID="${cand}"
      break
    fi
  done
fi

statistics_csv="${TARGET}/hone-statistics.csv"

function verbose {
  if [ "${HONE_VERBOSE}" == 'true' ]; then
    echo "$@"
  fi
}

function statistics_header {
  if [ "${HONE_STATISTICS}" == 'true' ]; then
    local csv="${1}"
    echo "ID,Before,After,Changed,LinesPerSec" > "${csv}"
  fi
}

function statistics_row {
  if [ "${HONE_STATISTICS}" == 'true' ]; then
    local csv="${1}"
    local idx="${2}"
    local phi="${3//\"/\"\"}"
    local pho="${4//\"/\"\"}"
    local changed="${5}"
    local per="${6}"
    printf '%s,"%s","%s",%s,%s\n' "${idx}" "${phi}" "${pho}" "${changed}" "${per}" >> "${csv}"
  fi
}

function atomic_write {
  # Run a command, capture its stdout into a temp file next to the target,
  # and atomically move it into place only when the command succeeds and
  # actually wrote something. A crashed command leaves no half-written file
  # behind that a later skip-if-newer check could mistake for a fresh result
  # (see #837), and a command that exits zero having written nothing is
  # refused the same way, instead of shipping an empty result downstream
  # (see #1039).
  local destination="${1}"
  shift
  local tmp="${destination}.tmp.$$"
  local code=0
  "${@}" > "${tmp}" || code=$?
  if [ "${code}" -eq 0 ] && [ -s "${tmp}" ]; then
    mv -f "${tmp}" "${destination}"
  else
    rm -f "${tmp}"
    return "$(( code == 0 ? 1 : code ))"
  fi
}

function now {
  # Current time in seconds with sub-second precision. macOS's BSD date has
  # no %N, so use perl's high-resolution clock when available (see #867).
  perl -MTime::HiRes=time -e 'printf "%.6f\n", time' 2>/dev/null || date '+%s'
}

function q {
  # Bash-compatible quoting of a single argument (portable printf %q).
  printf '%q' "${1}"
}

if [ "${HONE_DEBUG}" == 'true' ]; then
  set -x
fi

wanted=''
for candidate in en_US.UTF-8 C.UTF-8; do
  if locale -a 2>/dev/null | tr -d '-' | tr '[:upper:]' '[:lower:]' \
    | grep -qx "$(echo "${candidate}" | tr -d '-' | tr '[:upper:]' '[:lower:]')"; then
    wanted=${candidate}
    break
  fi
done
if [ -n "${wanted}" ] && [ "${LANG}" != "${wanted}" ]; then
  echo "Setting locale to ${wanted} from '${LANG}'"
  LANG=${wanted}
  export LANG
  LC_ALL=${wanted}
  export LC_ALL
  LANGUAGE=${wanted}
  export LANGUAGE
fi

mapfile -t rules < <(printf '%s\n' "${HONE_RULES}" | sed '/^$/d')

# A fingerprint of everything that changes the result of a rewrite, except the
# input file itself: the rules, their modification times, and the options. It
# is stored next to the output and compared on the next run, so that an edited
# rule or a changed option is not skipped (see #943).
stamp="${HONE_RULES}|${HONE_GREP_IN}|${HONE_SMALL_STEPS}|${HONE_MAX_CYCLES}|${HONE_MAX_DEPTH}"
for rule in "${rules[@]}"; do
  if [ -f "${rule}" ]; then
    stamp="${stamp}|$(date -r "${rule}" '+%s' 2>/dev/null || echo '0')"
  fi
done

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
  mark="${xo}.stamp"
  # The input is compared by its content, not by its modification time,
  # because jeo:disassemble writes it again on every run (see #1032).
  seal="${stamp}|$(cksum < "${xi}")"
  fresh=false
  if [ -f "${mark}" ] && [ "$(cat "${mark}")" == "${seal}" ] && [ -f "${xo}" ]; then
    if [ ! -f "${phi}" ]; then
      # There are no intermediates, because grep-in excluded this file the
      # previous time and the output is a plain copy of the input.
      fresh=true
    elif [ -f "${pho}" ] && [ "${pho}" -nt "${phi}" ] && [ "${xo}" -nt "${pho}" ]; then
      fresh=true
    fi
  fi
  if [ "${fresh}" == 'true' ]; then
    echo "Output $(basename "${xo}") was made from the same input $(basename "${xi}") all the way through the chain, with the same rules and options; skipping transformation for ${idx}"
    statistics_row "${statistics_csv}" "${idx},\"${phi}\",\"${pho}\",0,0"
    return
  fi
  verbose "Next ${idx} XMIR is ${xi} ($(du -sh "${xi}" | cut -f1))"
  if [ -n "${HONE_GREP_IN}" ]; then
    rc=0
    grep_in_check "${HONE_GREP_IN}" "${xi}" || rc=$?
    if [ "${rc}" -eq 2 ]; then
      echo "The grep-in pattern '${HONE_GREP_IN}' is invalid (grep exited with code 2); refusing to skip classes blindly" >&2
      exit 1
    fi
    if [ "${rc}" -ne 0 ]; then
      cp "${xi}" "${xo}"
      printf '%s' "${seal}" > "${mark}"
      echo "No grep-in match for ${idx} $(basename "${xi}") ($(du -sh "${xi}" | cut -f1)), skipping"
      statistics_row "${statistics_csv}" "${idx},\"${phi}\",\"${pho}\",0,0"
      return
    fi
  fi
  atomic_write "${phi}" phino rewrite "${phinopts[@]}" --input=xmir --sweet "${xi}"
  verbose "Converted ${idx} XMIR ($(du -sh "${xi}" | cut -f1)) to $(basename "${phi}") ($(du -sh "${phi}" | cut -f1))"
  rm -f "${pho}".*
  pos=0
  start=$(now)
  if [ "${HONE_SMALL_STEPS}" == "true" ]; then
    verbose "Applying ${#rules[@]} rule(s) one by one to ${idx} $(basename "${phi}")..."
    cp "${phi}" "${pho}"
    width=${#rules[@]}
    width=${#width}
    if [ "${width}" -lt 2 ]; then
      width=2
    fi
    for rule in "${rules[@]}"; do
      m=$(basename "${rule}")
      m="${m%.*}"
      pos=$(( pos + 1 ))
      t="${pho}.$(printf "%0${width}d" "${pos}")"
      atomic_write "${t}" phino rewrite "${phinopts[@]}" --max-cycles "${HONE_MAX_CYCLES}" --max-depth "${HONE_MAX_DEPTH}" --sweet --rule "${rule}" "${pho}"
      if cmp -s "${pho}" "${t}"; then
        verbose "  No changes made by '${m}' to $(basename "${t}")"
      else
        verbose "  $(diff "${pho}" "${t}" | grep -cE '^>') lines changed by '${m}' in $(basename "${t}")"
      fi
      cp "${t}" "${pho}"
    done
  else
    opts=()
    for rule in "${rules[@]}"; do
      opts+=("--rule=${rule}")
    done
    atomic_write "${pho}" phino rewrite "${phinopts[@]}" --max-cycles "${HONE_MAX_CYCLES}" --max-depth "${HONE_MAX_DEPTH}" --sweet "${opts[@]}" "${phi}"
  fi
  s_size=$(du -sh "${xi}" | cut -f1)
  s_lines=$(wc -l < "${pho}" | xargs)
  per=$(perl -E 'my ($lines, $end, $from) = @ARGV; my $secs = $end - $from; $secs = 0.001 if $secs < 0.001; say int($lines / $secs)' "${s_lines}" "$(now)" "${start}")
  changed=0
  if cmp -s "${phi}" "${pho}"; then
    echo "No changes in ${idx} $(basename "${pho}"): ${s_size}, ${s_lines} lines, ${per} lps"
  else
    changed=$(diff "${phi}" "${pho}" | awk '/^>/ { a++ } /^</ { d++ } END { print (a > d ? a : d) + 0 }' || true)
    echo "Modified ${idx} $(basename "${phi}") (${s_size}): ${changed}/${s_lines} lines changed, ${per} lps"
  fi
  statistics_row "${statistics_csv}" "${idx}" "${phi}" "${pho}" "${changed}" "${per}"
  atomic_write "${xo}" phino rewrite "${phinopts[@]}" --output=xmir --omit-listing --omit-comments "${pho}"
  verbose "Converted PHI to ${idx} $(basename "${xo}") ($(du -sh "${xo}" | cut -f1))"
  if cmp -s "${xi}" "${xo}"; then
    verbose "No changes made to ${idx} $(basename "${xi}")"
  else
    verbose "Changes made to ${idx} $(basename "${xi}"): $(diff "${xi}" "${xo}" | grep -cE '^>') lines"
  fi
  printf '%s' "${seal}" > "${mark}"
}

# Kill a process together with its whole descendant tree, escalating from a
# given signal towards the leader. Children are signalled before their parents
# so that nothing is reparented to init and left running. Used so that a
# timed-out rewrite does not orphan the phino process it spawned (see #727).
function kill_tree {
  local sig="${1}"
  local root="${2}"
  local child
  for child in $(pgrep -P "${root}" 2>/dev/null || true); do
    kill_tree "${sig}" "${child}"
  done
  kill "-${sig}" "${root}" 2>/dev/null || true
}

function rewrite_with_timeout {
  idx=${1}
  phi=${2}
  pho=${3}
  xi=${4}
  xo=${5}
  start=$(now)
  code=0
  # GNU "timeout" sends its signal only to its direct child (the bash
  # re-invocation of this script), not to the phino process that the "rewrite"
  # function launches in the foreground. On timeout that phino is reparented to
  # init and keeps burning CPU, and across a large run these orphans accumulate
  # until the optimize never converges (#727). To avoid that we run the worker
  # in its own session via "setsid" and, when the deadline passes, terminate the
  # whole process group (and any stragglers in its tree) so phino dies too.
  flag=$(mktemp)
  rm -f "${flag}"
  "${SETSID}" --wait "${0}" rewrite "$@" &
  sid=$!
  group=""
  # shellcheck disable=SC2329
  function stop_worker {
    kill_tree TERM "${sid}"
    kill -TERM "-${sid}" 2>/dev/null || true
    if [ -n "${watchdog:-}" ]; then
      kill_tree TERM "${watchdog}"
    fi
    sleep "${HONE_KILL_GRACE:-10}"
    kill_tree KILL "${sid}"
    kill -KILL "-${sid}" 2>/dev/null || true
    rm -f "${flag}"
    exit 143
  }
  trap stop_worker TERM INT HUP
  for _ in $(seq 1 100); do
    group=$(ps -o pgid= -p "${sid}" 2>/dev/null | tr -d ' ') || true
    [ -n "${group}" ] && break
    kill -0 "${sid}" 2>/dev/null || break
    sleep 0.05
  done
  (
    sleep "${HONE_TIMEOUT}"
    : > "${flag}"
    if [ -n "${group}" ]; then
      kill_tree TERM "${sid}"
      kill -TERM "-${group}" 2>/dev/null || true
      sleep "${HONE_KILL_GRACE:-10}"
      kill_tree KILL "${sid}"
      kill -KILL "-${group}" 2>/dev/null || true
    fi
  ) &
  watchdog=$!
  wait "${sid}" || code=$?
  # If the deadline fired, the watchdog is mid-escalation (TERM → grace →
  # KILL) and killing it here would orphan phino again (#727, #863) — so only
  # stop it when it is still in its initial sleep, i.e. the worker finished
  # before the deadline.
  if [ ! -f "${flag}" ]; then
    # The watchdog is a subshell with a separate sleep child. Killing only
    # the shell leaves that sleep orphaned until the timeout expires (see #933).
    kill_tree TERM "${watchdog}"
  fi
  wait "${watchdog}" 2>/dev/null || true
  # The worker finishing and the deadline passing are independent events, so
  # both can be true for a file that finished just as the watchdog fired. The
  # exit code decides: a worker that exited normally wrote its output, and
  # copying the input over it would ship the class unoptimized (#893).
  if [ "${code}" -eq 0 ]; then
    rm -f "${flag}"
  elif [ -f "${flag}" ]; then
    rm -f "${flag}"
    sec=$(perl -E "say int($(now) - ${start})")
    echo "Timeout in ${idx} $(basename "${xi}") ($(du -sh "${xi}" | cut -f1)) after ${sec} seconds"
    # The killed worker had no chance to remove the temp file of its
    # atomic_write, so it is swept here (see #1038).
    rm -f "${phi}".tmp.* "${pho}".tmp.* "${xo}".tmp.*
    cp "${xi}" "${xo}"
  else
    rm -f "${flag}"
    sec=$(perl -E "say int($(now) - ${start})")
    echo "Failure (exit code ${code}) in ${idx} $(basename "${xi}") ($(du -sh "${xi}" | cut -f1)) after ${sec} seconds; refusing to copy it through unoptimized" >&2
    exit "${code}"
  fi
}

function grep_in_check {
  # Verify a grep-in pattern against a file. The exit code mirrors grep -E:
  # 0 = the pattern matched, 1 = no match, 2 = the pattern is invalid.
  local rc=0
  grep -qE "${1}" "${2}" || rc=$?
  return "${rc}"
}

if [ "${1}" == 'rewrite' ]; then
  rewrite "${@:2}"
  exit
fi

if [ "${1}" == 'rewrite_with_timeout' ]; then
  rewrite_with_timeout "${@:2}"
  exit
fi

if [ "${1}" == 'grep_in_check' ]; then
  grep_in_check "${2}" "${3}"
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

# The output of a class that is no longer disassembled must not be assembled
# back (see #948), while the output of every other class stays for the skip
# check above (see #1032).
find "${HONE_XMIR_OUT}" -name '*.xmir' -type f | while IFS= read -r o; do
  if [ ! -f "${HONE_XMIR_IN}/${o#"${HONE_XMIR_OUT}"/}" ]; then
    rm -f "${o}" "${o}.stamp"
  fi
done

statistics_header "${statistics_csv}"

echo "Hone version: ${HONE_VERSION}"

echo "Phino version: $(phino --version | xargs)"

if [ "${HONE_TIMEOUT}" -lt 5 ]; then
  echo "Timeout is too low: ${HONE_TIMEOUT} seconds"
  exit 1
fi
echo "Timeout: ${HONE_TIMEOUT} seconds"

if [ -z "${SETSID}" ]; then
  echo "The 'setsid' utility is required to enforce the per-file timeout, can't rewrite"
  exit 1
fi
if ! command -v pgrep >/dev/null 2>&1; then
  echo "The 'pgrep' utility is required to enforce the per-file timeout, can't rewrite"
  exit 1
fi

echo "Using ${#rules[@]} rewriting rule(s)"

if [ -n "${HONE_GREP_IN}" ]; then
  echo "Grep-in: ${HONE_GREP_IN}"
fi

files=$(find "$("${RP}" "${HONE_XMIR_IN}")" -name '*.xmir' -type f -exec "${RP}" --relative-to="${HONE_XMIR_IN}" {} \; | LC_ALL=C sort)
if [ -z "${files}" ]; then
  echo "No XMIR files to process"
  exit 0
fi
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
  printf "%s rewrite_with_timeout %s %s %s %s %s\n" "$(q "${0}")" "$(q "${i}")" "$(q "${phi}")" "$(q "${pho}")" "$(q "${xi}")" "$(q "${xo}")" >> "${tasks}"
done <<< "${files}"

threads=${HONE_THREADS}
if [ -z "${threads}" ] || [ "${threads}" == '0' ]; then
  threads=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 1)
  echo "Using ${threads} threads, by the number of CPU cores"
fi

start=$(now)
if [ "${threads}" -eq 1 ]; then
  echo "Starting to rewrite ${total} file(s)..."
  while IFS= read -r cmd <&3; do
    env bash -c "${cmd}" < /dev/null
  done 3< "${tasks}"
else
  if ! parallel --version >/dev/null; then
    echo "The system doesn't have GNU Parallel installed, can't rewrite in ${threads} threads"
    exit 1
  fi
  echo "Starting to rewrite ${total} file(s) in ${threads} thread(s)..."
  export PARALLEL_HOME=${TARGET}/parallel
  mkdir -p "${PARALLEL_HOME}"
  parallel --record-env
  parallel "--joblog=${PARALLEL_HOME}/tasks.log" --will-cite \
    "--max-procs=${threads}" \
    --env _ \
    --halt-on-error=now,fail=1 --halt=now,fail=1 < "${tasks}"
fi
echo "Finished rewriting ${total} file(s) in $(perl -E "say int($(now) - ${start})") seconds"
