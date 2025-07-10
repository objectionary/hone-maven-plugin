# Bytecode Optimizing Maven Plugin

[![mvn](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml/badge.svg)](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.eolang/hone-maven-plugin.svg)](https://maven-badges.herokuapp.com/maven-central/org.eolang/hone-maven-plugin)
[![Javadoc](https://www.javadoc.io/badge/org.eolang/hone-maven-plugin.svg)](https://www.javadoc.io/doc/org.eolang/hone-maven-plugin)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE.txt)
[![Hits-of-Code](https://hitsofcode.com/github/objectionary/hone-maven-plugin?branch=master&label=Hits-of-Code)](https://hitsofcode.com/github/objectionary/hone-maven-plugin/view?branch=master&label=Hits-of-Code)
![Lines of code](https://sloc.xyz/github/objectionary/hone-maven-plugin)
[![codecov](https://codecov.io/gh/objectionary/hone-maven-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/objectionary/hone-maven-plugin)

This [Maven] plugin _may_ optimize your [Bytecode][bytecode] after compilation,
  to make it work faster.
Just add this to your `pom.xml` file (you must have [Docker] installed too):

```xml
<project>
  [..]
  <build>
    <plugins>
      <plugin>
        <groupId>org.eolang</groupId>
        <artifactId>hone-maven-plugin</artifactId>
        <version>0.1.1</version>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
              <goal>optimize</goal>
              <goal>rmi</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

The plugin will do exactly the following:

1. Take Bytecode `.class` files from the `target/classes/` directory and copy
all of them to the `target/classes-before-hone/` directory (as a backup).
1. Using [jeo-maven-plugin](https://github.com/objectionary/jeo-maven-plugin),
transform `.class` files to
`.xmir` [format](https://news.eolang.org/2022-11-25-xmir-guide.html),
which is [EO](https://www.eolang.org) in XML, and place them into
the `target/generated-sources/jeo-disassemble/` directory.
1. Using [phino](https://github.com/objectionary/phino),
convert `.xmir` files to `.phi` files
with [𝜑-calculus](https://arxiv.org/abs/2111.13384) expressions,
and place them into the `target/generated-sources/phi/` directory.
1. Using [phino](https://github.com/objectionary/phino),
apply a number of optimizations to 𝜑-calculus expressions in the `.phi` files
and place new `.phi` files into
the `target/generated-sources/phi-optimized/` directory.
1. Using [phino](https://github.com/objectionary/phino),
convert `.phi` files back to `.xmir` files and
place them into the `target/generated-sources/unphi/` directory.
1. Using [jeo-maven-plugin](https://github.com/objectionary/jeo-maven-plugin),
transform `.xmir` files back to Bytecode and place `.class` files into
the `target/classes/` directory.

The effect of the plugin should be performance-positive (your code should
work faster) along with no functionality degradation (your code should work
exactly the same as it worked before optimizations). If any of these
is not true, [submit a ticket], we will try to fix.

## Benchmark

Here is the result of the latest processing of a large Java class
from [JNA](https://github.com/java-native-access/jna):

<!-- benchmark_begin -->
```text
Input: com/sun/jna/Pointer.class
Size of .class: 22Kb (22Kb bytes)
Size of .xmir after disassemble: 2Mb (2Mb bytes, 56524 lines)
Size of .phi: 643Kb (643Kb bytes, 19153 lines)
Size of .xmir after unphi: 2Mb (2Mb bytes, 56509 lines)
Optimization time: 8s (7566 ms)

jeo-maven-plugin:disassemble  2.355  44.98%
exec-maven-plugin:exec        1.96   37.43%
jeo-maven-plugin:assemble     0.921  17.59%
```

The results were calculated in [this GHA job][benchmark-gha]
on 2025-07-10 at 06:09,
on Linux with 4 CPUs.
<!-- benchmark_end -->

## How to Contribute

Fork repository, make changes, then send us a [pull request][guidelines].
We will review your changes and apply them to the `master` branch shortly,
provided they don't violate our quality standards. To avoid frustration,
before sending us your pull request please run full Maven build:

```bash
mvn clean install -Pqulice
```

You will need [Maven 3.3+](https://maven.apache.org), Java 11+,
and [Docker](https://docs.docker.com/engine/install/) installed.

The versions of [EO] and
[JEO](https://github.com/objectionary/jeo-maven-plugin),
that we use, are defined in the `in-docker-pom.xml` file.

[EO]: https://github.com/objectionary/eo
[benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/16187339014
[bytecode]: https://en.wikipedia.org/wiki/Java_bytecode
[guidelines]: https://www.yegor256.com/2014/04/15/github-guidelines.html
[Maven]: https://maven.apache.org/
[Docker]: https://docs.docker.com/engine/install/
[submit a ticket]: https://github.com/objectionary/hone-maven-plugin/issues
