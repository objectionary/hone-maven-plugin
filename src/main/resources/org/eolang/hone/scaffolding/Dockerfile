# The MIT License (MIT)
#
# Copyright (c) 2024-2025 Objectionary.com
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

FROM yegor256/hone:0.0.29

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.0"

ENV DEBIAN_FRONTEND="noninteractive"

ENV LC_ALL="C.UTF-8"

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    ca-certificates=* \
    curl=* \
    dpkg-dev=* \
    g++=* \
    gcc=* \
    git=* \
    gnupg=* \
    libc6-dev=* \
    libffi-dev=* \
    libgmp-dev=* \
    libnuma-dev=* \
    make=* \
    netbase=* \
    openjdk-21-jdk-headless=* \
    tree=* \
    wget=* \
    xz-utils=* \
    zlib1g-dev=* \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /hone

COPY install-maven.sh ./
RUN chmod a+x install-maven.sh \
  && ./install-maven.sh

ENV GHC=9.10.1
COPY install-ghc.sh ./
RUN chmod a+x install-ghc.sh \
  && ./install-ghc.sh
ENV PATH="/opt/ghc/${GHC}/bin:$PATH"

ENV STACK=2.15.7
COPY install-stack.sh ./
RUN chmod a+x install-stack.sh \
  && ./install-stack.sh \
  && stack config set system-ghc --global true \
  && stack upgrade \
  && stack update

ENV NORMALIZER=3.2.0
COPY install-normalizer.sh ./
RUN chmod a+x install-normalizer.sh \
  && ./install-normalizer.sh

COPY in-docker-pom.xml pom.xml

# Warming up Maven cache:
ENV MAVEN_OPTS="-Dmaven.repo.local=/tmp/m2"
COPY extensions.xml ./.mvn/
RUN mvn --update-snapshots --batch-mode --strict-checksums --errors \
  -Dbuildtime.output.log=true \
  eo:help \
  jeo:help \
  exec:help \
  && chmod a+w -R /tmp/m2

COPY entry.sh ./
RUN chmod a+x entry.sh
COPY normalize.sh ./
RUN chmod a+x normalize.sh
COPY rules/ ./rules/

ENTRYPOINT ["/hone/entry.sh"]
