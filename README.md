# Bytecode Optimizing Maven Plugin

[![mvn](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml/badge.svg)](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.eolang/hone-maven-plugin.svg)](https://maven-badges.herokuapp.com/maven-central/org.eolang/hone-maven-plugin)
[![Javadoc](http://www.javadoc.io/badge/org.eolang/hone-maven-plugin.svg)](http://www.javadoc.io/doc/org.eolang/hone-maven-plugin)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE.txt)
[![Hits-of-Code](https://hitsofcode.com/github/objectionary/hone-maven-plugin?branch=master&label=Hits-of-Code)](https://hitsofcode.com/github/objectionary/hone-maven-plugin/view?branch=master&label=Hits-of-Code)
![Lines of code](https://sloc.xyz/github/objectionary/hone-maven-plugin)
[![codecov](https://codecov.io/gh/objectionary/hone-maven-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/objectionary/hone-maven-plugin)

This [Apache Maven](https://maven.apache.org/) plugin _may_ optimize
your [Bytecode][bytecode]
after compilation, to make it work faster.
Just add this to your `pom.xml` file
(you must have [Docker](https://docs.docker.com/engine/install/) installed too):

```xml
<project>
  [..]
  <build>
    <plugins>
      <plugin>
        <groupId>org.eolang</groupId>
        <artifactId>hone-maven-plugin</artifactId>
        <version>0.0.27</version>
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
1. Using [eo-maven-plugin](https://github.com/objectionary/eo/eo-maven-plugin),
convert `.xmir` files to `.phi` files
with [𝜑-calculus](https://arxiv.org/abs/2111.13384) expressions,
and place them into the `target/generated-sources/phi/` directory.
1. Using [normalizer](https://github.com/objectionary/normalizer),
apply a number of optimizations to 𝜑-calculus expressions in the `.phi` files
and place new `.phi` files into
the `target/generated-sources/phi-optimized/` directory.
1. Using [eo-maven-plugin](https://github.com/objectionary/eo/eo-maven-plugin),
convert `.phi` files back to `.xmir` files and
place them into the `target/generated-sources/unphi/` directory.
1. Using [jeo-maven-plugin](https://github.com/objectionary/jeo-maven-plugin),
transform `.xmir` files back to Bytecode and place `.class` files into
the `target/classes/` directory.

The effect of the plugin should be performance-positive (your code should
work faster) along with no functionality degradation (your code should work
exactly the same as it worked before optimizations). If any of these
is not true,
[submit a ticket](https://github.com/objectionary/hone-maven-plugin/issues),
we will try to fix.

## Benchmark

Here is the result of the latest processing of a large Java class
from [JNA](https://github.com/java-native-access/jna):

<!-- benchmark_begin -->
```text
Input: com/sun/jna/Pointer.class
Size of .class: 22Kb (22Kb bytes)
Size of .xmir after disassemble: 2Mb (2Mb bytes, 37641 lines)
Size of .phi: 2Mb (2Mb bytes, 52462 lines)
Size of .xmir after unphi: 7Mb (7Mb bytes, 189330 lines)
Optimization time: 3min (153311 ms)

jeo-maven-plugin:unroll-phi   80.017  53.26%
eo-maven-plugin:xmir-to-phi   51.476  34.26%
eo-maven-plugin:phi-to-xmir   14.468  9.63%
jeo-maven-plugin:disassemble  2.53    1.68%
exec-maven-plugin:exec        1.101   0.73%
jeo-maven-plugin:assemble     0.647   0.43%
```

The results were calculated in [this GHA job][benchmark-gha]
on 2024-11-20 at 12:46,
on Linux with 4 CPUs.
<!-- benchmark_end -->

Here is the result of the latest optimization of itself:

<!-- self_benchmark_begin -->
```text
to-phi.xsl                  11714  28.64%
add-refs.xsl                5202   12.72%
set-locators.xsl            3253   7.95%
stars-to-tuples.xsl         3093   7.56%
wrap-method-calls.xsl       2496   6.10%
vars-float-up.xsl           1554   3.80%
cti-adds-errors.xsl         1488   3.64%
wrap-bytes.xsl              1470   3.59%
explicit-data.xsl           1307   3.20%
resolve-aliases.xsl         1306   3.19%
atoms-with-bound-attrs.xsl  1150   2.81%
add-probes.xsl              1137   2.78%
add-default-package.xsl     1114   2.72%
expand-qqs.xsl              1068   2.61%
const-to-dataized.xsl       994    2.43%
expand-aliases.xsl          946    2.31%
```

The results were calculated in [this GHA job][self-benchmark-gha],
on 2024-11-20 at 13:01,
on Linux with 4 CPUs.
For the sake of brevity, we show only the first 16 lines.
<!-- self_benchmark_end -->

## How to Contribute

Fork repository, make changes, then send us
a [pull request][guidelines].
We will review your changes and apply them to the `master` branch shortly,
provided they don't violate our quality standards. To avoid frustration,
before sending us your pull request please run full Maven build:

```bash
mvn clean install -Pqulice
```

You will need [Maven 3.3+](https://maven.apache.org), Java 11+,
and [Docker](https://docs.docker.com/engine/install/) installed.

The versions of [EO](https://github.com/objectionary/eo) and
[JEO](https://github.com/objectionary/jeo-maven-plugin),
that we use, are defined in the `in-docker-pom.xml` file.

[benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/11933394049
[bytecode]: https://en.wikipedia.org/wiki/Java_bytecode
[guidelines]: https://www.yegor256.com/2014/04/15/github-guidelines.html
[self-benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/11933394045
