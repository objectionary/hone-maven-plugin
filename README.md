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
        <version>0.26.0</version>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
              <goal>optimize</goal>
              <goal>rmi</goal>
            </goals>
            <configuration>
              <rules>streams/*</rules>
            </configuration>
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
1. Using [jeo-maven-plugin],
transform `.class` files to
`.xmir` [format][XMIR],
which is [EO] in XML, and place them into
the `target/hone/jeo-disassemble/` directory.
1. Using [phino],
convert `.xmir` files to `.phi` files
with [𝜑-calculus] expressions,
and place them into the `target/hone/phi/` directory.
1. Using [phino],
apply a number of optimizations to 𝜑-calculus expressions in the `.phi` files
and place new `.phi` files into
the `target/hone/phi-optimized/` directory.
1. Using [phino],
convert `.phi` files back to `.xmir` files and
place them into the `target/hone/unphi/` directory.
1. Using [jeo-maven-plugin],
transform `.xmir` files back to Bytecode and place `.class` files into
the `target/classes/` directory.

The effect of the plugin should be performance-positive (your code should
work faster) along with no functionality degradation (your code should work
exactly the same as it worked before optimizations). If any of these
is not true, [submit a ticket], we will try to fix.

To make it work faster, you may install [phino] on your machine beforehand.

## How It Works

The most interesting step of the pipeline is the phi-to-phi rewriting,
  where [phino] turns one 𝜑-calculus expression into another
  by applying a fixed set of rules to every `.phi` file
  in `target/hone/phi/` and writing the result into `target/hone/phi-optimized/`.

Each rule lives in its own `.phr` file under
  [`src/main/resources/org/eolang/hone/rules/`](src/main/resources/org/eolang/hone/rules)
  and has the same three-part shape:
  `pattern` describes the sub-expression to look for,
  `result` describes what to put in its place,
  and an optional `where` block defines auxiliary metavariables
  computed by small string functions such as `concat`, `sed`, `join`, and `tau`.
Inside the 𝜑-calculus body,
  identifiers prefixed with `𝐵-` capture whole groups of bindings,
  those prefixed with `𝜏-` capture a single binding name,
  and those prefixed with `𝑒-` capture an atomic sub-expression,
  so that a single rule can match an entire family of concrete expressions.

When the plugin is configured with `<rules>streams/*</rules>`,
  the `Rules` class scans the classpath under that directory,
  collects every `.phr` (and `.yml`) file that matches the pattern,
  and sorts the resulting names alphabetically before passing them on
  (see `Collections.sort(names)` in `Rules.discover()`).
Each filename begins with a numeric prefix
  (for example `101-`, `111-`, `121-`, ..., `701-`, `702-`),
  so the alphabetical sort produces the exact sequence
  in which the rules are intended to fire,
  one by one.
The prefixes also reveal the logical phases of the pipeline:
  the `1xx` group prepares the input
  (it removes self-referencing labels and lowers `invokedynamic` to a lambda),
  the `2xx` group recognises stream operations
  (`filter`, `map`, their primitive variants, `unbox` and `box`),
  the `3xx` group folds every recognised operation
  into a uniform internal `distill` node,
  the `4xx` group fuses adjacent `distill` nodes
  into a single combined one,
  the `5xx` group rewrites the fused chain into one `mapMulti` call,
  the `6xx` group lowers `mapMulti` back into a lambda,
  and the `7xx` group re-introduces `invokedynamic`
  so that the bytecode emitted by JEO is shaped the way the JVM expects.

In the default "big-steps" mode,
  the `rewrite.sh` script invokes `phino rewrite` once per `.phi` file
  and passes every selected rule on the same command line
  as a sequence of `--rule=` arguments in their alphabetical order.
Phino itself then walks through that list,
  trying each rule against every position in the expression tree
  and re-trying until no rule matches anymore,
  capped by the `maxDepth` and `maxCycles` parameters.
The "small-steps" mode (`<smallSteps>true</smallSteps>`)
  is meant for debugging the rules:
  it invokes `phino rewrite` separately for each rule
  and saves the intermediate result as `Foo.phi.01`, `Foo.phi.02`, and so on,
  so that a `diff` between two adjacent files
  reveals exactly which rule changed what.

## How to Use in Gradle

You can use this plugin with [Gradle] too, but it requires
some additional steps. You need to add the following to your `build.gradle` file:

```groovy
task hone(type: Exec, dependsOn: compileJava) {
    commandLine 'mvn',
      "-Dhone.target=${buildDir}",
      "-Dhone.classes=${buildDir.toPath().relativize(sourceSets.main.output.classesDirs.singleFile.toPath())}",
      '-Dhone.rules=streams/*',
      'org.eolang:hone-maven-plugin:0.0.0:build',
      'org.eolang:hone-maven-plugin:0.0.0:optimize'
}
compileJava.finalizedBy hone
classes.dependsOn hone
```

See how it works in [this example](src/test/gradle).

## How to Use in Docker

If you ran `hone-maven-plugin` in a Docker container, you might face issues if
your Docker image doesn't have [phino](https://github.com/objectionary/phino)
installed. In this case, `hone-maven-plugin`
will try to run a Docker container inside your Docker container (DinD).
This might lead to problems with
[volume mounting](https://github.com/objectionary/hone-maven-plugin/pull/458).

While it's technically possible to implement this (by specifying correct
volumes on your host machine), we highly recommend **avoiding** such a setup.

## Benchmark

Here is the result of the latest processing of a large Java class
from [JNA](https://github.com/java-native-access/jna):

<!-- benchmark_begin -->
```text
Input: com/sun/jna/Pointer.class
Size of .class: 22Kb (22Kb bytes)
Size of .xmir after disassemble: 2Mb (2Mb bytes, 52996 lines)
Size of .phi: 617Kb (617Kb bytes, 14843 lines)
Size of .xmir after unphi: 2Mb (2Mb bytes, 52981 lines)
Optimization time: 11s (11146 ms)

```

The results were calculated in [this GHA job][benchmark-gha]
on 2026-05-08 at 15:17,
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
that we use, are defined in the `pom.xml` file.

[EO]: https://github.com/objectionary/eo
[benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/25563002162
[bytecode]: https://en.wikipedia.org/wiki/Java_bytecode
[guidelines]: https://www.yegor256.com/2014/04/15/github-guidelines.html
[Maven]: https://maven.apache.org/
[Docker]: https://docs.docker.com/engine/install/
[submit a ticket]: https://github.com/objectionary/hone-maven-plugin/issues
[Gradle]: https://gradle.org/
[phino]: https://github.com/objectionary/phino
[jeo-maven-plugin]: https://github.com/objectionary/jeo-maven-plugin
[𝜑-calculus]: https://arxiv.org/abs/2111.13384
[XMIR]: https://news.eolang.org/2022-11-25-xmir-guide.html
