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

FROM ubuntu:24.04

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.11"

ENV DEBIAN_FRONTEND=noninteractive

ENV LC_ALL=C.UTF-8

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    ca-certificates=* \
    curl=* \
    wget=* \
    dpkg-dev=* \
    openjdk-21-jdk-headless=* \
    git=* \
    gcc=* \
    gnupg=* \
    g++=* \
    libc6-dev=* \
    libffi-dev=* \
    libgmp-dev=* \
    libnuma-dev=* \
    make=* \
    netbase=* \
    xz-utils=* \
    zlib1g-dev=* \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /hone

COPY install-maven.sh ./
RUN chmod a+x install-maven.sh \
  && ./install-maven.sh

ENV GHC 9.10.1
ENV GHC_RELEASE_KEY FFEB7CE81E16A36B3E2DED6F2DE04D4E97DB64AD
COPY install-ghc.sh ./
RUN chmod a+x install-ghc.sh \
  && ./install-ghc.sh
ENV PATH /root/.cabal/bin:/root/.local/bin:/opt/ghc/${GHC}/bin:$PATH

ENV STACK 2.15.7
ENV STACK_RELEASE_KEY C5705533DA4F78D8664B5DC0575159689BEFB442
COPY install-stack.sh ./
RUN chmod a+x install-stack.sh \
  && ./install-stack.sh \
  && stack config set system-ghc --global true

ENV NORMALIZER 1.1.0
RUN stack update \
  && stack install --resolver lts-22.16 eo-phi-normalizer-${NORMALIZER}

COPY in-docker-pom.xml pom.xml

# Warming up Maven cache:
RUN mvn eo:help jeo:help

COPY entry.sh ./
COPY simple.yml ./

ENTRYPOINT ["/hone/entry.sh"]
