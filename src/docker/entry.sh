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

if [ -z "${target}" ]; then
  echo "The \$TARGET environment variable is not set!"
  echo "Make sure you do 'docker run' with the '-e TARGET=...' parameter,"
  echo "which points to the target/ directory of a Maven project."
  exit 1
fi

declare -a opts=('--update-snapshots' '--fail-fast' '--errors')

mvn "${gopts[@]}" \
  jeo:disassemble \
  "-Djeo.disassemble.sourcesDir=${target}/classes" \
  "-Djeo.disassemble.outputDir=${target}/generated-sources/jeo-disassemble"

# opeo:decompile
# -Dopeo.decompile.sourcesDir=${directory}/generated-sources/jeo-disassemble
# -Dopeo.decompile.outputDir=${directory}/generated-sources/opeo-decompile

# eo:xmir-to-phi
# -Deo.phiInputDir=${directory}/generated-sources/opeo-decompile
# -Deo.phiOutputDir=${directory}/generated-sources/phi

# normalizer

# eo:phi-to-xmir
# -DunphiInputDir=${directory}/generated-sources/phi
# -DunphiOutputDir=${directory}/generated-sources/unphi

# opeo:compile
# -Dopeo.compile.sourcesDir=${directory}/generated-sources/ineo-staticize
# -Dopeo.compile.outputDir=${directory}/generated-sources/opeo-compile

# jeo:assemble
# -Djeo.assemble.sourcesDir=${directory}/generated-sources/opeo-compile
# -Djeo.assemble.outputDir=${directory}/classes
