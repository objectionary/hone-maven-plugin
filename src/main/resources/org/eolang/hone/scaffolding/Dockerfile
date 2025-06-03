# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

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

ENV PHINO=0.0.0.1
COPY install-phino.sh ./
RUN chmod a+x install-phino.sh \
  && ./install-phino.sh

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
