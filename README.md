# Bytecode Optimizing Maven Plugin

[![mvn](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml/badge.svg)](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.eolang/hone-maven-plugin.svg)](https://maven-badges.herokuapp.com/maven-central/org.eolang/hone-maven-plugin)
[![Javadoc](https://www.javadoc.io/badge/org.eolang/hone-maven-plugin.svg)](https://www.javadoc.io/doc/org.eolang/hone-maven-plugin)
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
        <version>0.0.29</version>
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
1. Using [phino](https://github.com/objectionary/phino),
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
Size of .xmir after disassemble: 1Mb (1Mb bytes, 29568 lines)
Size of .phi: 627Kb (627Kb bytes, 13486 lines)
Size of .xmir after unphi: 5Mb (5Mb bytes, 130843 lines)
Optimization time: 33min (1987884 ms)

jeo-maven-plugin:unroll-phi   1827.34  92.06%
eo-maven-plugin:xmir-to-phi   137.867  6.95%
eo-maven-plugin:phi-to-xmir   15.644   0.79%
jeo-maven-plugin:disassemble  1.926    0.10%
exec-maven-plugin:exec        1.679    0.08%
jeo-maven-plugin:assemble     0.46     0.02%
```

The results were calculated in [this GHA job][benchmark-gha]
on 2025-01-21 at 13:18,
on Linux with 4 CPUs.
<!-- benchmark_end -->

Here is the result of the latest optimization of itself:

<!-- self_benchmark_begin -->
```text
wrap-bytes.xsl              1588  27.56%
wrap-method-calls.xsl       1535  26.64%
normalize-bytes.xsl         1373  23.83%
atoms-with-bound-attrs.xsl  1266  21.97%
```

The results were calculated in [this GHA job][self-benchmark-gha],
on 2025-01-21 at 12:52,
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

The versions of [EO] and
[JEO](https://github.com/objectionary/jeo-maven-plugin),
that we use, are defined in the `in-docker-pom.xml` file.

[EO]: https://github.com/objectionary/eo
[benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/12887187273
[bytecode]: https://en.wikipedia.org/wiki/Java_bytecode
[guidelines]: https://www.yegor256.com/2014/04/15/github-guidelines.html
[self-benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/12887187268
