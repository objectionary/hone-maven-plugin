#!/bin/bash
# The MIT License (MIT)
#
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex

if [ "$(eo-phi-normalizer --version)" == "${NORMALIZER}" ]; then
  echo "Normalizer ${NORMALIZER} already installed"
  exit
fi

stack install --no-run-tests --no-run-benchmarks \
  --progress-bar count-only \
  "eo-phi-normalizer-${NORMALIZER}"
cp /root/.local/bin/eo-phi-normalizer /usr/local/bin/eo-phi-normalizer

eo-phi-normalizer --help
