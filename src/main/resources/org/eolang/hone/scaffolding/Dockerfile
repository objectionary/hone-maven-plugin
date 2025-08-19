# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
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
    lsb-release=* \
    software-properties-common=* \
    zlib1g-dev=* \
  && sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen \
  && locale-gen \
  && update-locale LANG=en_US.UTF-8 \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

ARG GHC=9.6.7
ARG CABAL=3.12.1.0
RUN gpg --batch --recv-keys ECA44F5A172EDAD947F39E3D4275CDA6A29BED43 \
  && curl -sSL https://downloads.haskell.org/~ghcup/x86_64-linux-ghcup -o /usr/bin/ghcup \
  && chmod +x /usr/bin/ghcup \
  && ghcup config set gpg-setting GPGStrict \
  && ghcup -v install ghc --isolate /usr/local --force "${GHC}" \
  && ghcup -v install cabal --isolate /usr/local/bin --force "${CABAL}" \
  && cabal update

ARG PHINO_VERSION=0.0.0.0
RUN cabal install --disable-tests --disable-coverage --overwrite-policy=always "phino-${PHINO_VERSION}" \
  && find /root -name phino -type f 2>/dev/null | head -1 | xargs -I {} cp {} /usr/local/bin/phino \
  && chmod +x /usr/local/bin/phino \
  && /usr/local/bin/phino --version

FROM eclipse-temurin:17.0.16_8-jre-jammy

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.0"

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
    libgmp10=* \
    libxml2-utils=* \
    tree=* \
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

COPY in-docker-pom.xml pom.xml
COPY extensions.xml ./.mvn/
COPY settings.xml /hone/

# Pre-download Maven dependencies and clean up unnecessary plugins
RUN mkdir -p /hone/.m2 \
  && mvn --settings=/hone/settings.xml --update-snapshots --batch-mode --strict-checksums --errors -Dbuildtime.output.log=true eo:help jeo:help exec:help \
  && chmod -R a+rwx /hone/.m2 \
  && find /hone/.m2 -name "*.lastUpdated" -delete \
  && find /hone/.m2 -name "_remote.repositories" -delete \
  && find /hone/.m2 -name "*.sha1" -delete

ENV MAVEN_OPTS=-Xmx16g
ENV JAVA_OPTS=-Xmx16g

COPY entry.sh ./
RUN chmod a+x entry.sh
COPY normalize.sh ./
RUN chmod a+x normalize.sh
COPY rules/ ./rules/

ENTRYPOINT ["/hone/entry.sh"]
