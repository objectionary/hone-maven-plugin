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
        <version>0.27.3</version>
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

## Optimization Pipeline

The `.phi` file that arrives from [jeo-maven-plugin]
  contains the bytecode of one Java class
  re-encoded as a single 𝜑-expression:
  every instruction (`iload`, `imul`, `invokedynamic`, ...)
  becomes a small formation
  whose `φ` attribute dispatches to a name under `Φ.jeo.opcode`,
  and the metadata of the class
  (its name, access flags, methods, line numbers, ...)
  sits alongside as ordinary bindings.
The rules in `streams/` then walk this expression through
  seven stages of rewriting,
  turning a sequence of low-level instructions
  into a single fused stream operation
  while keeping the resulting expression
  re-assemblable back to valid `.class` bytes by jeo.
The pipeline below mirrors the one described in the [hone paper][hone-paper].

**Stage 1 (rules `101-` to `141-`): clean up and lower `invokedynamic`.**
The Java compiler emits a stream pipeline as a chain of
  `invokedynamic`+`invokeinterface` pairs
  plus a synthetic static method per lambda;
  before anything else can be recognised,
  the noise around those instructions must go.
Rule `101-remove-self-reference-labels` deletes labels and line-number
  entries that no longer point anywhere meaningful,
  `111-invokedynamic-to-lambda` rewrites the `invokedynamic` formation
  into a higher-level `Φ.hone.lambda` pragma that exposes
  the interface, the target method, and the captured arguments,
  `121-` and `131-` normalise the static-vs-instance shape
  of the produced lambda,
  and `141-set-opcode-in-lambda` records the original opcode
  on the pragma so that later stages can reverse the lowering.

**Stage 2 (rules `201-` to `282-`): recognise stream operations as pragmas.**
A `Φ.hone.lambda` immediately followed by an `invokeinterface`
  on a `java.util.stream` class is, semantically, one operation:
  rules `201-lambda-to-filter`, `202-lambda-to-map`,
  `203-lambda-to-primitive-filter`, `204-lambda-to-primitive-map`,
  and `205-lambda-to-unbox`
  match exactly that pair and replace it with a single
  `Φ.hone.filter` / `Φ.hone.map` / `Φ.hone.unbox` pragma.
The paper calls these synthetic formations _pragmas_:
  they look like bytecode instructions
  but carry the information needed to reconstruct one later.
Rules `206-` through `261-` then tidy up the boxing and primitive
  conversions that the compiler inserted around the lambda
  (for example moving an `Integer.valueOf` call from outside the lambda
  into a `Φ.hone.box` pragma),
  and `271-`, `272-` remove the now-useless `CHECKCAST` and
  object-to-primitive conversions that the pragma made redundant.
Rules `281-` and `282-` insert a `DUP` in front of every `filter`
  so the value can be both tested and forwarded
  without re-running the predicate.

**Stage 3 (rules `301-` to `311-`): fold every operation into `distill`.**
Mapping and filtering still look different at this point:
  `map` rewrites a value, `filter` drops one.
The third stage erases that difference
  by rewriting every `Φ.hone.map`, `Φ.hone.filter`,
  and their primitive variants
  into a uniform `Φ.hone.distill` pragma
  whose body is a piece of bytecode that accepts an item
  and either falls through (keep), returns early (drop),
  or replaces the local variable (transform).
Rules `301-dup-to-distill`, `302-transform-to-distill`,
  `303-type-to-distill`, `304-primitive-filter-to-distill`,
  `305-object-filter-to-distill`, and `306-map-to-distill`
  handle the individual cases,
  and `311-load-this-in-pre-distill` prepares the stack
  for instance-method lambdas.
After this stage,
  a pipeline of `.map().filter().mapToInt()...`
  has been reduced to a flat list of `distill` pragmas
  inside the same method.

**Stage 4 (rules `401-` to `431-`): fuse adjacent `distill` pragmas.**
This is the optimization that actually saves work at runtime.
Rule `401-fuse` looks for two consecutive `distill` pragmas
  inside the same method body
  and concatenates their lambda bodies into a single `distill`,
  so that the JVM has to traverse the stream pipeline only once
  instead of pumping every element through several `Consumer` objects.
The rule fires repeatedly under phino's fixed-point evaluation:
  if a method had five chained operations,
  `401-fuse` will fire four times,
  collapsing all of them into one.
Concatenation only works when the type that flows between
  two pragmas is the same;
  when one pragma returns a primitive and the next expects a wrapper
  (or vice versa),
  rule `411-box-distill-unbox-to-primitive-distill`
  unifies the boundary
  by pushing the boxing/unboxing inside the body,
  and `421-`/`422-` align the head and tail types
  with the surrounding bytecode.
Rule `431-dup-before-filter-distill` re-applies the `DUP`
  fix-up after a fusion changed which value is being filtered.

**Stage 5 (rules `501-` and `511-`): emit a single `mapMulti` call.**
At this point each fused `distill` is one big anonymous function
  that consumes an item.
The JDK already offers an idiomatic shape for exactly that,
  `Stream.mapMulti(BiConsumer)`,
  whose second argument is the downstream sink.
Rule `501-distill-to-mapMulti` rewrites the `distill` pragma
  into a `Φ.hone.mapMulti` pragma
  by appending a `c.accept(x)` call to the body
  (where `c` is the `BiConsumer` argument),
  and `511-distill-lambda-to-method` extracts the body
  into a real private static method on the class
  so that it can be invoked through a method handle.

**Stage 6 (rules `601-` to `603-`): pragmas back to lambdas and `invokeinterface`.**
Once the optimizer has done its job,
  the `Φ.hone.*` pragmas have to disappear:
  jeo doesn't know about them and can't translate them to bytecode.
Rule `601-mapMulti-to-lambda` rewrites the `Φ.hone.mapMulti` pragma
  back into a `Φ.hone.lambda` formation paired with an `invokeinterface`
  call on `Stream.mapMulti`,
  reversing the recognition that stages 2 and 5 performed.
Rule `602-box-to-boxed` rewrites the `Φ.hone.box` pragma
  directly into an `invokeinterface` on `Integer.valueOf` (and its siblings),
  since boxing is just a static method call and does not need a lambda.
Rule `603-unbox-to-lambda` rewrites the `Φ.hone.unbox` pragma
  back into a `Φ.hone.lambda` formation,
  matching the shape that `205-lambda-to-unbox` originally consumed.
After this stage, every pragma is gone;
  what remains are `Φ.hone.lambda` formations and ordinary bytecode opcodes.

**Stage 7 (rules `701-` and `702-`): lambdas back to `invokedynamic`.**
This stage is the inverse of stage 1 and the final bytecode-level fixup.
Rule `701-static-lambda-to-invokedynamic` handles
  the lambdas whose body is a static method,
  and `702-nonstatic-lambda-to-invokedynamic`
  handles the ones that capture a `this` reference.
Both rules lower a `Φ.hone.lambda` formation
  into a full `Φ.jeo.opcode.invokedynamic` formation,
  reconstructing the `LambdaMetafactory.metafactory` call site,
  the bridge and target method handles,
  and the method-type descriptors
  that the JVM expects to find on the constant pool side of an `invokedynamic`.
The result is a `.phi` file
  that contains regular bytecode instructions again,
  just fewer of them and arranged for a single pass through the stream,
  ready to be converted back to `.xmir` by phino
  and then to `.class` by jeo.

This pipeline never invents bytecode that the JVM cannot run.
Even when the fusion stage produces a method body
  that the Java compiler would reject
  (for example one local variable holding values of two unrelated
  reference types in succession),
  the JVM itself does not type-check locals at runtime,
  so the resulting `.class` file still verifies and executes.
The [hone paper][hone-paper] discusses this property,
  along with the cases (mixing primitives and wrappers across a fuse)
  where an explicit boxing `distill` must be inserted to keep the JVM happy.

## Why Short-Circuiting Operations Are Not Fused

The pipeline deliberately fuses only non-short-circuiting operations
  (`filter`, `map`, `peek`, `distinct`, `skip`, `dropWhile`, `flatMap`,
  the primitive conversions, and `mapMulti` itself);
  the two short-circuiting intermediate operations of the JDK,
  `limit()` and `takeWhile()`, are left untouched.
The reason is structural:
  everything the fusion produces is one `Stream.mapMulti(BiConsumer)` stage,
  and a `mapMulti` body may keep, drop, or transform an element,
  but it has no access to the `Sink.cancellationRequested()` channel —
  there is no way for the fused body to ask upstream to stop traversal.
A stateful guard inside the body
  (a countdown for `limit`, exactly like the one `308-skip-to-distill`
  builds for `skip` but with the branch inverted,
  or a sticky latch for `takeWhile`)
  would reproduce the values of the original pipeline
  but not its traversal contract:
  the source keeps being pulled after the cut-off.
On an unbounded source such as `Stream.iterate(1, n -> n + 2).limit(5)`
  the rewritten program would simply never terminate,
  and on a bounded one it would traverse the entire source —
  `list.stream().limit(3)` would visit every element of the list —
  pessimizing the very behavior `limit` exists to provide.
A semantics-preserving variant
  (keep the native `Stream.limit(n)` right after the fused `mapMulti`
  so that upstream cancellation still happens)
  is only exact when no element-dropping operation is fused after the guard,
  and would save a single `invokedynamic` in that narrow shape —
  not worth the rule complexity.
Instead, `limit` and `takeWhile` act as barriers:
  the chains of operations on either side of them
  fuse into their own `mapMulti` calls independently,
  while the native short-circuiting call stays in place.

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
Optimization time: 10s (9727 ms)

```

The results were calculated in [this GHA job][benchmark-gha]
on 2026-06-09 at 08:04,
on Linux with 4 CPUs.
<!-- benchmark_end -->

## Coverage

Here is the result of running the plugin against a number of mid-size,
mature open-source Java projects from GitHub. Each project is pinned to
a specific commit (rather than its moving `master` head) so that the
table stays reproducible across runs. For each pinned commit, the
workflow shallow-fetches that revision, runs `mvn clean test` to record
a baseline, applies `hone-maven-plugin` to every `target/classes/`
directory, and then re-runs the tests to check that the bytecode still
passes the project's own test suite. The number of modified `.class`
files is computed by comparing MD5 checksums before and after. The
`Edits` column reads as `NN/MM`: `NN` is that count of modified classes
and `MM` is the number of compiled `.class` files whose bytecode
references `Stream`, `IntStream`, `LongStream`, or `DoubleStream` — that
is, how many of the stream-using classes the plugin actually rewrote.

<!-- coverage_begin -->
| Repository | Forks | LoC | Classes | Before | Edits | Hone | After |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [commons-cli](https://github.com/apache/commons-cli/commit/17de58009bf9dada031a7b3891014c6de5a089bf) | 248 | 11243 | 56 | 6s | 4/2 | 17s | 6s |
| [jsoup](https://github.com/jhy/jsoup/commit/a7ec14364e2f9f84ecb795814b4fd05d028f709d) | 2290 | 39031 | 317 | 14s | 13/8 | 35s | 14s |
| [jackson-core](https://github.com/FasterXML/jackson-core/commit/3c0bcb749b106d6b80cd1d1d133cf1c97b66e752) | 889 | 86966 | 176 | 28s | 1/0 | 29s | 29s |
| [commons-codec](https://github.com/apache/commons-codec/commit/77fcf89711a0e20393105a1247c41968f6eb58d4) | 268 | 27186 | 150 | 22s | 3/4 | 22s | 21s |
| [commons-csv](https://github.com/apache/commons-csv/commit/6f93c7edfa0f758f757227b1d30588411fdbf669) | 300 | 10092 | 20 | 9s | 2/3 | 13s | 9s |
| [commons-text](https://github.com/apache/commons-text/commit/283eaf49586331a7adc0b28fdfa5e8f09df87123) | 285 | 28298 | 168 | 34s | 11/8 | 27s | 35s |
| [JSON-java](https://github.com/stleary/JSON-java/commit/d749ee16abd084ae94785c7afe954de25d921a5d) | 2593 | 16352 | 31 | 5s | 1/0 | 17s | 5s |
| [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram/commit/de84b0a7de2378abfc405da503bf4898e84ea98e) | 269 | 13086 | 97 | 51s | 0/0 | 18s | 51s |
| [vavr](https://github.com/vavr-io/vavr/commit/1c9b7af87949df5232c81253a7fc59fde3598aec) | 664 | 74130 | 435 | 44s | 168/24 | 108s | 41s |
| [junit4](https://github.com/junit-team/junit4/commit/300468b1efd48d76fac2f7bd6d576846dcbbf5ed) | 3270 | 31232 | 349 | 9s | 11/0 | 29s | 9s |
| [commons-lang](https://github.com/apache/commons-lang/commit/f51d0615d034ae997611ac01fd4c79823122372b) | 1972 | 104400 | 433 | 380s | 25/34 | 58s | 352s ⚠️ |
| [commons-collections](https://github.com/apache/commons-collections/commit/b533ad6c0527027ba08d9a9891b09d7eaf1af98e) | 518 | 73500 | 614 | 25s | 101/12 | 62s | 25s |
| [commons-pool](https://github.com/apache/commons-pool/commit/772089fa7b441b61fbbb1663cdbcab2158dbc9a5) | 302 | 16596 | 87 | 388s | 3/7 | 22s | 383s |
| [commons-configuration](https://github.com/apache/commons-configuration/commit/6ef60965b273110bd83068312420c32421fef11d) | 148 | 52534 | 400 | 32s | 31/27 | 43s | 32s |
| [commons-validator](https://github.com/apache/commons-validator/commit/2747da8d0ff7d3aa971369543239160f7d723da9) | 178 | 17086 | 88 | 8s | 3/0 | 40s | 7s |

The results were calculated in [this GHA job][coverage-gha]
on 2026-06-09 at 17:54,
on Linux with 4 CPUs.
<!-- coverage_end -->

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
[benchmark-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/27192081741
[coverage-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/27220821499
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
[hone-paper]: https://github.com/objectionary/hone-paper
