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

cd /tmp; \
ARCH="$(dpkg-architecture --query DEB_BUILD_GNU_CPU)"; \
STACK_URL="https://github.com/commercialhaskell/stack/releases/download/v${STACK}/stack-${STACK}-linux-$ARCH.tar.gz"; \
# sha256 from https://github.com/commercialhaskell/stack/releases/download/v${STACK}/stack-${STACK}-linux-$ARCH.tar.gz.sha256
case "$ARCH" in \
    'aarch64') \
        STACK_SHA256='f0c4b038c7e895902e133a2f4c4c217e03c4be44aa5da48aec9f7947f4af090b'; \
        ;; \
    'x86_64') \
        STACK_SHA256='4e635d6168f7578a5694a0d473c980c3c7ed35d971acae969de1fd48ef14e030'; \
        ;; \
    *) echo >&2 "error: unsupported architecture '$ARCH'" ; exit 1 ;; \
esac; \
curl -sSL "$STACK_URL" -o stack.tar.gz; \
echo "$STACK_SHA256 stack.tar.gz" | sha256sum --strict --check; \
\
curl -sSL "$STACK_URL.asc" -o stack.tar.gz.asc; \
GNUPGHOME="$(mktemp -d)"; export GNUPGHOME; \
gpg --batch --keyserver keyserver.ubuntu.com --receive-keys "$STACK_RELEASE_KEY"; \
gpg --batch --verify stack.tar.gz.asc stack.tar.gz; \
gpgconf --kill all; \
\
tar -xf stack.tar.gz -C /usr/local/bin --strip-components=1 "stack-$STACK-linux-$ARCH/stack"; \
stack config set system-ghc --global true; \
stack config set install-ghc --global false; \
\
rm -rf /tmp/*; \
\
stack --version;

cd /tmp; \
ARCH="$(dpkg-architecture --query DEB_BUILD_GNU_CPU)"; \
GHC_URL="https://downloads.haskell.org/~ghc/$GHC/ghc-$GHC-$ARCH-deb11-linux.tar.xz"; \
# sha256 from https://downloads.haskell.org/~ghc/$GHC/SHA256SUMS
case "$ARCH" in \
    'aarch64') \
        GHC_SHA256='1db449c445d34779d4deaba22341576f7b512a05b6c2b5cb64f3846d1509714e'; \
        ;; \
    'x86_64') \
        GHC_SHA256='78975575b8125ecf1f50f78b1016b14ea6e87abbf1fc39797af469d029c5d737'; \
        ;; \
    *) echo >&2 "error: unsupported architecture '$ARCH'" ; exit 1 ;; \
esac; \
curl -sSL "$GHC_URL" -o ghc.tar.xz; \
echo "$GHC_SHA256 ghc.tar.xz" | sha256sum --strict --check; \
\
GNUPGHOME="$(mktemp -d)"; export GNUPGHOME; \
curl -sSL "$GHC_URL.sig" -o ghc.tar.xz.sig; \
gpg --batch --keyserver keyserver.ubuntu.com --receive-keys "$GHC_RELEASE_KEY"; \
gpg --batch --verify ghc.tar.xz.sig ghc.tar.xz; \
gpgconf --kill all; \
\
tar xf ghc.tar.xz; \
cd "ghc-$GHC-$ARCH-unknown-linux"; \
./configure --prefix "/opt/ghc/$GHC"; \
make install; \
\
rm -rf /tmp/*; \
\
"/opt/ghc/$GHC/bin/ghc" --version
