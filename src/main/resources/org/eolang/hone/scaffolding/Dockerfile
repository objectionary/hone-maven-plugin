# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

FROM yegor256/haskell:0.0.1

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.0"

WORKDIR /hone

ENV PHINO=0.0.0.5
RUN cabal update \
    && cabal install --disable-tests --disable-coverage --overwrite-policy=always "phino-${PHINO}" \
    && cp /root/.local/bin/phino /usr/bin \
    && phino --version

COPY in-docker-pom.xml pom.xml

COPY extensions.xml ./.mvn/
RUN mvn --update-snapshots --batch-mode --strict-checksums --errors \
  -Dbuildtime.output.log=true \
  eo:help \
  jeo:help \
  exec:help

COPY entry.sh ./
RUN chmod a+x entry.sh
COPY normalize.sh ./
RUN chmod a+x normalize.sh
COPY rules/ ./rules/

ENTRYPOINT ["/hone/entry.sh"]
