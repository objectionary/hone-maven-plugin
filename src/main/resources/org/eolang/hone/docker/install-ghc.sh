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

# Taken from here: https://github.com/haskell/docker-haskell/blob/master/9.10/bullseye/Dockerfile

set -ex

cd /tmp
ARCH="$(dpkg-architecture --query DEB_BUILD_GNU_CPU)"
GHC_URL="https://downloads.haskell.org/~ghc/$GHC/ghc-$GHC-$ARCH-deb11-linux.tar.xz"
case "$ARCH" in \
    'aarch64') \
        GHC_SHA256='1db449c445d34779d4deaba22341576f7b512a05b6c2b5cb64f3846d1509714e'; \
        ;; \
    'x86_64') \
        GHC_SHA256='78975575b8125ecf1f50f78b1016b14ea6e87abbf1fc39797af469d029c5d737'; \
        ;; \
    *) echo >&2 "error: unsupported architecture '$ARCH'" ; exit 1 ;; \
esac
curl -sSL "$GHC_URL" -o ghc.tar.xz
echo "$GHC_SHA256 ghc.tar.xz" | sha256sum --strict --check

GNUPGHOME="$(mktemp -d)"; export GNUPGHOME
curl -sSL "$GHC_URL.sig" -o ghc.tar.xz.sig
gpg --batch --keyserver keyserver.ubuntu.com --receive-keys "$GHC_RELEASE_KEY"
gpg --batch --verify ghc.tar.xz.sig ghc.tar.xz
gpgconf --kill all

tar xf ghc.tar.xz
cd "ghc-$GHC-$ARCH-unknown-linux"
./configure --prefix "/opt/ghc/$GHC"
make install
