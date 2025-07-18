# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

FROM yegor256/haskell:0.0.1

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.4.0"

WORKDIR /hone

ARG PHINO_VERSION=0.0.0.25
RUN cabal update \
    && cabal install --disable-tests --disable-coverage --overwrite-policy=always "phino-${PHINO_VERSION}" \
    && cp /root/.local/bin/phino /usr/bin \
    && phino --version

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
