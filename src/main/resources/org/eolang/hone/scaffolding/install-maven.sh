#!/bin/bash
# The MIT License (MIT)
#
# SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
# SPDX-License-Identifier: MIT

set -ex

if mvn --version 2>/dev/null; then
  echo "Maven already installed"
  exit
fi

if [ -z "${JAVA_HOME}" ]; then
  JAVA_HOME=$(find /usr/lib/jvm -name 'java-*' | head -1)
  export JAVA_HOME
fi

MAVEN_VERSION=3.9.9
M2_HOME="/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}"

echo "export M2_HOME=/usr/local/apache-maven/apache-maven-\${MAVEN_VERSION}" >> /root/.profile
wget --quiet "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
mkdir -p /usr/local/apache-maven
mv "apache-maven-${MAVEN_VERSION}-bin.tar.gz" /usr/local/apache-maven
tar xzvf "/usr/local/apache-maven/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -C /usr/local/apache-maven/
update-alternatives --install /usr/bin/mvn mvn "${M2_HOME}/bin/mvn" 1
update-alternatives --config mvn

mvn -version
bash -c '[[ "$(mvn --version)" =~ "${MAVEN_VERSION}" ]]'
