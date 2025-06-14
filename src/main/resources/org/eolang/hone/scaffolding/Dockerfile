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

COPY install-cabal.sh ./
RUN chmod a+x install-cabal.sh \
  && ./install-cabal.sh
ENV PATH="/root/.ghcup/bin:${PATH}"
ENV PATH="/root/.cabal/bin:${PATH}"
RUN cabal --version

ENV PHINO=0.0.0.3
COPY install-phino.sh ./
RUN chmod a+x install-phino.sh \
  && ./install-phino.sh
RUN phino --version
RUN cp "$(which phino)" /usr/bin

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
