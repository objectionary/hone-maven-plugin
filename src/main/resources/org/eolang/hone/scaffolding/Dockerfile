# SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
# SPDX-License-Identifier: MIT

FROM ubuntu:24.04 AS builder

SHELL ["/bin/bash", "-e", "-c", "-o", "pipefail"]

ENV DEBIAN_FRONTEND=noninteractive
ENV LC_ALL=en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US.UTF-8

RUN apt-get update && apt-get install --yes --no-install-recommends \
    locales=* \
    build-essential=* \
    ca-certificates=* \
    curl=* \
    gnupg2=* \
    libgmp-dev=* \
    software-properties-common=* \
    zlib1g-dev=* \
  && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
  && locale-gen \
  && update-locale LANG=en_US.UTF-8 \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

ARG GHC=9.6.7
ARG CABAL=3.12.1.0

RUN curl --proto '=https' --tlsv1.2 -sSf https://get-ghcup.haskell.org | sh
ENV PATH="/root/.ghcup/bin:${PATH}"
RUN ghcup -v install ghc --isolate /usr/local --force "${GHC}" && \
    ghcup -v install cabal --isolate /usr/local/bin --force "${CABAL}"

ARG PHINO_VERSION=0.0.0.0
RUN cabal update \
  && cabal install --disable-tests --disable-coverage --overwrite-policy=always "phino-${PHINO_VERSION}" \
  && find /root -name phino -type f 2>/dev/null | head -1 | xargs -I {} cp {} /usr/local/bin/phino \
  && chmod +x /usr/local/bin/phino \
  && /usr/local/bin/phino --version

FROM eclipse-temurin:25.0.3_9-jre-jammy

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.0"

ENV HOME=/root

ENV DEBIAN_FRONTEND=noninteractive
ENV LC_ALL=en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US.UTF-8

# Install only essential runtime dependencies
RUN apt-get update && apt-get install --yes --no-install-recommends \
    locales=* \
    ca-certificates=* \
    curl=* \
    wget=* \
    parallel=* \
    libgmp10=* \
    libxml2-utils=* \
    tree=* \
    bzip2=* \
    unzip=* \
  && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
  && locale-gen \
  && update-locale LANG=en_US.UTF-8 \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Install Maven (smaller footprint)
ENV MAVEN_VERSION=3.9.9
ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"
RUN wget --quiet "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  && tar xzf "apache-maven-${MAVEN_VERSION}-bin.tar.gz" -C /opt \
  && mv "/opt/apache-maven-${MAVEN_VERSION}" "${MAVEN_HOME}" \
  && rm "apache-maven-${MAVEN_VERSION}-bin.tar.gz"

WORKDIR /hone

# Copy phino binary and its dependencies from builder:
COPY --from=builder /usr/local/bin/phino /usr/bin/phino
RUN phino --version

COPY settings.xml /hone/

ARG JEO_VERSION=0.0.0

# Pre-download Maven dependencies and clean up unnecessary plugins
RUN mkdir -p /hone/.m2 \
  && mvn --settings=/hone/settings.xml --update-snapshots --batch-mode --strict-checksums --errors "-Djeo.version=${JEO_VERSION}" "org.eolang:jeo-maven-plugin:${JEO_VERSION}:help" \
  && chmod -R a+rwx /hone/.m2 \
  && find /hone/.m2 -name "*.lastUpdated" -delete \
  && find /hone/.m2 -name "_remote.repositories" -delete \
  && find /hone/.m2 -name "*.sha1" -delete

ENV MAVEN_OPTS=-Xmx16g
ENV JAVA_OPTS=-Xmx16g

COPY entry.sh ./
RUN chmod a+x entry.sh
COPY rewrite.sh ./
RUN chmod a+x rewrite.sh
COPY rules/ ./rules/

ENTRYPOINT ["/hone/entry.sh"]
