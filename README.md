# Bytecode Optimizing Maven Plugin

[![mvn](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml/badge.svg)](https://github.com/objectionary/hone-maven-plugin/actions/workflows/mvn.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.eolang/hone-maven-plugin.svg)](https://maven-badges.herokuapp.com/maven-central/org.eolang/hone-maven-plugin)
[![Javadoc](https://www.javadoc.io/badge/org.eolang/hone-maven-plugin.svg)](https://www.javadoc.io/doc/org.eolang/hone-maven-plugin)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE.txt)
[![Hits-of-Code](https://hitsofcode.com/github/objectionary/hone-maven-plugin?branch=master&label=Hits-of-Code)](https://hitsofcode.com/github/objectionary/hone-maven-plugin/view?branch=master&label=Hits-of-Code)
![Lines of code](https://sloc.xyz/github/objectionary/hone-maven-plugin)
[![codecov](https://codecov.io/gh/objectionary/hone-maven-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/objectionary/hone-maven-plugin)

## How to Use the Maven Plugin

This [Maven] plugin can optionally optimize your compiled [Bytecode][bytecode] in order to improve runtime performance.
Before using it, make sure that:

- your project builds successfully with Maven
- [Docker] is installed and available on your system
- you’re ready to introduce a post-compilation optimization step

To enable the plugin, simply add the following block to your pom.xml file

```xml
<project>
  [..]
  <build>
    <plugins>
      <plugin>
        <groupId>org.eolang</groupId>
        <artifactId>hone-maven-plugin</artifactId>
        <version>0.20.3</version>
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

## What the Plugin Actually Does

Once the plugin is configured, it performs a series of post-compilation transformation steps on your classes:

1. **Backup**
   - Copies all `.class` files from `target/classes/`
   - Stores them in `target/classes-before-hone/`

2. **Bytecode → XMIR**

   - Uses [jeo-maven-plugin] to transform `.class` files into `.xmir` files
   - Outputs them to `target/hone/jeo-disassemble/`
   - `.xmir` represents [EO] in the [XMIR] XML format

4. **XMIR → 𝜑-calculus (`.phi`)**

    - Uses [phino] to convert `.xmir` files into `.phi` files expressed in [𝜑-calculus]
   - Stores them in `target/hone/phi/`

6. **Optimization**
 
   - Uses [phino] again to apply optimization rules to the `.phi` files
   - Places optimized results into `target/hone/phi-optimized/`

7. **𝜑-calculus → XMIR**

   - Converts optimized `.phi` files back into `.xmir`
   - Stores them in `target/hone/unphi/`

9. **XMIR → Bytecode**

   - Uses [jeo-maven-plugin] to reassemble `.xmir` into new `.class` files
   - Writes them back to `target/classes/`

## Expected Result

When everything completes, you should end up with:

- a bytecode output that still behaves the same as before
- but is expected to have better performance characteristics

The plugin guarantees:

- no functional regressions — your behavior must remain identical
- positive performance changes — code should execute faster

If you notice functional differences or negative performance effects, please [submit a ticket] so it can be investigated and fixed.

### Optional Recommendation

To speed up local builds, you can pre-install [phino] on your system.

## How to Use in Gradle

It’s also possible to use this plugin with [Gradle].
However, the setup process includes a few additional steps compared to Maven.
To configure it properly, you need to:

- define a custom Gradle task that invokes Maven
- ensure that the task runs after compilation
- pass the necessary parameters to Maven so the plugin knows what to optimize
- link the task to Gradle’s build lifecycle

To do this, add the following snippet to your `build.gradle` file:

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
After adding this:

- the hone task will run each time Java sources are compiled
- the plugin will receive the target and class directories it needs
- bytecode will be optimized as part of the build process
- the final output will include the optimized classes
  
See how it works in [this example](src/test/gradle).

## Benchmark

Here is the result of the latest processing of a large Java class
from [JNA](https://github.com/java-native-access/jna):

<!-- benchmark_begin -->
```bash
Benchmark results for optimizing a large Java class:

Before optimization:
  Bytecode size:          152,384 bytes
  Execution time:         127.4 ms

After optimization:
  Bytecode size:          118,742 bytes
  Execution time:         103.1 ms

Result:
  Bytecode reduction:     ~22.1%
  Speedup:                ~1.24× faster
```

The results were calculated in [this GHA job][benchmark-gha]
on 2025-12-17 at 13:53,
on Linux with 4 CPUs.
<!-- benchmark_end -->

## How to Contribute

Contributing is straightforward, and we’re always happy to welcome improvements from the community. To do so, you should:

- fork the repository to your account
- make the necessary changes in your fork
- submit a [pull request][guidelines] once you're done

After you submit your pull request:

- we will review your changes
- ensure they align with our quality standards
- then merge them into the master branch if everything looks good

Before sending your pull request, please make sure to run the full Maven build in order to avoid any unnecessary back-and-forth or build failures:

```bash
mvn clean install -Pqulice
```

### Requirements
To successfully build and contribute, you must have:

- [Maven 3.3+](https://maven.apache.org)
-  Java 11+
- [Docker](https://docs.docker.com/engine/install/) installed.

### Dependency Versions
The versions of:

- [EO]
- [JEO](https://github.com/objectionary/jeo-maven-plugin)

used in this project are specified directly in the `pom.xml` file, so you can reference or adjust them if needed.

[EO]: https://github.com/objectionary/eo
[benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/20305168836
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
