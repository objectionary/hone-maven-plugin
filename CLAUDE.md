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
| Stream rules (one per file)          | `src/main/resources/org/eolang/hone/rules/streams/Nxx/NNN-name.phr`   |
| Demo / sanity rules                  | `src/main/resources/org/eolang/hone/rules/{none.yml,33-to-42.yml}`    |
| Pinned tool versions                 | `src/main/resources/org/eolang/hone/default-{phino,jeo}-version.txt`  |
| Pipeline orchestration               | `src/main/resources/org/eolang/hone/scaffolding/entry.sh`             |
| Phino invocation loop                | `src/main/resources/org/eolang/hone/scaffolding/rewrite.sh`           |
| Rule discovery and pattern selection | `src/main/java/org/eolang/hone/Rules.java`                            |
| Mojo with all user-facing knobs      | `src/main/java/org/eolang/hone/OptimizeMojo.java`                     |
| End-to-end test fixtures             | `src/test/resources/org/eolang/hone/optimize/streams-*.yml`           |
| Single-rule unit tests               | `src/test/phino/*.yml`                                                |
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

### Every operation folds to a distill

Every recognised non-terminal Stream operator folds to a uniform
`Φ.hone.distill` pragma, the 4xx fuse pass collapses adjacent distills
into one, and 5xx emits a single `Φ.hone.mapMulti` per remaining
distill. The pragma carries ten bindings:

```text
φ ↦ Φ.hone.distill   the pragma marker (or Φ.hone.pre-distill before 311)
class                the enclosing class, for the synthesised method
bridge-input         the JVM type the body consumes (e.g. "Ljava/lang/Object;")
bridge-output        the JVM type the body produces
start                a label naming the originating operator ("map", "filter", …)
accepted             the SAM-accepted signature fragment
returned             the SAM-returned signature fragment
static               "true"/"false"; whether the synthesised method is static
state                a block of bytecode that appends the operator's captured
                       state to a shared List; empty (⟦ ρ ↦ ∅ ⟧) for pointwise
                       operators
body                 the opcode formation spliced between item-load and emit
```

The body is one-in-one-out (auto-style): phino feeds it a single item
and it produces a single item, so `401-fuse` can concatenate any two
adjacent bodies blindly. Pointwise operators (map, filter, …) carry an
empty `state`; the two stateful operators today are `distinct` (whose
`state` builds a `java/util/HashSet` and whose body consults it) and
`skip` (whose `state` builds a `long[1]` countdown counter) — see
"Stateful operators (distinct, skip)" below. There is still no
continuation-passing variant.

The full operator-to-rule mapping (every rule named below exists under
`streams/`):

```text
filter, map (object + primitive) → 201..204 → Φ.hone.{filter,map}
boxed/unbox, box                 → 205, 206  → Φ.hone.{unbox,box}
box/unbox cleanup + collapse     → 211, 221, 231, 232 → fold back to map/filter
type / transform adjustments     → 241..243, 251..261, 271, 272
dup insertion (for filter)       → 281, 282, 283 (after distinct/skip)
dup, transform, type, filter, map → 301..306 → Φ.hone.distill (auto body)
load `this` into a pre-distill   → 311 → flips Φ.hone.pre-distill to distill
box-distill-unbox collapse       → 411 → primitive-typed distill
transform-distill normalisation  → 421, 422
dup before a filter distill      → 431
distinct                         → 216 → 307 → state distill;
                                    441 reverts it to invokeinterface
                                    if it never fused
skip                             → 220 → 308 → state distill;
                                    442 reverts it (count and call)
                                    to invokeinterface if it never fused
```

`401-fuse` is the only fuser: it matches two adjacent `Φ.hone.distill`
formations in the same method body, `join`s their two bodies into one,
and narrows the bridge types so the fused distill inherits the first
side's `bridge-input` / `accepted` / `start` and the second side's
`bridge-output` / `returned`. Because every distill body is auto-style,
no guard on body shape is needed — any two adjacent distills fuse.

`501-distill-to-mapMulti` then rewrites each remaining *empty-state*
distill into a `Φ.hone.mapMulti` dispatch, and `511-distill-lambda-to-method`
hoists the distill body out into a private static `(item, consumer) → void`
method that the `mapMulti` call references. The 6xx rules (601, 602,
603) unrecognise `Φ.hone.mapMulti` / `box` / `unbox` back into
`Φ.hone.lambda` + `invokeinterface`, and the 7xx rules (701, 702)
lower the lambdas to `invokedynamic`.

### Stateful operators (distinct, skip)

`distinct` is the canonical operator that needs per-element state — a
`java/util/HashSet` of the elements already seen. It is treated as "a
filter that owns a HashSet" and rides the same auto-style distill, with
three extra pieces:

- **`state` block.** `307-distinct-to-distill` does not allocate the set
  inline; it stashes the *bytecode* that **appends** a fresh `HashSet` to a
  shared `java/util/List` (`dup; new HashSet; …; List.add; pop`) into the
  distill's `state` binding (with `random-tau` opcode names so two distincts
  in one method don't clash). Each append begins and ends at `[list]`, so
  `401-fuse` `join`s the `state` blocks of *any* number of operators into
  one block whose stack peak never grows — the key that lets two stateful
  distills fuse without overflowing the caller's max-stack.
- **`Φ.hone.fetch` marker.** The distill body is index-agnostic: it opens
  with `⟦ φ ↦ Φ.hone.fetch, type ↦ … ⟧` meaning "push my state-var onto
  the stack", then runs the guard (`HashSet.add`; keep the item iff the
  add was new, else `return` early). Because the body never names a slot,
  fusion stays pure concatenation. At emit, `502-distill-state-to-mapMulti`
  builds one fresh `java/util/ArrayList` and relocates the `state` appends
  in front of the `mapMulti` dispatch (so the List is built and captured),
  `512-distill-state-lambda-to-method` builds the `(List, item, consumer) →
  void` wrapper and zeroes a per-call counter at local 3, `521-fetch-to-load`
  rewrites each `fetch` into `aload 0; iload 3; List.get(I); iinc 3 1;
  checkcast` so the N fetches walk the List in body order (guard *k* reads
  state var *k*), and `503-distill-state-frame` fills each guard's stackmap
  frame — a FULL frame that lists the counter local, because a
  same-locals-1 frame would drop it (`504-distill-state-filter-frame` does
  the same promotion for a filter guard's keep-frame fused into the same
  wrapper). jeo is a verbatim assembler — it never recomputes frames, so
  rules must supply them. `601` widens the invokedynamic factory descriptor
  by the captured `List` (its `captured` binding); the implMethod handle
  gets the captured prefix but the `instantiatedMethodType` does not.
- **`441` unfused revert.** A distinct only earns the `mapMulti` machinery
  when it actually fuses with a neighbour. `441-unfused-distinct-to-invokeinterface`
  runs after the 4xx fuse pass: if a distinct distill still carries *only*
  the bare guard (it never fused), it is reverted to a plain
  `invokeinterface Stream.distinct()` so a lone distinct costs nothing
  extra. The reverted opcode carries a `reverted ↦ Φ.true` marker; `216`'s
  pattern is closed on four operands, so the marker stops it re-recognising
  the opcode and prevents a 216 → 307 → 441 loop (jeo ignores the extra
  binding when assembling).

`skip` is the second stateful operator and is built as a strict parallel
of distinct — "a filter that owns a countdown counter":

- **`state` block.** `308-skip-to-distill` stashes the bytecode that builds
  a fresh `long[1]` and **appends** it to the shared `java/util/List`, seeded
  from the *captured count-push* — `220-recognize-skip` grabs the single
  `lconst_0` / `lconst_1` / `ldc {long}` opcode that precedes `Stream.skip(J)`
  and carries it on the pragma as `push`, so the original count rides into the
  counter cell. The block builds the `long[]` first and only then maneuvers
  the List underneath it (a `swap`/`dup_x1`/`swap` shuffle before `List.add`)
  to keep its stack peak at 6 — anything higher overflows the *caller's*
  max-stack, which jeo never recomputes.
- **`Φ.hone.fetch` marker.** The body opens with `⟦ φ ↦ Φ.hone.fetch,
  type ↦ "[J" ⟧` (its state-var is a `long[]`), reads the counter, writes
  it back decremented (a long counter never wraps in any reachable stream),
  and branches on the *old* count: while it was positive the item is
  dropped, otherwise it survives to `consumer.accept`. `dup2_x2` stashes the
  old count below the store group so the array is consumed *before* the
  branch — the keep label is left with just `[item]`, the same single-item
  frame distinct uses, so `503` fills it unchanged. Skip reuses 502 / 512 /
  521 / 601 verbatim; `512`'s wrapper max-stack is 10 to fit the long
  juggling and its max-locals is 4 (params 0-2 plus the counter at slot 3).
- **`442` unfused revert.** `442-unfused-skip-to-invokeinterface` is 441's
  twin: a skip that never fused is reverted, but because skip takes an
  argument the revert replays the count-push (recovered from the `state`
  block) before the `invokeinterface Stream.skip(J)` — a lone skip lowered
  to a mapMulti would be strictly slower than the native skip. Same
  `reverted ↦ Φ.true` marker breaks the 220 → 308 → 442 loop.

Any number of stateful operators now fuse into one wrapper: the shared
`java/util/List` holds every state var (built by `List.add` appends, read
by `List.get(counter++)`), so `401-fuse` merges two stateful distills with
no special gating. A filter that follows a `distinct`/`skip` needs its own
`dup` before the predicate; `281` only inserts that after a map/filter
predecessor, so `283-insert-dup-before-filter-after-stateful` covers the
stateful-predecessor case.

Limitations (v1): object streams only (`512` hardcodes the `Object` item
shape); and `503` reads the frame type off `bridge-input`, correct while
every operator before the distinct preserves the element type. Lifting
these is future work.

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
  --rule src/main/resources/org/eolang/hone/rules/streams/4xx/401-fuse.phr \
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
   `src/main/resources/org/eolang/hone/rules/streams/Nxx/` (the
   `Nxx/` subdirectory whose digit matches the rule's hundreds prefix
   — e.g. a `4xx` fuse rule lives in `streams/4xx/`), following the
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
4. **Add a single-rule unit test** under `src/test/phino/` (a `.yml`
   pack named after the rule) with a minimal input the rule should
   transform and the expected rewritten output.
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
  negation: `streams/4xx/*,!streams/4xx/411-*` includes all `4xx` rules
  except one.
- **External (extra) rules.** Without rebuilding the plugin, point
  `<extra>` at a directory of `.phr`/`.yml` files. `OptimizeMojo`
  copies them into a `hone-extra/` directory and `entry.sh` appends
  them to `RULES` *after* the built-in selection. Useful for project-
  local experiments.

## Running the test suite

The `pom.xml` sets `<excludedGroups>deep</excludedGroups>` by default,
which causes the end-to-end optimize fixtures (the `streams-*.yml`
packs under `src/test/resources/org/eolang/hone/optimize/` and the
`src/test/phino/*.yml` rule-unit packs) to be skipped — running
`mvn test` alone exercises roughly half of the suite and silently
hides regressions in any `.phr` rule. Always run

```bash
mvn -Pdeep test
```

when verifying a rule change; the `deep` profile clears
`excludedGroups` so every `@Tag("deep")` test runs against the real
`phino` binary on the host.

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
