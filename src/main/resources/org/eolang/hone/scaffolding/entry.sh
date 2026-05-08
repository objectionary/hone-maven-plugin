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

if [ "${DEBUG}" == 'true' ]; then
  echo "We are in debug mode, printing all commands..."
  set -x
fi

SELF=$(dirname "$0")

if [ "${LANG}" != 'en_US.UTF-8' ]; then
  echo "Setting locale to en_US.UTF-8 from '${LANG}'"
  LANG=en_US.UTF-8
  export LANG
  LC_ALL=en_US.UTF-8
  export LC_ALL
  LANGUAGE=en_US.UTF-8
  export LANGUAGE
fi

cd "${SELF}" || exit 1

if [ -z "${TARGET}" ]; then
  echo "The \$TARGET environment variable is not set; make sure you do \
'docker run' with the '-e TARGET=...' parameter, which points to the \
target/ directory of a Maven project"
  exit 1
fi

if [ -z "${EO_CACHE}" ]; then
  echo "The \$EO_CACHE environment variable is not set; make sure you do \
'docker run' with the '-e EO_CACHE=...' parameter, which points to the \
directory with EO cache files"
  exit 1
fi

if [ ! -e "${TARGET}" ]; then
  echo "There is no '${TARGET}' directory, which most probably means \
that Docker is misconfigured; the directory must exist even if there are no .class files"
  exit 1
fi

if [ ! -e "${TARGET}/${CLASSES}" ]; then
  echo "There is no '${TARGET}/${CLASSES}' directory, which most probably means \
that the project has not been compiled yet; make sure you use 'hone-maven-plugin' \
after the 'compile' phase is finished; this is what is in the '${TARGET}' directory:"
  tree "${TARGET}"
  if [ "${SKIP_IF_NO_CLASSES}" == 'true' ]; then
    echo "We don't fail but quit quietly, because of skipIfNoClasses=true"
    exit
  else
    echo "We can't continue and must fail here. Set skipIfNoClasses to 'true' if you need a quiet pass."
    exit 1
  fi
fi

# In order to save them "as is", just in case:
cp -R "${TARGET}/${CLASSES}" "${TARGET}/classes-before-hone"
echo "The binaries before hone are saved in '${TARGET}/classes-before-hone' ($(find "${TARGET}/classes-before-hone" -print | wc -l | xargs) files)"

if [ -z "${PHINO_VERSION}" ]; then
  echo "PHINO_VERSION is not set"
  exit 1
fi
v=$(phino --version)
if [ "${v}" != "${PHINO_VERSION}" ]; then
  echo "Phino version is '${v}', while '${PHINO_VERSION}' was expected"
  exit 1
fi

if [ -z "${JEO_VERSION}" ]; then
  echo "JEO_VERSION is not set"
  exit 1
fi

# Maven options shared by both jeo invocations:
declare -a opts=(
  '--update-snapshots'
  '--fail-fast'
  '--strict-checksums'
  '--errors'
  '--batch-mode'
  '-Dfile.encoding=UTF-8'
  "-Deo.cache=${EO_CACHE}"
  "-Djeo.version=${JEO_VERSION}"
)
if [ -n "${WORKDIR}" ] && [ -e "${WORKDIR}/settings.xml" ]; then
  opts+=("--settings=${WORKDIR}/settings.xml")
  echo "Using Maven settings file at ${WORKDIR}"
fi
if [ -n "${EO_VERSION}" ]; then
  opts+=("-Deo.version=${EO_VERSION}")
  echo "Using EO version ${EO_VERSION}"
fi
echo "Using JEO version ${JEO_VERSION}"

if [ -z "${RULES}" ]; then
  RULES=$(find "${SELF}/rules" -name '*.yml' -exec "${RP}" {} \;)
fi
for rule in ${RULES}; do
  if [ ! -e "${rule}" ]; then
    echo "YAML rule file does not exist: ${rule}"
    tree "${SELF}"
    exit 1
  fi
done
if [ -n "${EXTRA}" ]; then
  e=$(find "${EXTRA}" -name '*.yml' -exec "${RP}" {} \; | sort | tr '\n' ' ')
  if [ -n "${e}" ]; then
    echo "Extra rules found in ${EXTRA}: ${e}"
    RULES="${RULES} ${e}"
  else
    echo "No extra rules found in ${EXTRA}"
  fi
fi

if [ -e /proc/meminfo ]; then
  printf 'Memory available: %s Gb\n' "$(grep MemAvailable /proc/meminfo | awk '{printf "%.2f\n", $2/1024/1024}')"
fi

printf 'Using Java: %s\n' "$(java --version | head -1)"

printf 'Using Maven: %s\n' "$(mvn --version | head -1)"

printf 'Using GNU Parallel: %s\n' "$(parallel --version | head -1)"

printf 'Using the following %d rules:\n\t%b\n' \
  "$(( "$(echo "${RULES}" | grep -o ' ' | wc -l)" + 1))" \
  "${RULES// /\\n\\t}"

declare -a disassemble_opts=(
  "${opts[@]}"
  "-Djeo.disassemble.sourcesDir=${TARGET}/${CLASSES}"
  "-Djeo.disassemble.outputDir=${TARGET}/hone/jeo-disassemble"
  "-Djeo.disassemble.mode=debug"
  "-Djeo.disassemble.xmir.modifiers=true"
  "-Djeo.disassemble.omitComments=true"
  "-Djeo.disassemble.omitListings=true"
  "-Djeo.disassemble.prettyXmir=true"
  "-Djeo.disassemble.xmir.verification=false"
)
declare -a assemble_opts=(
  "${opts[@]}"
  "-Djeo.assemble.outputDir=${TARGET}/${CLASSES}"
  "-Djeo.assemble.xmir.verification=false"
  "-Djeo.assemble.skip.verification=true"
)
if [ -n "${INCLUDES}" ]; then
  disassemble_opts+=("-Djeo.disassemble.includes=${INCLUDES}")
  assemble_opts+=("-Djeo.assemble.includes=${INCLUDES}")
fi
if [ -n "${EXCLUDES}" ]; then
  disassemble_opts+=("-Djeo.disassemble.excludes=${EXCLUDES}")
  assemble_opts+=("-Djeo.assemble.excludes=${EXCLUDES}")
fi
if [ "${SKIP_PHINO}" == 'true' ]; then
  echo "Skipping the phino step as requested"
  assemble_opts+=("-Djeo.assemble.sourcesDir=${TARGET}/hone/jeo-disassemble")
else
  assemble_opts+=("-Djeo.assemble.sourcesDir=${TARGET}/hone/unphi")
fi

timings_csv="${TARGET}/timings.csv"
mkdir -p "$(dirname "${timings_csv}")"
echo '"Module";"Mojo";"Time"' > "${timings_csv}"

function record_timing {
  local mojo="${1}"
  local seconds="${2}"
  printf '"hone";"%s";"%s"\n' "${mojo}" "${seconds}" >> "${timings_csv}"
}

function elapsed {
  awk -v start="${1}" -v end="$(date '+%s.%N')" 'BEGIN { printf "%.3f\n", end - start }'
}

start=$(date '+%s.%N')
(
  set -x
  mvn "${disassemble_opts[@]}" "org.eolang:jeo-maven-plugin:${JEO_VERSION}:disassemble"
)
record_timing "jeo-maven-plugin:disassemble (default-cli)" "$(elapsed "${start}")"

if [ "${SKIP_PHINO}" != 'true' ]; then
  export TARGET
  export HONE_VERSION
  export HONE_VERBOSE="${VERBOSE}"
  export HONE_DEBUG="${DEBUG}"
  export HONE_RULES="${RULES}"
  export HONE_GREP_IN="${GREP_IN}"
  export HONE_XMIR_IN="${TARGET}/hone/jeo-disassemble"
  export HONE_FROM="${TARGET}/hone/phi"
  export HONE_TO="${TARGET}/hone/phi-optimized"
  export HONE_XMIR_OUT="${TARGET}/hone/unphi"
  export HONE_SMALL_STEPS="${SMALL_STEPS}"
  export HONE_MAX_DEPTH="${MAX_DEPTH}"
  export HONE_MAX_CYCLES="${MAX_CYCLES}"
  export HONE_THREADS="${THREADS}"
  export HONE_TIMEOUT="${TIMEOUT}"
  export HONE_STATISTICS
  start=$(date '+%s.%N')
  (
    set -x
    "${SELF}/rewrite.sh"
  )
  record_timing "phino:rewrite (default-cli)" "$(elapsed "${start}")"
fi

start=$(date '+%s.%N')
(
  set -x
  mvn "${assemble_opts[@]}" "org.eolang:jeo-maven-plugin:${JEO_VERSION}:assemble"
)
record_timing "jeo-maven-plugin:assemble (default-cli)" "$(elapsed "${start}")"
