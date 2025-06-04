#!/bin/bash
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex -o pipefail

if phino --version && [ "$(phino --version)" == "${PHINO}" ]; then
  echo "Phino ${PHINO} already installed"
  exit
fi

cabal update
cabal install --global --disable-tests --disable-coverage "phino-${PHINO}"

cp /root/.local/bin/phino /usr/local/bin/phino

phino --help
