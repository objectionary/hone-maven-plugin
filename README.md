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
        <version>0.29.4</version>
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
A lambda that CAPTURES something has a non-empty factory descriptor,
  which `111-` declines,
  so each capturing operator gets a lifting rule of its own —
  `112-` for `map`, `113-capturing-invokedynamic-to-filter` for `filter`,
  and `113-capturing-invokedynamic-to-dropwhile` for `dropWhile` —
  paired with a `11x-` rule that peels the captured pushes
  into the shared state list the fused body reads them back from.

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
Two operations carry no lambda at all —
  `distinct()` and `skip(n)` —
  so there is no pair to match:
  rules `216-recognize-distinct` and `220-recognize-skip`
  lift the bare call instead,
  and `212-recognize-primitive-distinct` and
  `212-recognize-primitive-skip` lift the `IntStream` ones (#996).
Because those two carry no signature to read an element type off,
  `211-box-unbox-primitive-function` cannot wrap them
  the way it wraps every other primitive operation,
  so `214-box-unbox-primitive-stateful` builds
  the `boxed()` / `mapToInt` sandwich around them from the stream
  interface instead,
  and the object folds take it from there unchanged.

An operation whose argument is an OBJECT rather than a lambda
  carries no `invokedynamic` either:
  `.map(new Foo())` compiles to
  `new` / `dup` / `invokespecial` / `invokeinterface`,
  so `111-` never fires
  and nothing downstream could see the operation at all (#635).
Rules `215-recognize-named-map` and `215-recognize-named-filter`
  lift that run the way `216-` lifts a bare `distinct`,
  with the constructor's own pushes bound whole when it captures (#1008).
A capturing constructor is taken whatever its arguments weigh (#1029).
The run being lifted is javac's own
  and stands at the depth javac sized `max_stack` for,
  so relocating it into `502`'s state append
  costs the two cells of the `ArrayList` beneath it and nothing else —
  the arguments cancel.
The fixed bump of 6 that `502` applies
  is sized for `307`'s and `308`'s appends,
  which materialise a `HashSet` and a `long[]`
  that were never in the original bytecode,
  and it covers a relocated append several times over.
An operator that is LOADED rather than built —
  out of a parameter, a local, or a static field —
  has no such run to anchor on,
  and the lone `aload` in front of the call
  cannot be told apart from the receiver stream's own,
  so `215-recognize-ref-map` and `215-recognize-ref-filter`
  key on the call's own descriptor instead,
  which names the functional interface,
  and take the opcode adjacent to it —
  which a one-argument call site guarantees is the argument (#1014).
Both shapes mint the same pragma,
  differing only in what its `make` block holds,
  and because `Function.apply` is erased —
  the call site javac emits says only `java.lang.Object` —
  `247-` reads the element type off the pragma that consumes it
  and `317-` narrows the item there with a single `CHECKCAST`.

One operation gets no pragma of its own:
  `IntStream.mapToObj(...)` crosses from a primitive stream
  to a reference one,
  and there is no `IntStream.mapMultiToObj` for stage 5 to emit.
Rule `208-mapToObj-to-boxed-map` moves the crossing instead,
  rewriting it into two pragmas that already exist —
  the `Φ.hone.box` of a `boxed()`
  and a plain `Φ.hone.map` carrying the original mapper —
  which puts the operation on the reference side of the crossing,
  where everything downstream can fuse it.
Rules `206-` through `261-` then tidy up the boxing and primitive
  conversions that the compiler inserted around the lambda
  (for example moving an `Integer.valueOf` call from outside the lambda
  into a `Φ.hone.box` pragma),
  splicing a `Wrapper.xValue()` wherever one pragma leaves a reference
  and the next one wants the primitive
  (`241-` to `246-`, where `245-` and `246-` are the two sides
  of the seam a lifted primitive `distinct` or `skip` opens,
  since neither pragma carries a type to read),
  and `271-`, `272-` remove the now-useless `CHECKCAST` and
  object-to-primitive conversions that the pragma made redundant.
Rules `281-` and `282-` insert a `DUP` in front of every `filter`
  so the value can be both tested and forwarded
  without re-running the predicate,
  and `283-` does the same behind an operator that carries state
  (a `distinct`, a `skip`, a `dropWhile`, a capturing one,
  or a map over an object argument).

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

**Stage 4 (rules `401-` to `461-`): fuse adjacent `distill` pragmas.**
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
Rule `413-dup-before-filter-distill` re-applies the `DUP`
  fix-up for the filter that opens a fused run,
  which has no predecessor for `281-` to splice a guard behind;
  it deliberately runs ahead of `421-`,
  so that a head unboxing lands in front of the guard
  and the copy the predicate eats is the primitive it expects.

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

The same blind spot reaches `flatMap` from the other direction (#736).
A `flatMap` is fusable — `455-flatMap-to-mapMulti` and its primitive siblings
  rewrite it into a `mapMulti` whose adapter drains each inner stream with
  `result.sequential().forEach(c)` — but that adapter, being just a `mapMulti`
  body, cannot see `cancellationRequested()` either.
The JDK's `ReferencePipeline.flatMap` does forward cancellation:
  once a downstream short-circuit has fired it stops part-way through an inner
  stream, so `Stream.of(1L).flatMap(x -> Stream.iterate(x, i -> i + 1)).limit(5)`
  yields five elements and terminates; the fused `forEach` adapter, by contrast,
  runs to completion for every source element and hangs forever on the
  unbounded inner stream.
Because a `mapMulti` cannot honour cancellation, the cure mirrors the
  revert-on-parallel guard above rather than patching the adapter:
  `454-flatMap-revert-on-short-circuit` reverts the lifted `flatMap` back to its
  native call whenever the same method body reaches a `limit`, `takeWhile`,
  `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch` or `gather`
  downstream of it, so those `flatMap`s stay unfused.
Covering those eight names takes two rules, because by the time the 4xx pass
  runs they no longer share a shape (#816):
  `limit`, `findFirst`, `findAny` and `gather` take no lambda and are still
  plain opcodes,
  while `takeWhile`, `anyMatch`, `allMatch` and `noneMatch` each take one and
  have therefore been folded into a `Φ.hone.lambda` by
  `111-invokedynamic-to-lambda`.
`454-flatMap-revert-on-short-circuit` matches the opcode shape and its sibling
  `454-flatMap-revert-on-lifted-short-circuit` the folded one, with the same
  guard and the same eight-name list in both;
  until the second one existed the four lambda-taking names went unguarded, the
  `flatMap` was fused anyway, and the hang above stayed reachable through
  `.takeWhile(i -> i < 5)`.
`gather` joined the list last (#972).
A `Gatherer` whose integrator returns `false` stops the upstream
  and the JDK honours that through `flatMap` exactly as it honours a `limit`,
  so the same pipeline hangs when fused;
  the operator was still a preview API when the list was drawn up
  and reached final API status in Java 24.
It is the one name the end-to-end fixtures cannot reach,
  because they are compiled at `maven.compiler.release=17` —
  the two single-rule packs are what keep it on the list.

## Why `skip` Is Only Fused on Sequential Pipelines

`skip(n)` is fused (it lowers to a countdown counter inside the
  `mapMulti` body, see `308-skip-to-distill`), but only when the pipeline
  provably stays sequential.
The fused counter is correct only if arrival order equals encounter order:
  on a parallel stream several `ForkJoin` workers drive the one captured
  counter concurrently, so it both races and counts in arrival order rather
  than encounter order, dropping the wrong elements
  (this is the order-dependent analogue of the parallel `distinct` race
  fixed in #715, except that no thread-safe data structure can recover
  encounter order once it is lost).
The JDK's native `Stream.skip(n)` honours the ordered/parallel contract,
  so when a method contains a `parallel()` or `parallelStream()` call the
  rules `222-parallel-reverts-skip` and `223-parallel-reverts-skip-after`
  revert the recognised `skip` back to the native call before `308` can fold
  it (#719, #717).
The same revert-on-parallel guard applies to `dropWhile`, which is
  order-dependent for the same reason: the rules
  `226-parallel-reverts-dropwhile` and `227-parallel-reverts-dropwhile-after`
  revert the recognised `dropWhile` (rebuilding its predicate lambda) back to
  the native call before `310` can fold it (#862).

## Why `distinct` Is Only Fused on Sequential or Unordered Pipelines

`distinct()` is fused (it lowers to a seen-set check inside the `mapMulti`
  body, see `307-distinct-to-distill`), but only when the pipeline provably
  stays sequential.
The fused seen-set is a plain `java.util.HashSet`, and the fold that builds
  it (`307-distinct-to-distill`) is reached only on a pipeline that stays on
  one thread; the one fold that can run wide builds a
  `Collections.synchronizedSet(new HashSet<>())` instead, which is what #715
  asked for when it closed the data race on parallel streams.
Making `add()` atomic, however, only fixes _whether_ duplicates leak, not
  _which_ element survives among equals.
The JDK's `distinct()` is documented _stable_ on an ordered stream: among
  equal elements it keeps the one first in encounter order.
A single shared set populated by several `ForkJoin` workers instead keeps
  whichever equal element wins the race to `add()` — arrival-order-first,
  which is neither encounter-order-first nor deterministic across runs.
For primitives and substitutable-`equals` values this is unobservable, but
  for equal-but-distinguishable objects (an `equals`/`hashCode` that collapses
  instances differing in a payload field) the surviving object changes from
  run to run, silently violating the stability contract.
The JDK's native `Stream.distinct()` honours the ordered/parallel contract,
  so — exactly as for `skip` — when a method contains a `parallel()` or
  `parallelStream()` call the rules `224-parallel-reverts-distinct` and
  `225-parallel-reverts-distinct-after` revert the recognised `distinct` back
  to the native call before `307` can fold it (#738).

That stability contract, however, holds only while the stream is _ordered_,
  and `BaseStream.unordered()` is exactly the call that drops it:
  an unordered `distinct()` is specified to keep _any_ element among equals,
  which is precisely what a single shared seen-set already delivers.
So `217-unordered-permits-distinct` gives the fusion back
  when an explicit `unordered()` call precedes the `distinct()`,
  by stamping the pragma with a mark that `224` and `225` decline (#975):

```java
// reverted: parallel and ordered, so stability applies
WORDS.stream().parallel().map(s -> s + "!").distinct()

// fused: unordered before the distinct, so any survivor is legal
WORDS.stream().unordered().parallel().map(s -> s + "!").distinct()

// reverted: unordered() marks the stream unordered from that point ON,
// so a distinct ahead of it still ran on an ordered stream
WORDS.stream().parallel().map(s -> s + "!").distinct().unordered()
```

Whichever of the two folds fires, the set has to accept `null`, because
  `distinct()` does:
  the JDK runs the sequential ordered case through a `LinkedHashSet`,
  which holds one `null` happily.
Both folds used to build the set with `ConcurrentHashMap.newKeySet()`,
  whose `add()` throws `NullPointerException` on `null` —
  and it threw from inside the synthetic `distill_…` lambda,
  so a pipeline that carried nulls before the rewrite died after it
  with a stack trace pointing nowhere near the user's code (#970):

```java
// 2 before the rewrite, NullPointerException after it
Stream.of("a", null, "a", null).distinct().map(s -> s).count();
```

`HashSet` restores that behaviour and costs less per element,
  and `Collections.synchronizedSet` keeps it on the unordered fold,
  which is the only one that pays for a lock.

## Why a `long` Element Used To Fuse Less Than an `int`

Nothing stateful fused on a `LongStream` or a `DoubleStream` until #1012,
  and the reason was a local-variable layout rather than a contract.
`512` lowers a stateful distill into a wrapper
  whose locals it writes out itself:
  the captured `List` at 0, the item at 1, the consumer at 2,
  `521`'s fetch counter at 3,
  and `310`'s dropWhile latch scratch at 4 and 5.
A `long` or a `double` takes _two_ slots,
  so a wide item slides every one of those by one,
  and `521` — which sees a bare fetch marker —
  had no way to learn the width of the item
  in the method it was lowering into.

So `distinct()`, `skip()` and `dropWhile()` all stayed native
  on half the primitive stream types,
  which is the whole stateful family.
Issues #982 and #996 each closed the `IntStream` arm of that gap
  and left the rest,
  so the backlog looked like it tracked something it did not.
All three fuse on a wide element now.
`distinct()` and `skip()` needed one more thing than the layout,
  and it was #1016 rather than anything about element width:
  their lift wraps the operation in a boxing sandwich,
  and `221` used to cancel that sandwich's trailing unbox
  against whatever box came next —
  including the user's own `boxed()`,
  whose boxing then simply disappeared,
  handing a `mapMultiToInt`'s `IntStream`
  to code holding the `Stream` that `boxed()` promised.
Four rules mint a box and they are not interchangeable,
  so every box now records which one minted it:
  `minted ↦ "user"` for a `boxed()` the user wrote,
  `"crossing"` for `208-mapToObj`'s primitive-to-reference step,
  `"split"` for `211`'s sandwich head
  and `"sandwich"` for `214`'s.
`221-unbox-box-to-map` takes every box but the user's own,
  and `209-unbox-box-to-distill` takes only the user's and the crossing,
  which is what its header always said it wanted
  and what its position in the sort never actually gave it —
  the rule set runs to a fixpoint,
  so `209` comes round again after `211` and `214` have fired.
The one user `boxed()` that is still safe to cancel against
  is one the user's own `mapToX` unboxes on the very next step,
  and `221-unbox-user-box-to-map` takes that one,
  pinning the following unbox in its pattern
  so the round trip it drops is provably dead.
That defect was live on `IntStream` long before any wide element
  could reach it.

Two numbers move with the item's width,
  and both are now derived from it rather than written out:
  the consumer's slot and the counter's.
`512` reads the width off `bridge-input`,
  and `521` reads it off the parameter `512` declared,
  which is the same fact from the same place.
The latch scratch does not move at all —
  it is pinned above the widest counter,
  and it is dead by the guard's keep-label,
  so no frame ever lists it and the slot below it may go unwritten.
Every frame in the family stays a _four-local_ frame either way,
  because a stackmap frame lists one entry per value and not per slot.

One more thing has to follow the width.
A filter, a peek and a `dropWhile` each copy the item with a `dup`
  so the predicate can eat the copy and the original can travel on,
  and a two-slot value needs a `dup2`.
The rules that splice those copies cannot tell:
  when they fire the operation is still inside the boxing sandwich,
  where the item is the one-slot wrapper,
  and `411` only afterwards collapses the sandwich onto the raw primitive.
`414-widen-guard-dup` makes that repair once, after `411` has decided,
  and it decides from what the copy is handed to —
  a one-parameter `(J)Z` predicate copies a `long` —
  rather than from the distill's element type,
  which is still a reference when a `distinct()` leads the run.

`skip()` and `dropWhile()` have unordered contracts of their own
  — any _n_ elements, any subset of the matching prefix —
  so the same relaxation would extend to `222`/`223` and `226`/`227`,
  but only once their state is atomic:
  a countdown `long[1]` and a sticky `int[1]` are raced on a parallel stream,
  not merely unstable, so those two stay reverted on any parallel pipeline.

## Why Nothing Is Fused Ahead of an Elidable `count()`

`ReferencePipeline.count()` is allowed to skip the pipeline altogether
  when the source is SIZED and nothing clears it,
  and it does — it just asks the spliterator for its size.
So under a bare `count()` the lambdas never run:

```java
// prints nothing: the map is never executed
Stream.of("a", "bb").map(s -> { log(s); return s.length(); }).count();
```

`mapMulti` clears SIZED by design,
  so a fused chain forces the traversal the JVM was going to skip,
  and every `map` and `peek` lambda starts running.
An exception thrown inside one goes the same way:
  invisible before the rewrite, thrown after it.

Both forms are within the specification,
  since `count()` is documented as possibly not executing the pipeline,
  so this is a question of what the plugin promises.
It promises not to add work:
  `101-mark-traversing-count` and
  `142-count-keeps-elidable-stage-native` keep the chain native
  whenever the terminal is one the JVM can elide (#973),
  and `143-count-keeps-elidable-named-map-native` does the same
  for a `map` whose argument is an object,
  which has no lambda for `142-` to stamp (#635),
  as does `143-count-keeps-elidable-ref-map-native`
  for one whose operator arrives in a register (#1014).
Nothing is forfeited by doing so —
  in exactly that case the fused `mapMulti`
  would have optimised a pipeline that was never going to run.

The dividing line is whether a stage clears SIZED,
  which is **not** the same as whether it changes the element count.
Measured against the JDK rather than reasoned about:

* `limit`, `skip`, `sorted`, `peek`, `map`, `boxed` and `unordered`
  keep the elision, so the guard fires and the chain stays native;
* `filter`, `distinct`, `takeWhile`, `dropWhile`, `flatMap` and
  `mapMulti` clear SIZED, so the fusion is kept.

`limit(3)` drops elements and still elides,
  because `SliceOps` computes the new size from the old one.
Where a SIZED-clearing stage is present the JVM walks the stream anyway,
  so the fusion costs nothing and is kept.

## Which Sink-Free Shapes Are Not Fused Yet

The sections above explain what the plugin refuses to fuse
  because fusing it would be wrong.
This one it would like to fuse and cannot yet.
It is a fusion barrier:
  the operations on either side of it still fuse into a `mapMulti`,
  the unrecognised call stays native between them,
  and the code is correct, just not collapsed into a single pass.

```java
// A skip(n) whose count is not a compile-time constant (#969). 220 and
// 212 bake the count into the countdown by capturing the opcode that
// pushes it, so they admit only lconst_0, lconst_1 and ldc; anything
// computed is left as it is rather than baked in wrong.
stream.skip(list.size() - 3L)

// A map or a filter reading its operator from an INSTANCE field
// (#1014). 215-recognize-ref-map takes an aload or a getstatic, each of
// which pushes the operator on its own; a getfield's receiver was
// pushed by an opcode ahead of the run, and relocating the getfield
// into the state append 502 moves would strand it.
stream.map(this.mapper)
```

A CAPTURING `mapMulti` stage is also left as two calls,
  on every stream kind:
  `111` declines a capturing `invokedynamic`,
  so neither `461` nor `462` ever sees a pragma to fuse.

A method reference is fused only when the wrapper the rules synthesise
  for it takes the element and returns the result with no adaptation
  beyond a single unboxing:
  `103` covers the unbound-instance reference,
  `104` the static one,
  `105` the return-dropping one a `peek` needs,
  `107` the one whose erased return needs narrowing,
  and `108` the one whose return needs unboxing.
The referenced method may return void —
  `peek(X::traced)` over a `static void traced(Long)`
  is `104`'s shape, since a `Consumer.accept` returns void
  and the instantiated SAM type then equals the target signature (#1053).

```java
static Integer length(String s) { ... }

stream.mapToInt(X::length)     // fused; 108 spells out intValue()
stream.mapToLong(X::length)    // native; needs intValue() then i2l
stream.reduce(Integer::sum)    // native; two arguments
stream.map(StringBuilder::new) // native; constructor reference
```

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
| [commons-configuration](https://github.com/apache/commons-configuration/commit/6ef60965b273110bd83068312420c32421fef11d) | 156 | 52534 | 400 | 32s | 0/27 | 0s | 32s |
| [vavr](https://github.com/vavr-io/vavr/commit/3af14df99c6a99ecd984e516d5f5294335cfbe84) | 678 | 68282 | 388 | 73s | 0/24 | 0s | 75s |
| [commons-compress](https://github.com/apache/commons-compress/commit/493575e0dd82ccb8f55cbba2da12fb0df1a7e191) | 321 | 79083 | 619 | 105s | 0/19 | 0s | 106s |
| [mybatis-3](https://github.com/mybatis/mybatis-3/commit/d3802feff2bef6122bd65c07782b00e4ceb6c99b) | 12852 | 71445 | 486 | 47s | 0/14 | 0s | 48s |
| [json-schema-validator](https://github.com/networknt/json-schema-validator/commit/2276a17e117c8977eacac2dcef04908939463bed) | 352 | 31282 | 312 | 15s | 2/12 | 114s | 15s |
| [commons-collections](https://github.com/apache/commons-collections/commit/b533ad6c0527027ba08d9a9891b09d7eaf1af98e) | 531 | 73500 | 614 | 26s | 0/12 | 0s | 26s |
| [jsoup](https://github.com/jhy/jsoup/commit/a7ec14364e2f9f84ecb795814b4fd05d028f709d) | 2298 | 39031 | 317 | 16s | 0/8 | 0s | 15s |

The results were calculated in [this GHA job][coverage-gha]
on 2026-09-20 at 09:14,
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
[coverage-gha]: https://github.com/objectionary/hone-maven-plugin/actions/runs/35500448843
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
