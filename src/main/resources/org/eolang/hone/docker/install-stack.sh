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

if stack --version 2>/dev/null; then
  echo "Stack already installed"
  exit
fi

cd /tmp
ARCH="$(dpkg-architecture --query DEB_BUILD_GNU_CPU)"
STACK_URL="https://github.com/commercialhaskell/stack/releases/download/v${STACK}/stack-${STACK}-linux-$ARCH.tar.gz"

case "$ARCH" in \
    'aarch64') \
        STACK_SHA256='f0c4b038c7e895902e133a2f4c4c217e03c4be44aa5da48aec9f7947f4af090b'; \
        ;; \
    'x86_64') \
        STACK_SHA256='4e635d6168f7578a5694a0d473c980c3c7ed35d971acae969de1fd48ef14e030'; \
        ;; \
    *) echo >&2 "error: unsupported architecture '${ARCH}'" ; exit 1 ;; \
esac
curl -sSL "${STACK_URL}" -o stack.tar.gz
echo "${STACK_SHA256} stack.tar.gz" | sha256sum --strict --check

curl -sSL "${STACK_URL}.asc" -o stack.tar.gz.asc
GNUPGHOME="$(mktemp -d)"; export GNUPGHOME
gpg --batch --keyserver keyserver.ubuntu.com --receive-keys "C5705533DA4F78D8664B5DC0575159689BEFB442"
gpg --batch --verify stack.tar.gz.asc stack.tar.gz
gpgconf --kill all
tar -xf stack.tar.gz -C /usr/local/bin --strip-components=1 "stack-${STACK}-linux-${ARCH}/stack"

stack --version
