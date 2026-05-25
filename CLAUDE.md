# CLAUDE.md

This file orients Claude (and humans) on how the `.phr` rewriting pipeline
is shaped and how to modify or extend it. It assumes the reader has skimmed
`README.md`, which contains the *what* and *why*. This file covers the *how*.

## The pipeline in one paragraph

`OptimizeMojo` calls `jeo-maven-plugin:disassemble` to turn `.class` files
into `.xmir` under `target/hone/jeo-disassemble/`. It then runs `phino` on
every `.xmir` to produce a `.phi` file under `target/hone/phi/`, applies the
selected `.phr` rules in alphabetical order to produce
`target/hone/phi-optimized/`, converts the result back to `.xmir` under
`target/hone/unphi/`, and finally calls `jeo-maven-plugin:assemble` to emit
`.class` files into `target/classes/`. The orchestration lives in two shell
scripts shipped as classpath resources — `entry.sh` runs the whole pipeline,
`rewrite.sh` runs the phino step — and they are launched either inside a
Docker container (default) or directly on the host when a matching `phino`
binary is found.

## Where to look

<!-- markdownlint-disable MD013 -->
| Concern                              | File                                                                  |
| ------------------------------------ | --------------------------------------------------------------------- |
| Stream rules (one per file)          | `src/main/resources/org/eolang/hone/rules/streams/NNN-name.phr`       |
| Demo / sanity rules                  | `src/main/resources/org/eolang/hone/rules/{none.yml,33-to-42.yml}`    |
| Pinned tool versions                 | `src/main/resources/org/eolang/hone/default-{phino,jeo}-version.txt`  |
| Pipeline orchestration               | `src/main/resources/org/eolang/hone/scaffolding/entry.sh`             |
| Phino invocation loop                | `src/main/resources/org/eolang/hone/scaffolding/rewrite.sh`           |
| Rule discovery and pattern selection | `src/main/java/org/eolang/hone/Rules.java`                            |
| Mojo with all user-facing knobs      | `src/main/java/org/eolang/hone/OptimizeMojo.java`                     |
| End-to-end test fixtures             | `src/test/resources/org/eolang/hone/optimize/streams-*.yml`           |
| Single-rule expression tests         | `src/test/resources/org/eolang/hone/rules/streams/expressions/*.phi`  |
<!-- markdownlint-enable MD013 -->

## Anatomy of a `.phr` file

A `.phr` file is YAML with four meaningful keys:

- `name` — used in logs only.
- `pattern` — a 𝜑-calculus expression with metavariables that phino tries
  to match against every sub-expression of the input `.phi`.
- `result` — the replacement expression. The same metavariables that
  appear in `pattern` (plus any computed in `where`) carry their captured
  values into the result.
- `when` *(optional)* — a side-condition the match must satisfy
  (e.g. `matches: ['lambda\$.+', 𝑒-target-method]`). If absent, the rule
  fires whenever the pattern matches.
- `where` *(optional)* — auxiliary metavariables computed by small
  string functions. See "Where functions" below.

### Metavariable prefixes

These are the only three; phino uses the prefix to decide what kind of
thing the variable can bind to.

- `𝐵-foo` — captures **a group of bindings** inside a formation. Use it
  as a placeholder for "all the other bindings I don't care about".
- `𝜏-foo` — captures **a single binding name** (`tau`). When the same
  `𝜏-foo` appears in `pattern` and `result`, the original name is preserved
  on the rewritten formation.
- `𝑒-foo` — captures **one atomic sub-expression** (a number, a string,
  a formation, a dispatch chain).

A rule like `401-fuse.phr` is a textbook reference: every flavor of
metavariable is used, and the `where` block shows how to merge captured
groups (`join`) and rewrite captured atoms (`sed`).

### Where functions

The string-level helpers that `where` blocks can call:

- `concat` — string concatenation; produces an `𝑒-` value.
- `join` — concatenate two `𝐵-` binding groups into one.
- `sed` — apply one or more sed-style substitutions to an `𝑒-` value.
- `tau` — derive a `𝜏-` binding name from a string value (used to give
  the result formation a name computed from the input).
- `random-tau` — generate a fresh, unused `𝜏-` name, with the args
  listing existing names to avoid collisions.
- `random-string` — generate a fresh string from a `printf`-style format.
- `number` / `string` / `sum` — type coercions and arithmetic; rarely
  needed but legal.

When in doubt, grep the `streams/` directory for the function name and
copy the closest existing usage.

## Execution order is the filename

`Rules.discover()` reads every `.phr` and `.yml` under
`org/eolang/hone/rules/` on the classpath, then `Collections.sort(names)`
puts them in alphabetical order. Phino is then invoked with the rules in
that same order. The `NNN-` prefix is *the* mechanism that defines the
pipeline stages:

```text
1xx  prep         remove dead labels, lower invokedynamic to Φ.hone.lambda
2xx  recognise    lambda + invokeinterface → Φ.hone.{filter,map,unbox,box}
3xx  fold         every pragma → uniform Φ.hone.distill
4xx  fuse         adjacent distills → one combined distill  ← the actual win
5xx  emit         distill → Φ.hone.mapMulti + private static method
6xx  unrecognise  Φ.hone.* pragmas → Φ.hone.lambda + invokeinterface
7xx  lower        Φ.hone.lambda → Φ.jeo.opcode.invokedynamic
```

If you insert a rule with a prefix that lies between two phases (say,
`345-`), it runs after `3xx` is done and before `4xx` starts. Always pick
a prefix that reflects which invariant your rule preserves on its
*output* — that determines what later rules see.

### Every operation lives on the distill path

Every non-terminal Stream operator folds to a `Φ.hone.distill`
pragma, and the 4xx fuse pass collapses adjacent distills before
5xx emits one `Φ.hone.mapMulti` per remaining distill. The pragma
carries two pieces of payload that distinguish what shape of body
the wrapper method ends up with:

- **captures** — a binding group of state types
  (`captures ↦ ⟦ 𝐵-captures, ρ ↦ ∅ ⟧`) whose values become fields on
  the `BiConsumer` instance synthesised by `501`. Stateless distills
  carry an empty captures group; stateful ones carry one or more
  capture types
  (`[J` for `long[1]` counters, `[Z` for `boolean[1]` gate cells,
  `Ljava/util/HashSet;` for distinct's seen-set, etc.).
- **emit-shape** — `"auto"` or `"cps"`. Auto bodies are
  one-in-one-out: phino splices the operator opcodes between an
  `aload item` and a single auto-emit at the end. CPS bodies own
  their own emission and drive the downstream `Consumer` themselves
  via one or more `Φ.hone.emit` markers that `491-emit-to-accept-call`
  lowers to explicit `consumer.accept(...)` calls.

The full operator-to-rule mapping:

```text
filter, map, peek, mapToInt/Long/Double, boxed, dup, transform, type
  → 301..307 / 411..413 → auto distill (fuses freely via 401)
distinct, take-while, drop-while, skip(N),
  flatMap, mapMulti (verbatim),
  flatMapToInt/Long/Double, mapMultiToInt/Long/Double
  → 208, 215, 351..356 → cps distill (captures = state, emit-shape = cps)
sorted, sorted(Comparator), limit(N)
  → 212, 216 → named pragma, never folded (see next subsection)
skip(0L)
  → 311-skip-zero-to-noop (rewritten to Φ.jeo.opcode.nop, never reaches distill)
```

`401-fuse` (auto + auto), `401b-fuse-auto-cps` (auto then cps),
`401c-fuse-cps-auto` (cps then auto), and `401d-fuse-cps-cps`
(cps then cps) compose two distills into one whenever they sit
adjacent in the same method body. `501` then synthesises a single
private `BiConsumer` wrapper for each remaining distill, and
`502a`/`502b` lower the distill to the `mapMulti` dispatch pair.

#### Multi-emit fusion is supported

`401c-fuse-cps-auto` now uses phino's `splice` where-function
(objectionary/phino#708, shipped in phino 0.0.0.69) to insert the
auto body in front of *every* `Φ.hone.emit` marker in the cps body
on a single firing. A `part-of` guard on the cps body's binding
group keeps the rule from firing when no emit marker exists —
without it, the auto distill would be consumed yet its body would
land nowhere, silently dropping the operation. The `mapMulti` /
`mapMultiTo*` producers (215, 215b-d) emit cps distills with zero
`Φ.hone.emit` markers (the user lambda owns emission via the
captured `Consumer`), so the guard correctly leaves their adjacent
auto neighbours unfused; that limitation is a property of 215's
body shape, not of 401c.

#### CPS+CPS fusion is supported (capture union ≤ 1)

`401d-fuse-cps-cps` fuses two adjacent cps distills using phino's
`graft` where-function (objectionary/phino#721, shipped in phino
0.0.0.71). `graft` is the marker-replacing sibling of `splice`:
where `splice` inserts the replacement bindings in front of every
sentinel and keeps the sentinel in place, `graft` REPLACES the
sentinel with the replacement. That difference is exactly what
cps+cps fusion needs: a cps body consumes the top-of-stack item
via its leading store and would crash if the original
`Φ.hone.emit` marker still fired with no item left on the operand
stack. `graft` also renames every τ-labelled binding inside the
grafted copy on each firing, so the synthetic `i1, i2, i3, …`
keys that 3xx-fold rules emit in every cps body cannot collide
when the same body is grafted at multiple emit positions or
alongside the first body's own bindings — the binding-collision
footgun that blocked the earlier concatenation-based attempt at
401d.

Captures merge via `join`; the rule fires only when the union has
cardinality 0 or 1, gated by phino's built-in `length:` predicate
on the two pattern-captured binding groups in the `when:` clause.
The predicate language has `eq` but no `le`, so the
sum-of-lengths ≤ 1 constraint is unfolded into three OR'd AND-
pairs: (first = 0 AND second = 0), (first = 0 AND second = 1),
(first = 1 AND second = 0). The zero / one outcomes are exactly
what `502a-cps-no-cap-to-lambda` and `502b-cps-one-cap-to-lambda`
already lower. This covers every pairwise adjacency where at
most one side carries state — every (filter / flatMap / mapMulti)
pair, plus every (filter / flatMap / mapMulti) ↔ (distinct /
takeWhile / dropWhile / skip-N) adjacency in either order. Two
stateful cps producers adjacent to each other (distinct →
takeWhile, takeWhile → distinct, …) still survive as separate
`invokedynamic` dispatches: the merged distill would carry two
captures and no `502*` rule lowers multi-capture cps today. A
future `502c` would close that remaining gap.

Bridge narrowing follows the same pattern as 401-fuse / 401b /
401c: the fused distill inherits the first side's `bridge-input`,
`accepted`, `start` and the second side's `bridge-output`,
`returned`. The merged body keeps the cps emit-shape, so further
fusion by 401b / 401c / 401d can splice or graft neighbours around
the surviving emit markers; lowering by 491 + 502a sees the merged
distill exactly as it would see any single-operator zero- or
one-capture cps distill.

#### Typed-bridge fusion is supported

`401b-fuse-auto-cps` and `401c-fuse-cps-auto` previously locked
both sides to `bridge-input ↦ "Ljava/lang/Object;"` (and likewise
`bridge-output` / `accepted` / `returned`), which excluded typed
auto distills produced by 411–413 (e.g. `Integer → Integer` from
`.map(n -> n + 1)` on a `Stream<Integer>`, or primitive `I` / `J` /
`D` from box-distill-unbox collapse) from joining adjacent cps
operators. The metavar-only revision drops the gate: 401b takes the
auto side's bridge-input / accepted and the cps side's bridge-output
/ returned (mirroring `401-fuse`'s narrowing convention); 401c does
the dual swap on cps's bridge-input / auto's bridge-output.
`502a-cps-no-cap-to-lambda` was widened to derive the SAM class,
lambda-signature, stream class, item load opcode, max-locals, and
consumer index from `bridge-input` via the same sed dispatch table
that `502b` already used.

401c's `when` arm gains a second guard:
`not eq: 𝑒-first-start "flatMap"`. The flatMap-family cps distills
(`208-flatMap-to-distill`, `207b/c/d` primitive variants) emit a
user-produced `Stream` and rely on 493 lowering the marker into
`Stream.forEach(Consumer)` — the item-shape splice that 401c does
for `distinct` / `takeWhile` / `dropWhile` / `skip` would force the
spliced auto body to operate on a `Stream` value as if it were the
downstream element, which jeo:assemble flags as a VerifyError.
401b complements this: its result inherits the cps side's `start`
(rather than the auto side's), so a `.filter().flatMap()` chain
that 401b fuses into a single distill still carries
`start ↦ "flatMap"` downstream, keeping 491 from incorrectly lowering
the emit as `Consumer.accept(Stream)` and routing the lowering
through 493 instead.

#### Why `513-merge-mapMulti-unbox` survives but `512` does not

The 5xx phase historically carried two adjacent-mapMulti mergers as
the *slow-path* counterpart to the 4xx distill fuser:

- `512-merge-mapMulti.phr` matched two adjacent `Φ.hone.mapMulti`
  formations in the same body and merged them into one.
- `513-merge-mapMulti-unbox.phr` matches a `Φ.hone.mapMulti`
  immediately followed by a `Φ.hone.unbox` pragma and synthesises a
  single `mapMultiToInt`/`Long`/`Double` lambda.

With every non-terminal folded into distill, the fast path's
`501-distill-to-mapMulti` only ever emits *one* `mapMulti` formation
per pipeline, so 512's input pattern no longer occurs in any
deep-test fixture and the rule was deleted as dead code. 513
survives because the 2xx primitive-collapse rules (e.g.
`232-boxed-primitive-filter-to-filter.phr`) can still leave a
`Φ.hone.unbox` pragma adjacent to a `mapMulti` even after fusion —
verified by running every `streams-*.yml` fixture under
`-Dhone.small-steps=true` and observing 513 firing in
`streams-closures`, `streams-fusion`, and `streams-sources`.

### Sorted and limit stay non-fusable

Two stream operators are deliberately excluded from the distill
migration above: `sorted` and `limit`. They are *fundamentally* incompatible
with a single-pass `mapMulti` pipeline:

- `sorted` is a fully-buffering barrier — every upstream element must be
  consumed and held in memory before any downstream element can be
  emitted. There is no continuation-passing body shape that preserves
  this semantics while still flowing through a per-element `BiConsumer`.
- `limit(N)` short-circuits after N downstream emits. A merged distill
  would have to abort the surrounding `forEach` driver mid-stream, which
  the `BiConsumer.accept` contract has no clean way to express.

The chosen approach: recognise both operators as named pragmas so
neighbouring stateless segments can still fuse around them, but never
fold them into distill and never merge them into the surrounding
`mapMulti`. Each `sorted` or `limit` therefore survives as its own
`invokeinterface` dispatch in the final bytecode.

The mechanics:

- `212-lambda-to-sorted` recognises `Stream.sorted(Comparator)` as a
  named `Φ.hone.sorted` pragma so nothing in the 3xx / 4xx phases
  folds or fuses around it. `607-sorted-to-lambda` is the paired
  unrecognise. The no-arg `Stream.sorted()` variant carries no
  lambda and survives as a raw `invokeinterface` opcode throughout
  the pipeline.
- `216-recognize-limit-ldc` recognises `ldc N + invokeinterface
  Stream.limit(J)` as a named `Φ.hone.limit` pragma carrying the
  count as a string literal. `722-limit-ldc-to-invokeinterface` is
  the paired unrecognise.

The practical consequence: every `sorted` and `limit` call survives as
its own `invokeinterface` dispatch, and the `streams-full-non-terminal`
fixture pins the resulting `after.invokedynamic` count (currently 20,
dominated by the 4 sorted + 2 limit barriers, the 5 `mapMulti` /
`mapMultiTo*` dispatches whose user-lambda emission shape leaves no
fusion seam, and the `flatMap` / `flatMapTo*` dispatches whose emit
shape is Stream-driven via `forEach` rather than item-driven via
`Consumer.accept` — 401c excludes flatMap-shaped cps from fusion
because the spliced auto body would otherwise operate on a `Stream`
value as if it were the downstream item).
That is by design — the architecture's value is fusing the *streamable*
part of the pipeline, not eliminating intrinsically-non-streamable
operators.

## phino: the only rewrite engine

There is no Java implementation of the rewriter. Everything is delegated
to the external `phino` binary, which is a Haskell program installed via
`cabal install phino`. The pinned expected version lives in
`default-phino-version.txt`; `entry.sh` refuses to run if `phino --version`
disagrees. Two phino subcommands matter:

- `phino rewrite --input=xmir --sweet INPUT.xmir` — converts an XMIR
  document into "sweet" 𝜑-calculus syntax (the kind that appears in
  `.phi` files and in `.phr` patterns).
- `phino rewrite --max-cycles N --max-depth M --sweet --rule=R1.phr
  --rule=R2.phr ... INPUT.phi` — fixed-point rewrite. Phino tries each
  rule at every position in the tree and keeps going until no rule fires
  or the `maxCycles` / `maxDepth` caps are hit. The `--output=xmir
  --omit-listing --omit-comments` form does the reverse direction.

The plugin runs phino in one of two modes (see `OptimizeMojo#smallSteps`
and `rewrite.sh`):

- **Big steps** *(default)* — every selected rule is passed on the same
  command line. Phino picks them up and applies them in a single
  fixed-point loop. Fast, opaque.
- **Small steps** (`<smallSteps>true</smallSteps>` or
  `-Dhone.small-steps=true`) — `rewrite.sh` calls phino once per rule
  and saves the intermediate result as `Foo.phi.01`, `Foo.phi.02`, ...
  in `target/hone/phi-optimized/`. `diff Foo.phi.07 Foo.phi.08` then
  shows exactly what rule `nnn` did. This is the canonical way to debug
  a misbehaving rule.

To run phino on a single `.phi` file by hand without Maven:

```bash
phino rewrite --max-cycles 1 --max-depth 500 --sweet \
  --rule src/main/resources/org/eolang/hone/rules/streams/401-fuse.phr \
  /tmp/Foo.phi
```

The output is the rewritten expression on stdout.

## How to add a new rule

1. **Pick the stage.** Decide which of the seven phases the rule belongs
   to (or that you are introducing a new phase between two existing ones)
   and pick the smallest unused `NNN-` prefix that places it correctly.
   Phase boundaries are recorded above; check `streams/` for the
   nearest neighbours before choosing.
2. **Write the `.phr` file** under
   `src/main/resources/org/eolang/hone/rules/streams/` following the
   header block (`SPDX` + `# yamllint disable rule:line-length`). Take
   the closest existing rule as a template; the metavariable conventions
   are not enforced by the tool but make rules readable.
3. **Run small-steps locally** on a representative `.phi` to see what
   your rule produces:

   ```bash
   mvn -Dhone.small-steps=true -Dhone.rules='streams/*' \
       org.eolang:hone-maven-plugin:build \
       org.eolang:hone-maven-plugin:optimize
   ```

   Inspect `target/hone/phi-optimized/...phi.NN` files to verify the
   delta.
4. **Add a single-expression test** under
   `src/test/resources/org/eolang/hone/rules/streams/expressions/` with
   a minimal `.phi` that the rule should transform, and a sibling test
   that asserts on the rewritten output.
5. **Add or update an end-to-end YAML** in
   `src/test/resources/org/eolang/hone/optimize/`. These specify the
   Java source to compile, the expected `log` lines, and the expected
   opcode counts in the class — `before` for the bytecode produced by
   `javac` and `after` for the bytecode produced by the optimizer.
   A count of `0` asserts the opcode is absent at that stage. Updating
   the `after` counts is normal when a rule changes how a stream
   pipeline is lowered.
6. **Bump the phino version** in `default-phino-version.txt` only if
   the rule depends on syntax or behavior that ships in a newer phino.

## How to extend the pipeline beyond `streams/`

Two extension mechanisms exist; they are independent.

- **Built-in subfolder.** Add a new sibling directory next to `streams/`
  (for example `arithmetic/`), drop `.phr` files into it with the same
  conventions, and select them at runtime with
  `<rules>arithmetic/*</rules>`. The `Rules` class will discover them
  via ClassGraph because anything under `org/eolang/hone/rules/` is
  picked up automatically. The selection grammar supports wildcards and
  negation: `streams/4*,!streams/411-*` includes all `4xx` rules except
  one.
- **External (extra) rules.** Without rebuilding the plugin, point
  `<extra>` at a directory of `.phr`/`.yml` files. `OptimizeMojo`
  copies them into a `hone-extra/` directory and `entry.sh` appends
  them to `RULES` *after* the built-in selection. Useful for project-
  local experiments.

## Tools to keep on hand when working on rules

- **phino** — install on the host (`cabal install phino` matching the
  version pin) so the plugin can skip Docker. The `Phino` Java class
  checks `phino --version` and runs the pipeline directly if it
  matches; otherwise it falls back to Docker, which is slower.
- **jeo-maven-plugin** — the bytecode↔XMIR translator. When debugging
  a roundtrip failure, run `jeo:disassemble` and `jeo:assemble` by hand
  to bisect whether the corruption was introduced by phino or by jeo.
- **GNU coreutils** — `entry.sh` and `rewrite.sh` require GNU
  `realpath` (or `grealpath`) and `parallel`. On macOS install via
  Homebrew (`brew install coreutils parallel`).
- **`hone.debug=true` and `hone.verbose`** — enables `set -x` in the
  scripts and `--log-level=debug` in phino. Output is voluminous but
  shows every phino invocation and every shell expansion.
- **`hone.grep-in`** — pre-filter regex on the XMIR text. The default
  skips classes that contain neither `map` nor `filter`. When iterating
  on a single class, narrow this further or set it to `.*` to disable.
- **`target/hone-statistics.csv` and `target/timings.csv`** — produced
  by `rewrite.sh` and `entry.sh` respectively. Useful for spotting
  rules that fire on many lines or files that take disproportionate
  time.

## What not to do

- Do not assume rules are independent. The `4xx` fuse pass relies on
  every operation having already been folded into `distill` by `3xx`;
  inserting an unfolded pragma at `350-` would silently break fusion
  on that method.
- Do not change `Collections.sort(names)` in `Rules.discover()`. The
  pipeline depends on alphabetical ordering as its scheduling
  mechanism.
- Do not hand-edit `.class` files in `target/classes-before-hone/`.
  That directory is the backup the plugin makes before mutation; it is
  the only way to recover the pre-optimization bytecode without
  recompiling.
- Do not introduce a rule whose `result` contains constructs that
  `jeo:assemble` cannot translate back to bytecode. The pipeline never
  invents bytecode the JVM cannot run, but it is easy to invent
  𝜑-calculus that jeo cannot lower. Verify roundtripping by running the
  end-to-end optimize goal, not just phino in isolation.
