#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex -o pipefail

if [ "$(phino --version)" == "${PHINO}" ]; then
  echo "Phino ${PHINO} already installed"
  exit
fi

stack install --no-run-tests --no-run-benchmarks \
  --progress-bar count-only \
  "phino-${PHINO}"
cp /root/.local/bin/phino /usr/local/bin/phino

phino --help
