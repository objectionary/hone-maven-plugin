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

FROM eclipse-temurin:21

LABEL "repository"="https://github.com/objectionary/hone-maven-plugin"
LABEL "maintainer"="Yegor Bugayenko"
LABEL "version"="0.0.0"

# Installing Maven:
ENV MAVEN_VERSION 3.9.6
ENV M2_HOME "/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}"
RUN echo "export M2_HOME=/usr/local/apache-maven/apache-maven-\${MAVEN_VERSION}" >> /root/.profile \
  && wget --quiet "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  && mkdir -p /usr/local/apache-maven \
  && mv "apache-maven-${MAVEN_VERSION}-bin.tar.gz" /usr/local/apache-maven \
  && tar xzvf "/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -C /usr/local/apache-maven/ \
  && update-alternatives --install /usr/bin/mvn mvn "${M2_HOME}/bin/mvn" 1 \
  && update-alternatives --config mvn \
  && mvn -version \
  && bash -c '[[ "$(mvn --version)" =~ "${MAVEN_VERSION}" ]]'

WORKDIR /hone
COPY in-docker-pom.xml /hone/pom.xml

# Warming up Maven cache:
RUN mvn -f /hone eo:help
RUN mvn -f /hone opeo:help
RUN mvn -f /hone jeo:help

COPY entry.sh /hone

ENTRYPOINT ["/hone/entry.sh"]
