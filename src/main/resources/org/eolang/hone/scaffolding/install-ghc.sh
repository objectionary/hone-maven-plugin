#!/bin/bash
# The MIT License (MIT)
#
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

# Taken from here: https://github.com/haskell/docker-haskell/blob/master/9.10/bullseye/Dockerfile

set -ex

if ghc --version 2>/dev/null; then
  echo "GHC already installed"
  exit
fi

cd /tmp
ARCH="$(dpkg-architecture --query DEB_BUILD_GNU_CPU)"
GHC_URL="https://downloads.haskell.org/~ghc/${GHC}/ghc-${GHC}-${ARCH}-deb11-linux.tar.xz"
case "${ARCH}" in \
    'aarch64') \
        GHC_SHA256='1db449c445d34779d4deaba22341576f7b512a05b6c2b5cb64f3846d1509714e'; \
        ;; \
    'x86_64') \
        GHC_SHA256='78975575b8125ecf1f50f78b1016b14ea6e87abbf1fc39797af469d029c5d737'; \
        ;; \
    *) echo >&2 "error: unsupported architecture '${ARCH}'" ; exit 1 ;; \
esac
curl -sSL "${GHC_URL}" -o ghc.tar.xz
echo "${GHC_SHA256} ghc.tar.xz" | sha256sum --strict --check

GNUPGHOME="$(mktemp -d)"; export GNUPGHOME
curl -sSL "${GHC_URL}.sig" -o ghc.tar.xz.sig
gpg --batch --keyserver keyserver.ubuntu.com --receive-keys "FFEB7CE81E16A36B3E2DED6F2DE04D4E97DB64AD"
gpg --batch --verify ghc.tar.xz.sig ghc.tar.xz
gpgconf --kill all

tar xf ghc.tar.xz
cd "ghc-${GHC}-${ARCH}-unknown-linux"
./configure --prefix "/opt/ghc/${GHC}"
make install

"/opt/ghc/${GHC}/bin/ghc" --version
