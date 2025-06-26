#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex -o pipefail

SELF=$(dirname "$0")

if [ -z "${TARGET}" ]; then
  echo "The \$TARGET environment variable is not set! Make sure you do \
'docker run' with the '-e TARGET=...' parameter, which points to the \
TARGET/ TARGET of a Maven project."
  exit 1
fi

if [ ! -e "${TARGET}/classes" ]; then
  echo "There is no '${TARGET}/classes' directory, which most probably means \
that the project hasn't been compiled yet. Make sure you use 'hone-maven-plugin' \
after the 'compile' phase is finished."
  exit 1
fi
# In order to save them "as is", just in case:
cp -R "${TARGET}/classes" "${TARGET}/classes-before-hone"

# Maven options for all steps:
declare -a opts=(
  '--settings=/hone/settings.xml'
  '--update-snapshots'
  '--fail-fast'
  '--strict-checksums'
  '--errors'
  '--batch-mode'
  '-Deo.cache=/eo-cache'
  "--file=$(dirname "$0")/pom.xml"
)
if [ -n "${EO_VERSION}" ]; then
  opts+=("-Deo.version=${EO_VERSION}")
fi
if [ -n "${JEO_VERSION}" ]; then
  opts+=("-Djeo.version=${JEO_VERSION}")
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
    echo "YAML rule file doesn't exist: ${rule}"
    tree "${SELF}"
    exit 1
  fi
done
if [ -n "${EXTRA}" ]; then
  RULES="${RULES} $(find "${EXTRA}" -name '*.yml' -exec realpath {} \;)"
fi

if [ -z "${SKIP_PHINO}" ]; then
  mvn "${opts[@]}" \
    jeo:disassemble \
    "-Djeo.disassemble.sourcesDir=${TARGET}/classes" \
    "-Djeo.disassemble.outputDir=${TARGET}/generated-sources/jeo-disassemble" \
    eo:xmir-to-phi \
    "-Deo.phiInputDir=${TARGET}/generated-sources/jeo-disassemble" \
    "-Deo.phiOutputDir=${TARGET}/generated-sources/phi" \
    exec:exec \
    "-Dexec.phino.script=${SELF}/normalize.sh" \
    "-Dexec.phino.rules=${RULES}" \
    "-Dexec.phino.from=${TARGET}/generated-sources/phi" \
    "-Dexec.phino.to=${TARGET}/generated-sources/phi-optimized" \
    "-Dexec.phino.xmir=${TARGET}/generated-sources/unphi" \
    jeo:assemble \
    "-Djeo.assemble.sourcesDir=${TARGET}/generated-sources/unphi" \
    "-Djeo.assemble.outputDir=${TARGET}/classes"
else
  mvn "${opts[@]}" \
    jeo:disassemble \
    "-Djeo.disassemble.sourcesDir=${TARGET}/classes" \
    "-Djeo.disassemble.outputDir=${TARGET}/generated-sources/jeo-disassemble" \
    jeo:assemble \
    "-Djeo.assemble.sourcesDir=${TARGET}/generated-sources/jeo-disassemble" \
    "-Djeo.assemble.outputDir=${TARGET}/classes"
fi
