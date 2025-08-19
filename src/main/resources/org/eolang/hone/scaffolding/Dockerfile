# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

FROM ubuntu:24.04

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.0"

ENV DEBIAN_FRONTEND=noninteractive

WORKDIR /hone

# Install system dependencies:
RUN apt-get update \
  && apt-get install --yes --no-install-recommends \
    build-essential=* \
    ca-certificates=* \
    curl=* wget=* \
    gnupg2=* \
    libgmp-dev=* \
    libxml2-utils=* \
    lsb-release=* \
    openssl=* \
    software-properties-common=* \
    tree=* \
    unzip=* \
    zlib1g-dev=* \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Istall Java:
ENV JAVA_OPTS=-Xmx1g
ENV JAVA_VERSION=17
ENV JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}
RUN apt-get --yes --fix-missing update \
  && apt-get --yes --no-install-recommends install ca-certificates-java=* ca-certificates=* "openjdk-${JAVA_VERSION}-jdk=*" \
  && update-ca-certificates \
  && ln -s "/usr/lib/jvm/$(find /usr/lib/jvm -name "java-1.${JAVA_VERSION}*" -exec basename {} \;)" "/usr/lib/jvm/java-${JAVA_VERSION}" \
  && echo "export JAVA_HOME=/usr/lib/jvm/java-${JAVA_VERSION}" >> "${HOME}/.profile" \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Istall Maven
ENV MAVEN_OPTS=-Xmx1g
ENV MAVEN_VERSION=3.9.9
ENV M2_HOME=/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}
RUN echo "export M2_HOME=/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}" >> "${HOME}/.profile" \
  && wget --quiet "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  && mkdir -p /usr/local/apache-maven \
  && mv "apache-maven-${MAVEN_VERSION}-bin.tar.gz" /usr/local/apache-maven \
  && tar xzvf "/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -C /usr/local/apache-maven/ \
  && update-alternatives --install /usr/bin/mvn mvn "${M2_HOME}/bin/mvn" 1 \
  && update-alternatives --config mvn \
  && mvn dependency:get -Dartifact=junit:junit:4.11

# Install Cabal and GHC
ARG GHC=9.6.7
ARG CABAL=3.12.1.0
RUN gpg --batch --recv-keys ECA44F5A172EDAD947F39E3D4275CDA6A29BED43 \
  && curl https://downloads.haskell.org/~ghcup/x86_64-linux-ghcup > /usr/bin/ghcup \
  && chmod +x /usr/bin/ghcup \
  && ghcup config set gpg-setting GPGStrict \
  && ghcup -v install ghc --isolate /usr/local --force "${GHC}" \
  && ghcup -v install cabal --isolate /usr/local/bin --force "${CABAL}" \
  && cabal update \
  && ghc --version \
  && cabal --version

# The value to be set in BuildMojo or in default-phino-version.txt file:
ARG PHINO_VERSION=0.0.0.0
RUN cabal update \
    && cabal install --disable-tests --disable-coverage --overwrite-policy=always "phino-${PHINO_VERSION}" \
    && cp /root/.local/bin/phino /usr/bin \
    && phino --version

# Remove unnecessary files to reduce image size
RUN rm -rf /root/.cabal \
    && rm -rf /root/.local/state/cabal \
    && rm -rf /usr/local/bin/cabal \
    && apt-get remove --purge -y cabal-install \
    && apt-get autoremove -y \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY in-docker-pom.xml pom.xml

COPY extensions.xml ./.mvn/
COPY settings.xml /hone/
RUN mkdir -p /hone/.m2 \
    && mvn --settings=/hone/settings.xml --update-snapshots --batch-mode --strict-checksums --errors -Dbuildtime.output.log=true eo:help jeo:help exec:help \
    && chmod -R a+rwx /hone/.m2

ENV MAVEN_OPTS=-Xmx16g
ENV JAVA_OPTS=-Xmx16g

COPY entry.sh ./
RUN chmod a+x entry.sh
COPY normalize.sh ./
RUN chmod a+x normalize.sh
COPY rules/ ./rules/

ENTRYPOINT ["/hone/entry.sh"]
