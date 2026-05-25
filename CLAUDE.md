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

### Every operation lives on the distill path (issue #570)

The 3xx description above is now the *actual* behaviour — every
non-terminal Stream operator folds to a `Φ.hone.distill` pragma, and
the 4xx fuse pass collapses adjacent distills before 5xx emits one
`Φ.hone.mapMulti` per remaining distill. The pragma carries two
pieces of payload that distinguish what shape of body the wrapper
method ends up with:

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

The full operator-to-rule mapping after Steps 1-10:

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

#### Multi-emit fusion is supported (Step 7, landed)

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

#### CPS+CPS fusion is supported (Step 3, landed — capture union ≤ 1)

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

#### Typed-bridge fusion is supported (Step 8c, landed)

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

Once every non-terminal folded into distill (Steps 1-10), the fast
path's `501-distill-to-mapMulti` only ever emits *one* `mapMulti`
formation per pipeline, so 512's input pattern stopped occurring in
any deep-test fixture and the rule was deleted as dead code. 513
survives because the 2xx primitive-collapse rules (e.g.
`232-boxed-primitive-filter-to-filter.phr`) can still leave a
`Φ.hone.unbox` pragma adjacent to a `mapMulti` even after fusion —
verified by running every `streams-*.yml` fixture under
`-Dhone.small-steps=true` and observing 513 firing in
`streams-closures`, `streams-fusion`, and `streams-sources`.

### Sorted and limit stay non-fusable (issue #570, Step 10)

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

The chosen approach is **option (A)** from the plan: recognise both
operators as named pragmas so neighbouring stateless segments can still
fuse around them, but never fold them into distill and never merge them
into the surrounding `mapMulti`. Each `sorted` or `limit` therefore
survives as its own `invokeinterface` dispatch in the final bytecode.

The mechanics:

- `212-lambda-to-sorted` recognises `Stream.sorted(Comparator)` as
  `Φ.hone.sorted`. If no later rule consumes it,
  `607-sorted-to-lambda` lowers it back to the original lambda +
  `invokeinterface` pair, which 7xx then turns into bytecode.
- `216-recognize-limit-ldc` recognises `ldc + Stream.limit(J)` as
  `Φ.hone.limit`. The mirror rule `722-limit-ldc-to-invokeinterface`
  lowers it back when nothing fuses it.

The practical consequence: every `sorted` and `limit` call survives as
its own `invokeinterface` dispatch, and the `streams-full-non-terminal`
fixture pins the resulting `after.invokedynamic` count (currently 21,
dominated by the 4 sorted + 2 limit barriers, the 5 `mapMulti` /
`mapMultiTo*` dispatches whose user-lambda emission shape leaves no
fusion seam, and the `flatMap` / `flatMapTo*` dispatches whose emit
shape is Stream-driven via `forEach` rather than item-driven via
`Consumer.accept` — 401c excludes flatMap-shaped cps from fusion
because the spliced auto body would otherwise operate on a `Stream`
value as if it were the downstream item).
That is by design — the architecture's value is fusing the *streamable*
part of the pipeline, not eliminating intrinsically-non-streamable
operators. The buffered-distill option, originally out of scope for
`#570`, is now picked up under the "driver" emit-shape work described
in the Active plan section below.

## Active plan: fuse every non-terminal pipeline into one structure

The flagship fixture
  `src/test/resources/org/eolang/hone/optimize/streams-full-non-terminal.yml`
  sits at `after.invokedynamic: 21`.
The plan below covers the work still required
  to bring every non-terminal pipeline to a single fused structure —
  one `mapMulti` invokedynamic
  for pipelines with no sorted / limit,
  one `invokestatic` to a synthesized driver method
  (zero invokedynamic) for pipelines with sorted or limit.

Sub-agents should treat this section as their working brief
  and pick up the next unfinished step in order.

### Current state

The previous round of work landed three relevant pieces:

- The `emit-context ↦ "top-level"` annotation
  on every existing `Φ.hone.emit` producer
  (commit `5099e3f0`).
- flatMap recognition restructured to emit an explicit
  `Iterator.hasNext/next` loop in the cps distill body
  with a top-level `Φ.hone.emit` marker
  (commit `20daf333`,
  new file `208b-primitive-flatMap-to-distill.phr` for the primitive variants).
  `493-emit-to-foreach.phr` is gone;
  401c's flatMap guard is gone.
- mapMulti recognition restructured to inline the user lambda's opcodes
  into the cps distill body
  (commit `88259f67`).
  New companion rules
  `217-mapMulti-accept-to-emit.phr` and `217b/c/d`
  rewrite each inlined accept call to a top-level `Φ.hone.emit`.
  Phino is pinned to `0.0.0.70`
  (required for the rule-chaining fix in phino#714).

`streams-full-non-terminal.yml`'s `after.invokedynamic` continues
  to track the residue from sorted / limit (not yet migrated to
  driver-shape) plus the two-stateful-cps adjacencies that 401d
  cannot fuse today (distinct → takeWhile etc., pending 502c).
  Filter is cps (Step 2 landed) and cps+cps pairs whose merged
  captures union is ≤ 1 fuse via 401d (Step 3 landed, scoped to
  capture-union ≤ 1).

### The splice-doesn't-recurse constraint

Phino's `splice` where-function walks ONLY the top level
  of the binding group it is given.
It does NOT recurse into nested formations.
It does NOT cross method boundaries.

Every step in this plan obeys one rule:
  every `Φ.hone.emit` marker that must fuse with neighbouring operators
  lives at the TOP LEVEL of the distill `body ↦ ⟦ ... ⟧` binding group.
Nested formations (loops with `Φ.jeo.label` / `Φ.jeo.opcode.goto`)
  are allowed — those are opcodes in a sequence,
  and splice walks past opcodes correctly.
What is not allowed is a sub-formation
  (a separate `⟦ ... ⟧` block bound to its own metavariable)
  containing emit markers that need to be reached from outside.

### Step 1 — Delete the unused `491b-emit-nested-to-accept.phr`

The corrected plan never produces an `emit-context ↦ "nested-consumer"` marker.
A sanity check confirms it
  (`ggrep -rn 'nested-consumer' src/`
  returns only matches inside `491b-emit-nested-to-accept.phr` itself).
The rule is dead code.

**Mechanism.**

Delete `src/main/resources/org/eolang/hone/rules/streams/491b-emit-nested-to-accept.phr`.
If a sibling test exists under
  `src/test/phino/491b-emit-nested-to-accept.yml`,
  delete it too.
Keep the `emit-context ↦ "top-level"` annotations on every other producer;
  they document the invariant and are not dead.

**Verification.**

- `bash src/test/phino/run.sh` green.
- `mvn -Pdeep test` green.
- `streams-full-non-terminal.yml`'s `after.invokedynamic` is still 21.

**Result.**
Commit as a single chore commit.

### Step 2 — Make filter's distill body self-contained (landed, cps revision)

Filter was originally an auto-shape distill that relied on an
  externally inserted dup
  (`281-insert-dup-before-filter.phr` / `282`)
  to keep the item alive past the predicate call.
When `mapMulti` preceded a filter,
  281 did not fire
  (it matched only auto+filter neighbour pairs),
  so 401c's splice of the filter body produced invalid bytecode.
A workaround guard
  `not eq: 𝑒-second-start "filter"` in `401c-fuse-cps-auto.phr`
  blocked the fusion at the cost of one extra invokedynamic
  per `mapMulti.filter` adjacency.

**Final shape.**

Both `305-object-filter-to-distill.phr` and
  `304-primitive-filter-to-distill.phr`
  now produce a **cps-shape** distill
  (emit-shape `"cps"`,
  zero captures,
  body with internal `dup`,
  predicate call,
  `ifne` branch on the boolean result,
  `pop` + `goto end` on reject,
  `Φ.hone.frame` + `Φ.hone.emit` on accept,
  end label,
  end frame).
The reject path uses a forward `goto` to the body's own end label
  instead of a `Φ.jeo.opcode.return`,
  so the body falls through to the surrounding cps wrapper's outer loop
  (matching the contract that takeWhile / dropWhile / distinct already follow).
The accept-branch frame declares `nstack=1 stack=item`,
  matching the single-item-on-stack invariant of every cps emit point;
  the end label intentionally has no explicit frame
  so jeo computes one from context.

The 401c filter guard is gone.
The external-dup rules
  (`281-insert-dup-before-filter.phr`,
  `282-insert-dup-before-transformed-filter.phr`)
  were already deleted on `master` before this round of work.
The transition tail-boxing rule was split in two:

- `422-transform-distill-tail-to-object.phr` now matches only
  `emit-shape ↦ "auto"`,
  appending `valueOf` at the end of the body
  (correct because the auto wrapper emits AFTER the body).
- **new** `422b-transform-distill-cps-tail-to-object.phr`
  matches `emit-shape ↦ "cps"` distills
  whose body emits a primitive (`returned ∈ {B,C,D,F,I,J,S,Z}`)
  but whose surrounding SAM expects the boxed wrapper
  (`bridge-output` starts with `L`).
  It rewrites the matched `Φ.hone.emit` directly into the four-opcode
  `valueOf, aload-consumer, dup_x1, pop, invokeinterface accept`
  triple,
  taking the boxed-emit case out of 491's hands.

`502a-cps-no-cap-to-lambda.phr` was widened to derive `mapMulti-method`
  from both `bridge-input` (the cps body's actual input type)
  and `bridge-output`
  via a two-step sed dispatch
  (`𝑒-mapMulti-method-from-output` chained into `𝑒-mapMulti-method`),
  so a typed-bridge cps distill
  (e.g. `Integer → Integer` from a fused filter+map block)
  lowers to the correct `mapMulti` variant.

**The Step-3 follow-up (`401d-fuse-cps-cps.phr`) landed in scoped form.**
The rule's first concatenation-based attempt was reverted because
  pre-emit / second-body / post-emit fusion caused duplicate-key
  errors (e.g. `Duplicated attribute 'i3'`) whenever both bodies
  used the synthetic `i1, i2, i3, …` keys that the 3xx-fold rules
  emit. Phino 0.0.0.71 introduced `graft` (phino#721) — a `splice`
  variant that performs marker replacement with safe key renaming —
  and 401d was re-added on top of `graft`. The rule fires whenever
  the merged captures union is 0 or 1 (every (filter / flatMap /
  mapMulti) pair, plus every adjacency between such a zero-capture
  cps producer and a one-capture cps producer like distinct /
  takeWhile / dropWhile / skip-N), gated by phino's built-in
  `length:` predicate. Two stateful cps producers in adjacent
  positions still survive as separate dispatches because no `502*`
  rule lowers multi-capture cps; see "CPS+CPS fusion is supported
  (Step 3, landed — capture union ≤ 1)" above for the constraint
  rationale and the future-work pointer.

**Files modified (working tree).**

<!-- markdownlint-disable MD013 MD060 -->
| File                                                                  | Change                                                 |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| `src/main/resources/.../streams/305-object-filter-to-distill.phr`     | cps emit-shape, internal dup + branch + forward goto   |
| `src/main/resources/.../streams/304-primitive-filter-to-distill.phr`  | same, primitive comparison + dup width                 |
| `src/main/resources/.../streams/401c-fuse-cps-auto.phr`               | filter guard removed; doc updated                      |
| `src/main/resources/.../streams/401d-fuse-cps-cps.phr`                | re-added on top of phino's `graft`, capture union ≤ 1  |
| `src/test/phino/401d-fuse-cps-cps.yml`                                | re-added (filter → filter, zero-capture corner of range)|
| `src/main/resources/.../streams/422-transform-distill-tail-to-object.phr` | locked to `emit-shape ↦ "auto"`                    |
| `src/main/resources/.../streams/422b-transform-distill-cps-tail-to-object.phr` | new; primitive→boxed emit lowering for cps    |
| `src/main/resources/.../streams/502a-cps-no-cap-to-lambda.phr`        | derives `mapMulti-method` from both bridge sides       |
<!-- markdownlint-enable MD013 MD060 -->

**Verification (landed).**

- `bash src/test/phino/run.sh` green (70 / 70 with the re-added
  401d test).
- `mvn -Pdeep test` green.
- `streams-all-ops.yml` re-pinned: `after.return` dropped from 17 to 16
  on the Step-2 switch (the cps filter body's reject branch
  replaces a `return` with a `goto + emit + return` per filter).
- `streams-full-non-terminal.yml` re-pinned after Steps 2 and 3.
  The current value reflects sorted / limit (not yet migrated to
  driver-shape) plus the two-stateful-cps adjacencies that 401d
  cannot fuse today (distinct → takeWhile, takeWhile → skip, …).

**Result.**
Filter is a first-class cps distill;
  401c no longer carries operator-specific guards.
Cps+cps pairs whose merged captures union is ≤ 1 fuse via 401d
  (Step 3, landed). Two stateful cps adjacencies stay unfused
  until a `502c-cps-multi-cap-to-lambda` lowering lands.

### Step 3 — Add `401d-fuse-cps-cps.phr` (landed, capture union ≤ 1)

`401d-fuse-cps-cps.phr` is in the tree and uses phino's `graft`
  where-function (phino#721, shipped in 0.0.0.71) to replace each
  top-level `Φ.hone.emit` in the first body with the second body's
  opcodes. Captures merge via `join`; the rule fires when the
  union has cardinality 0 or 1, gated by phino's built-in
  `length:` predicate on the two pattern-captured binding groups
  (the predicate language has `eq` but no `le`, so the constraint
  is unfolded into three OR'd AND-pairs covering (0,0), (0,1),
  (1,0)). The zero / one outcomes are exactly what 502a / 502b
  already lower.

The remaining work — wiring the two-stateful-cps adjacencies
  (distinct → takeWhile, takeWhile → skip, …) — needs a
  `502c-cps-multi-cap-to-lambda` lowering. Once that lands, the
  `length:` guard on 401d's `when:` clause can drop the upper
  bound and the rule will fuse the full cps+cps matrix.

**Files modified (landed).**

<!-- markdownlint-disable MD013 MD060 -->
| File                                                                  | Change                                                 |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| `src/main/resources/.../streams/401d-fuse-cps-cps.phr`                | graft-based cps+cps fuser, capture union ≤ 1           |
| `src/test/phino/401d-fuse-cps-cps.yml`                                | filter → filter pinning test (zero-capture corner)     |
<!-- markdownlint-enable MD013 MD060 -->

**Verification (landed).**

- `bash src/test/phino/run.sh` green.
- `mvn -Pdeep test` green.
- `streams-full-non-terminal.yml`'s `after.invokedynamic` re-pinned.
- Future work: drop 401d's `length:` upper bound once 502c lands,
  and add expression tests for the two-stateful-cps cases.
  under `src/test/resources/org/eolang/hone/rules/streams/expressions/`.
- Re-pin the flagship fixture's `after.*` counts
  and the explanatory header comment
  to drop the `flatMap` / `flatMapTo*` / `mapMulti` / `mapMultiTo*`
  lines from the structural-limit list.

**Result.**
Every multi-emit operator participates in fusion.
The only surviving dispatches in the flagship are sorted (4) + limit (2)
  plus a small residue of fused-segment dispatches —
  Steps 4 / 5 / 6 address sorted and limit.

### Step 4 — Add the `"driver"` emit-shape and the 501-driver lowering

Phase C foundation step.
Introduces a second lowering pathway parallel to `501-distill-to-mapMulti`.
No behavior change yet —
  no existing rule produces `emit-shape ↦ "driver"` in this step.

**Mechanism.**

Extend the `Φ.hone.distill` schema with a third `emit-shape` value:
  `"auto"`, `"cps"`, or `"driver"`.
A driver-shape distill carries
  an iterator-style body that does NOT match the per-element BiConsumer contract:
  the body is given the upstream `Iterator` (or array) as a parameter
  and loops manually,
  emitting via top-level `Φ.hone.emit` markers
  whenever it wants to.

Create a new rule `501-distill-to-driver-method.phr`
  whose `pattern` matches `Φ.hone.distill` with `emit-shape ↦ "driver"`
  and whose `result` synthesizes a static method
  taking `(Iterator<T> source, Consumer<U> downstream)V`
  (and captures, threaded the same way 501/502 do today)
  and replaces the distill in the caller body
  with the corresponding `invokestatic` call,
  passing the upstream Stream's iterator
  (obtained via `invokeinterface Stream.iterator() → Iterator`).

The existing `501-distill-to-mapMulti.phr` remains the lowering
  for `emit-shape ↦ "auto"` and `emit-shape ↦ "cps"`.

**Files to modify.**

<!-- markdownlint-disable MD013 MD060 -->
| File                                                                  | Change                                                 |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| **new** `src/main/resources/.../streams/501-distill-to-driver-method.phr` | new lowering pathway for `emit-shape ↦ "driver"`  |
<!-- markdownlint-enable MD013 MD060 -->

**Verification.**

- `bash src/test/phino/run.sh` green.
- `mvn -Pdeep test` green
  (no behavior change —
  no producer of `"driver"` emit-shape exists yet).

**Result.**
The driver lowering pathway is in place,
  ready for Steps 5 and 6 to produce driver-shape distills.

### Step 5 — Migrate `sorted` to driver emit-shape

Expected outcome: sorted is recognised as driver-shape;
  the actual `after.invokedynamic` drop happens after Step 7
  closes the fusion-rule matrix.

**Mechanism.**

Add new 2xx-recognition rules
  for `Stream.sorted()` and `Stream.sorted(Comparator)`
  that produce a `Φ.hone.distill` with `emit-shape ↦ "driver"`,
  one `List` capture
  (`captures ↦ ⟦ c0 ↦ ⟦ φ ↦ Φ.hone.capture,
  type ↦ "Ljava/util/ArrayList;", init ↦ ⟦new ArrayList⟧ ⟧, ρ ↦ ∅ ⟧`),
  and an iterator-style body whose top-level emit marker
  fires inside the second loop:

```text
while (source.hasNext()) list.add(source.next());
Collections.sort(list);             -- or list.sort(comparator)
for (T x : list) emit(x);           -- emit is a top-level Φ.hone.emit
```

The existing `212-lambda-to-sorted.phr` recognises sorted today
  as a named pragma `Φ.hone.sorted`,
  and `607-sorted-to-lambda.phr`
  converts it BACK to an invokeinterface dispatch
  when nothing consumes it.
Replace both rules with the new driver-style migration.

**Files to modify.**

<!-- markdownlint-disable MD013 MD060 -->
| File                                                                  | Change                                                 |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| **new** `src/main/resources/.../streams/213-sorted-to-driver.phr`     | recognise `Stream.sorted()` as driver-shape distill    |
| **new** `src/main/resources/.../streams/214-sorted-comparator-to-driver.phr` | recognise `Stream.sorted(Comparator)` as driver-shape distill |
| `src/main/resources/.../streams/212-lambda-to-sorted.phr`             | delete                                                 |
| `src/main/resources/.../streams/607-sorted-to-lambda.phr`             | delete                                                 |
<!-- markdownlint-enable MD013 MD060 -->

**Verification.**

- `bash src/test/phino/run.sh` green.
- `mvn -Pdeep test` green.
- Add an expression test for sorted-recognized-as-driver-shape distill.

**Result.**
Sorted and sorted(Comparator) produce driver-shape distills.
Step 7's fusion-rule extensions
  are what actually let them collapse with neighbours.

### Step 6 — Migrate `limit` to driver emit-shape

Expected outcome: limit is recognised as driver-shape;
  the actual `after.invokedynamic` drop happens after Step 7
  closes the fusion-rule matrix.

**Mechanism.**

Add a 2xx-recognition rule
  for `Stream.limit(N)`
  that produces a `Φ.hone.distill` with `emit-shape ↦ "driver"`,
  one `long[1]` capture for the counter,
  and an iterator-style body of shape:

```text
while (source.hasNext() && counter[0]-- > 0) {
  emit(source.next());      -- top-level Φ.hone.emit
}
```

`216-recognize-limit-ldc.phr` recognises limit today as a named pragma
  `Φ.hone.limit`,
  and `722-limit-ldc-to-invokeinterface.phr` lowers it back.
Replace both with the new driver-style migration.

**Critical: limit's counter must combine correctly with upstream fusion.**

When 401b fuses an auto upstream into a driver-limit,
  the auto body's emits must each decrement the counter,
  not just the limit body's own emits.
This works automatically if the auto body is spliced AT the limit's
  per-iteration `emit(...)` point —
  the auto body's transform runs once per iteration,
  the emit fires once,
  the counter decrements once.
Verify this invariant by inspecting a small-steps `.phi.NN` trace
  of `.map(...).limit(5)` after the migration.

**Files to modify.**

<!-- markdownlint-disable MD013 MD060 -->
| File                                                                  | Change                                                 |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| **new** `src/main/resources/.../streams/215-limit-to-driver.phr`      | recognise `Stream.limit` as driver-shape distill       |
| `src/main/resources/.../streams/216-recognize-limit-ldc.phr`          | delete or refactor                                     |
| `src/main/resources/.../streams/722-limit-ldc-to-invokeinterface.phr` | delete                                                 |
<!-- markdownlint-enable MD013 MD060 -->

If the existing rule number 215 collides with the mapMulti recognition,
  pick a different prefix that respects phase ordering
  (must run after 2xx phase-boundary, before any 3xx fold).

**Verification.**

- `bash src/test/phino/run.sh` green.
- `mvn -Pdeep test` green.
- Add an expression test for limit-recognized-as-driver-shape distill.

**Result.**
Limit produces a driver-shape distill.

### Step 7 — Close the fusion-rule matrix and extend transition fusion

Steps 3, 5, and 6 introduced the third emit-shape `"driver"`
  and the second multi-emit shape pair (cps+cps via 401d).
The codebase now has three emit-shapes (`auto`, `cps`, `driver`)
  and three named transitions (`Φ.hone.box`, `Φ.hone.unbox`, `Φ.hone.transform`)
  but only a partial set of fusion rules.
This step makes the matrix complete
  so the flagship pipeline collapses end-to-end.

**Fusion-rule matrix (9 cells).**

<!-- markdownlint-disable MD060 -->
| First / Second  | auto       | cps         | driver        |
|-----------------|------------|-------------|---------------|
| auto            | 401 (kept) | 401b (kept) | **new 401e**  |
| cps             | 401c (kept)| 401d (kept) | **new 401f**  |
| driver          | **new 401g** | **new 401h** | **new 401i** |
<!-- markdownlint-enable MD060 -->

The five new cells share the same splice-based mechanism that 401c and 401d
  use today:
  splice the second body in front of every top-level `Φ.hone.emit` marker
  in the first body,
  union the captures via `join`,
  pick `start` / `accepted` from the first,
  pick `bridge-output` / `returned` from the second.
The result emit-shape is:

- `"driver"` if either side is driver
  (driver's iterator-style body is the outer shell);
- `"cps"` if no driver but at least one cps;
- `"auto"` only when both sides are auto
  (the existing 401 case).

Decide between extending existing files
  vs adding 401e-i as siblings
  based on pattern divergence.
The cleaner expected outcome
  is one file per cell;
  it makes the matrix legible.

**Transition fusion (411 / 412 / 413).**

The existing transition fusers hardcode `emit-shape ↦ "auto"` on both sides
  (verify by reading `412-distill-unbox-distill-to-distill.phr:41`).
The flagship has cps+transition+cps and cps+transition+auto
  (e.g. `.<Integer>mapMulti(...).mapToInt(Integer::intValue)`),
  which the auto-only rules cannot fuse.
Each unfused transition becomes a barrier that splits the pipeline.

Extend each of 411 / 412 / 413
  to accept any combination of emit-shapes on each side,
  using the same result-shape rule as the matrix above.
Either widen the existing patterns
  (replace the literal `"auto"` with a metavariable on each side
  and compute the result shape with a small `sed` decision table)
  or split each into a 3×3 = 9-cell family per transition.
Choose based on what the where-functions allow
  without losing pattern clarity.

**Files to modify.**

<!-- markdownlint-disable MD013 MD060 -->
| File                                                                  | Change                                                 |
|-----------------------------------------------------------------------|--------------------------------------------------------|
| **new** `src/main/resources/.../streams/401e-fuse-auto-driver.phr`    | auto+driver fusion                                     |
| **new** `src/main/resources/.../streams/401f-fuse-cps-driver.phr`     | cps+driver fusion                                      |
| **new** `src/main/resources/.../streams/401g-fuse-driver-auto.phr`    | driver+auto fusion                                     |
| **new** `src/main/resources/.../streams/401h-fuse-driver-cps.phr`     | driver+cps fusion                                      |
| **new** `src/main/resources/.../streams/401i-fuse-driver-driver.phr`  | driver+driver fusion                                   |
| `src/main/resources/.../streams/411-box-distill-unbox-to-primitive-distill.phr` | accept any emit-shape on either side          |
| `src/main/resources/.../streams/412-distill-unbox-distill-to-distill.phr`       | same                                          |
| `src/main/resources/.../streams/413-distill-box-distill-to-distill.phr`         | same                                          |
<!-- markdownlint-enable MD013 MD060 -->

**Verification.**

- `bash src/test/phino/run.sh` green.
- `mvn -Pdeep test` green.
- `streams-full-non-terminal.yml`'s `after.invokedynamic` drops to
  **0** (or very close).
  The flagship has at least one sorted (driver),
  so the entire fused pipeline lowers to a single `invokestatic`
  to the synthesized driver method —
  no invokedynamic at all.
- Pipelines with no sorted / limit
  (e.g. `streams-sources.yml`'s flatmap block)
  should land at exactly **1** invokedynamic
  (the single mapMulti).
- Re-pin every `after.*` count that shifts.
- Add expression tests for each of the five new fusion cells.

**Result.**
Every adjacent operator pair fuses.
Every transition crosses any emit-shape boundary.
The pipeline collapses into one structure per source-to-terminal chain.

### Step 8 — Final verification

Re-verify everything end-to-end
  and audit the final state of the flagship fixture.

**Baseline capture.**

Find the merge-base of the current branch and `origin/master`:
  `git merge-base HEAD origin/master`.
Check out that SHA in a worktree.
Run `mvn -Pdeep test` and capture per-fixture timings.
This is the baseline,
  before any of Steps 1-7 changed the codebase.

**HEAD verification.**

Back on the working branch,
  run `mvn -Pdeep test` and confirm zero regressions
  vs the baseline failure list,
  and total runtime no worse than baseline.

**Fixture audit.**

`streams-full-non-terminal.yml`'s `after.invokedynamic` should be **0**.
The flagship contains 4 sorted and 2 limit calls,
  each producing a driver-shape distill after Step 5/6.
Driver-shape distills lower via 501-driver to `invokestatic`,
  not `invokedynamic`,
  so a fully-fused flagship has zero invokedynamic dispatches.

Other fixtures' expected counts:
  pipelines with at least one sorted or limit → 0 invokedynamic;
  pipelines with no sorted / limit → exactly 1 invokedynamic
  (the single fused mapMulti emitted by 501).
If a fixture's count is higher than its target,
  the residue points to a fusion gap
  that Step 7's matrix missed —
  identify which adjacency does not fuse
  and either add the missing rule
  or document the residue
  in the fixture's explanatory header.

**Result.**

Every non-terminal pipeline is fused into one structure:
  either a single mapMulti (no sorted / limit)
  or a single driver-method invokestatic (with sorted / limit).
Rewrite the "Every operation lives on the distill path" and
  "Sorted and limit stay non-fusable" subsections of this file
  to reflect the new pipeline architecture
  (three emit-shapes `auto` / `cps` / `driver`,
  complete fusion-rule matrix,
  transitions fuse across any shape boundary),
  and delete the Active plan section above —
  the work is done.

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
