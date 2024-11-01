#!/bin/bash
# The MIT License (MIT)
#
# Copyright (c) 2024 Objectionary.com
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

set -ex
set -o pipefail

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
  '--update-snapshots'
  '--fail-fast'
  '--strict-checksums'
  '--errors'
  '--batch-mode'
  "--file=$(dirname "$0")/pom.xml"
)
if [ -n "${EO_VERSION}" ]; then
  opts+=("-Deo.version=${EO_VERSION}")
fi
if [ -n "${JEO_VERSION}" ]; then
  opts+=("-Djeo.version=${JEO_VERSION}")
fi

mvn "${opts[@]}" \
  jeo:disassemble \
  "-Djeo.disassemble.sourcesDir=${TARGET}/classes" \
  "-Djeo.disassemble.outputDir=${TARGET}/generated-sources/jeo-disassemble" \
  eo:xmir-to-phi \
  "-Deo.phiInputDir=${TARGET}/generated-sources/jeo-disassemble" \
  "-Deo.phiOutputDir=${TARGET}/generated-sources/phi"

if [ -z "${RULES}" ]; then
  RULES=$(find "${SELF}/rules" -name '*.yml' -exec basename {} \;)
fi
for rule in ${RULES}; do
  if [ ! -e "${SELF}/rules/${rule}" ]; then
    echo "YAML rule file doesn't exist: ${SELF}/rules/${rule}"
    tree "${SELF}"
    exit 1
  fi
done
SELF=$(dirname "$0")
from=${TARGET}/generated-sources/phi
to=${TARGET}/generated-sources/phi-optimized
mkdir -p "${to}"
while IFS= read -r f; do
  for rule in ${RULES}; do
    normalizer transform --rules "${SELF}/rules/${rule}" "${from}/${f}" --single -o "${to}/${f}"
  done
done < <(find "$(realpath "${from}")" -name '*.phi' -type f -exec realpath --relative-to="${from}" {} \;)

mvn "${opts[@]}" \
  eo:phi-to-xmir \
  "-Deo.unphiInputDir=${TARGET}/generated-sources/phi-optimized" \
  "-Deo.unphiOutputDir=${TARGET}/generated-sources/unphi" \
  jeo:unroll-phi \
  "-Djeo.unroll-phi.sourcesDir=${TARGET}/generated-sources/unphi" \
  "-Djeo.unroll-phi.outputDir=${TARGET}/generated-sources/unrolled" \
  jeo:assemble \
  "-Djeo.assemble.sourcesDir=${TARGET}/generated-sources/unrolled" \
  "-Djeo.assemble.outputDir=${TARGET}/classes"
