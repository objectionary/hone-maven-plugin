#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -e -o pipefail

if [ "${DEBUG}" == 'true' ]; then
  echo "We are in debug mode, printing all commands..."
  set -x
fi

SELF=$(dirname "$0")

if [ "${LANG}" != 'en_US.UTF-8' ]; then
  echo "Setting locale to en_US.UTF-8 from '${LANG}'"
  LANG=en_US.UTF-8
  export LANG
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
  exit 1
fi
# In order to save them "as is", just in case:
cp -R "${TARGET}/${CLASSES}" "${TARGET}/classes-before-hone"
echo "The binaries before hone are saved in '${TARGET}/classes-before-hone' ($(find "${TARGET}/classes-before-hone" -print | wc -l | xargs) files)"

# Maven options for all steps:
declare -a opts=(
  '--update-snapshots'
  '--fail-fast'
  '--strict-checksums'
  '--errors'
  '--batch-mode'
  '-Dfile.encoding=UTF-8'
  "-Deo.cache=${EO_CACHE}"
  "-Dexec.phino.target=${TARGET}"
)
if [ -n "${WORKDIR}" ] && [ -e "${WORKDIR}/settings.xml" ]; then
  opts+=("--settings=${WORKDIR}/settings.xml")
  echo "Using Maven settings file at ${WORKDIR}"
fi
if [ -n "${EO_VERSION}" ]; then
  opts+=("-Deo.version=${EO_VERSION}")
  echo "Using EO version ${EO_VERSION}"
fi
if [ -n "${JEO_VERSION}" ]; then
  opts+=("-Djeo.version=${JEO_VERSION}")
  echo "Using JEO version ${JEO_VERSION}"
fi
opts+=(
  "-Dbuildtime.output.csv=true"
  "-Dbuildtime.output.csv.file=${TARGET}/timings.csv"
)
opts+=("-Deo.xslMeasuresFile=${TARGET}/xsl-measures.csv")

if [ -z "${RULES}" ]; then
  RULES=$(find "${SELF}/rules" -name '*.yml' -exec realpath {} \;)
fi
for rule in ${RULES}; do
  if [ ! -e "${rule}" ]; then
    echo "YAML rule file does not exist: ${rule}"
    tree "${SELF}"
    exit 1
  fi
done
if [ -n "${EXTRA}" ]; then
  e=$(find "${EXTRA}" -name '*.yml' -exec realpath {} \; | sort | tr '\n' ' ')
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

if [ -n "${INCLUDES}" ]; then
  opts+=("-Djeo.disassemble.includes=${INCLUDES}")
  opts+=("-Djeo.assemble.includes=${INCLUDES}")
fi
if [ -n "${EXCLUDES}" ]; then
  opts+=("-Djeo.disassemble.excludes=${EXCLUDES}")
  opts+=("-Djeo.assemble.excludes=${EXCLUDES}")
fi

opts+=(
  "-Djeo.disassemble.sourcesDir=${TARGET}/${CLASSES}"
  "-Djeo.disassemble.outputDir=${TARGET}/generated-sources/jeo-disassemble"
  "-Dexec.phino.script=${SELF}/normalize.sh"
  "-Dexec.phino.verbose=${VERBOSE}"
  "-Dexec.phino.debug=${DEBUG}"
  "-Dexec.phino.rules=${RULES}"
  "-Dexec.phino.grep-in=${GREP_IN}"
  "-Dexec.phino.xmir-in=${TARGET}/generated-sources/jeo-disassemble"
  "-Dexec.phino.from=${TARGET}/generated-sources/phi"
  "-Dexec.phino.to=${TARGET}/generated-sources/phi-optimized"
  "-Dexec.phino.xmir-out=${TARGET}/generated-sources/unphi"
  "-Dexec.phino.small-steps=${SMALL_STEPS}"
  "-Dexec.phino.timeout=${TIMEOUT}"
  "-Dexec.phino.threads=${THREADS}"
  "-Dexec.phino.max-depth=${MAX_DEPTH}"
  "-Dexec.phino.max-cycles=${MAX_CYCLES}"
  "-Djeo.assemble.outputDir=${TARGET}/${CLASSES}"
)

opts+=('jeo:disassemble')
if [ "${SKIP_PHINO}" == 'true' ]; then
  echo "Skipping the phino step as requested"
  opts+=("-Djeo.assemble.sourcesDir=${TARGET}/generated-sources/jeo-disassemble")
else
  opts+=("-Djeo.assemble.sourcesDir=${TARGET}/generated-sources/unphi")
  opts+=('exec:exec')
fi
opts+=('jeo:assemble')

(
  set -x
  mvn "${opts[@]}"
)
