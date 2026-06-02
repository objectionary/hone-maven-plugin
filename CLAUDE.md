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
1xx  prep         strip line-numbers, desugar method refs to lambdas
                  (103 unbound, 104 static, 105 peek/return-drop), lower
                  invokedynamic to Φ.hone.lambda; lift a capturing map/filter
                  indy to Φ.hone.{c-map,cp-filter} (112, 113), peel a
                  multi-int capture run into the c-map (114)
2xx  recognise    lambda + invokeinterface → Φ.hone.{filter,map,unbox,box}
3xx  fold         every pragma → uniform Φ.hone.distill
4xx  fuse         adjacent distills → one combined distill  ← the actual win
5xx  emit         distill → Φ.hone.mapMulti + private static method
6xx  unrecognise  Φ.hone.* pragmas → Φ.hone.lambda + invokeinterface
7xx  lower        Φ.hone.lambda → Φ.jeo.opcode.invokedynamic; revert an
                  unfused method-ref wrapper to its native ref (711, 712)
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
adjacent bodies blindly. Pointwise operators (map, filter, peek, …) carry
an empty `state`; `peek` is the one whose body opens with a `dup` — it runs
its Consumer for the side effect and forwards the element unchanged, so the
body keeps a copy before the consumer invocation swallows the duplicate and
returns void (see 309). The two stateful operators today are `distinct` (whose
`state` builds a `java/util/HashSet` and whose body consults it) and
`skip` (whose `state` builds a `long[1]` countdown counter) — see
"Stateful operators (distinct, skip)" below. There is still no
continuation-passing variant.

The full operator-to-rule mapping (every rule named below exists under
`streams/`):

```text
filter, map (object + primitive) → 201..204 → Φ.hone.{filter,map}
peek                             → 207 → 309 → Φ.hone.peek → distill
boxed/unbox, box                 → 205, 206  → Φ.hone.{unbox,box}
box/unbox cleanup + collapse     → 211, 221, 231, 232 → fold back to map/filter
type / transform adjustments     → 241..243, 251..261, 271, 272
dup insertion (for filter)       → 281, 282, 283 (after distinct/skip)
dup, transform, type, filter, map → 301..306 → Φ.hone.distill (auto body)
load `this` into a pre-distill   → 311 → flips Φ.hone.pre-distill to distill
box-distill-unbox collapse       → 411 → primitive-typed distill
trailing mapToX into a distill   → 451 → type-transition distill
transform-distill normalisation  → 421, 422
dup before a filter distill      → 431
distinct                         → 216 → 307 → state distill;
                                    441 reverts it to invokeinterface
                                    if it never fused
skip                             → 220 → 308 → state distill;
                                    442 reverts it (count and call)
                                    to invokeinterface if it never fused
capturing map (N ints)           → 112 → 114 → 316 → c-map state distill;
                                    443 reverts a lone single-capture map if
                                    unfused (multi-capture stays mapMulti)
capturing filter (one int)       → 113 → 314 → c-filter state distill;
                                    444 reverts it to invokedynamic if unfused
```

`401-fuse` is the only fuser: it matches two adjacent `Φ.hone.distill`
formations in the same method body, `join`s their two bodies into one,
and narrows the bridge types so the fused distill inherits the first
side's `bridge-input` / `accepted` / `start` and the second side's
`bridge-output` / `returned`. Because every distill body is auto-style,
no guard on body shape is needed — any two adjacent distills fuse.

### Type-transition distills (trailing mapToInt/Long/Double)

A `mapToInt` / `mapToLong` / `mapToDouble` that ends an object pipeline is
recognised by `205` as a `Φ.hone.unbox` (object in, primitive out). A lone
unbox only ever folded when a following `.boxed()` paired with it (`221`,
`411`); a boundary mapToX just sat idle and `603` lowered it back to a
native call. `451-fuse-distill-unbox` closes that gap: it runs after the
`4xx` fuse pass and splices the unbox's target invocation onto the tail of
the single *pointwise* distill in front of it, widening that distill into a
**type-transition** distill — `bridge-input` stays the object element type
but `bridge-output` becomes the primitive (`I`/`J`/`D`). It deliberately
matches only empty-state distills, so a stateful predecessor
(distinct/skip) keeps its native mapToX, and a mapToX with no distill in
front of it never matches and stays native via `603` — the same
"do not pessimise a lone operator" stance as the `441`/`442` reverts.

Because a transition distill has `bridge-input ≠ bridge-output`, the emit
pass is **output-aware**: `Stream.mapMultiToX` takes a plain
`BiConsumer<T, XConsumer>`, so the mapMulti SAM and its erased
samMethodType still follow the INPUT (exactly as an ordinary mapMulti),
while the `consumer` the body drives, the resulting stream class and the
method name (`mapMulti` vs `mapMultiToInt`/`…Long`/`…Double`) follow the
OUTPUT. `501`/`511`/`601` thread two extra pragma fields — `stream-method`
and `result-stream-class` — to carry that split (a symmetric distill sets
them to its input-side values, so nothing changes there).

A method reference (`Integer::intValue`, `String::toUpperCase`, `X::keep`, …)
is **desugared into a lambda** up front so the rest of the pipeline picks it
up with no special-casing. A method-ref `invokedynamic` is byte-identical to a
lambda's except its target handle points straight at the referenced method, so
`111` (which only lifts `lambda$…` targets) leaves it native. Two rules close
that gap before `111`:

- `103-methodref-to-lambda` — **unbound-instance** refs (handle 5).
- `104-static-methodref-to-lambda` — **static** refs (handle 6).
- `105-methodref-peek-to-lambda` — **unbound-instance** refs feeding a
  `.peek(...)` (handle 5, **return-dropping**).

Each synthesises a `private static lambda$hone$N` wrapper whose body is the
forwarding the `LambdaMetafactory` would have generated (`<load arg>;
invoke{virtual,static} C.m …; Xreturn`), appends it to the class (the same
class-level binding-insertion `501` uses), and repoints the `invokedynamic`'s
target handle at it (`invokestatic THIS.lambda$hone$N`). `111` then lifts the
repointed indy like any javac lambda, so `201`-`207`/`451` recognise and fuse
it. The wrapper's call opcode carries an `origin ↦ Φ.true` marker (which jeo
ignores, like `441`'s `reverted`) so the revert below can find it.

`105` is the **void-SAM** case. A peek's SAM is a `Consumer` (`accept` returns
void); a reference like `Integer::intValue` returns a value, so javac points the
indy at a kind-5 handle with an instantiated type of `(L…;)V` and lets the
metafactory DROP the return. `103` declines that (its return-category guard has
no void branch), so `105` is its return-dropping twin: the wrapper body is
`aload 0; invoke C.m ()R; pop|pop2; return` (the drop opcode chosen by `R`'s
category). Crucially, `105` keeps `103`'s original **predecessor gate** — it
fires only when the reference directly follows a pointwise `map`/`filter`/`peek`
— because, unlike a mapToX unbox (which reverts to native via `603`/`701`/`711`
when it never fuses), a peek folds to a distill **unconditionally** (`309`)
and a lone distill is emitted as a standalone `mapMulti` (`501`) that `711`
cannot revert (the folded distill has dropped the SAM/instantiated types the
revert needs). The gate guarantees a fusable neighbour, so a lone peek
reference is left native — never pessimised.

A reference that does **not** fuse (it followed a barrier, a stateful
operator, or a `boxed()` roundtrip, or feeds a terminal) is restored to its
native form by `711-revert-unfused-methodref` (handle 5) and
`712-revert-unfused-static-methodref` (handle 6): once `701`/`702` have
lowered the unfused wrapper-lambda back to `invokedynamic → lambda$hone$N`,
the revert reads the referenced method from the `origin`-marked wrapper call,
repoints the indy at it and deletes the wrapper — leaving exactly the bytecode
javac emitted. So conversion is universal and **never pessimises**: it fuses
where it can and reverts where it cannot, the same stance as the `441`/`442`
distinct/skip reverts. (Admitting method refs in `111` *directly* instead does
not work — it `IncompatibleClassChangeError`s the `boxed`-roundtrip and
stateful paths at run time; the wrapper's `invokestatic`-to-a-real-method shape
is what keeps the downstream, which assumes `lambda$…` are static methods,
correct.)

`103`/`104` cover the **adaptation-free** case (argument and return types match
the SAM's instantiated type, single argument, no receiver `checkcast`); `105`
adds the one **return-dropping** adaptation (a value-returning ref against a
void `Consumer` SAM). Still native and the next puzzles: boxing/widening
adaptation (e.g. `String::length` returning `int` where a `Function` wants
`Integer`), multi-argument refs (`Integer::sum`), constructor refs (handle 8,
`Integer[]::new`), interface targets (handle 9), captured-receiver refs
(`self::decorate`, which need invokedynamic-argument surgery), and a static
(handle 6) peek ref — the wrapper mechanism extends to them by generating the
matching adaptation body.

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

### Capturing operators (closures)

A capturing lambda — `map(i -> i + x)`, `map(i -> i + x + y + z)`,
`filter(n -> n > t)` over effectively-final locals — is the case issue #636
reported as unfusable. javac compiles each captured value as an extra argument
on the lambda factory: the `invokedynamic` descriptor is `(I)L…Function;` (one
capture) or `(III)L…Function;` (three) rather than the zero-capture
`()L…Function;`, and a run of `iload k` pushes the values immediately before
the indy. `111`'s `\(\).+` interface guard matches only zero captures, so a
capturing indy never became a `Φ.hone.lambda` and was invisible to the whole
2xx→7xx pipeline.

The insight that makes it cheap: **a capture is just a pointwise operator
that owns one slot in the shared state List** — structurally identical to
`distinct`/`skip`, which already thread per-element state through that List.
So capturing operators reuse the entire stateful machinery (`401` fuse,
`502`/`512`/`521` emit, `503`/`504` frames, `601` factory-widen) with **no
edits to any existing rule**. They ride a parallel set of pragma names
(`Φ.hone.c-map`, `Φ.hone.cp-filter`) and distill `start` labels (`"c-map"`,
`"c-filter"`) so no existing recogniser, fold, dup-inserter or revert ever
touches them.

The path (object streams, int captures; map handles any number, filter one):

- `112` / `113` lift a capturing map / primitive-filter indy to
  `Φ.hone.c-map` / `Φ.hone.cp-filter`. `113` (single capture) still **grabs the
  one `iload k` push** and carries it as `capture-push`. `112` is now arity-
  agnostic: its guard is `\(I+\)L…Function;` (one OR MORE ints) and it grabs
  **no** push — it leaves the whole run of `iload`s inline in front of the
  c-map and seeds two EMPTY accumulators (`state-acc`, `body-acc`). They
  partition the indy space with `111`: `\(\)` matches zero captures, `\(I…\)`
  matches int captures, a handle-6 static `lambda$` target, and a
  `Stream<Integer>` instantiated type (the SAM type is capture-independent, so
  it is unchanged by arity). A reference capture (push is `aload`), category-2
  (`J`/`D`), `this`-capture (handle 5), type-transforming map, computed/field
  push — all stay native.
- `114` gathers `112`'s inline pushes into the c-map, one per firing,
  re-applying at fixpoint (the same self-iteration `521` uses). It matches the
  single `iload` **directly in front of** the c-map; phino's leading group is
  greedy, so the bound push is always the LAST one and the rule peels
  right-to-left. Each peel PREPENDS one boxing append
  (`dup; iload k; Integer.valueOf; List.add; pop`) to `state-acc` and one
  `fetch; intValue` to `body-acc`, so the captures end up appended in
  left-to-right (x, y, z) order — slot 0 = x, guard *k* reads capture *k*. The
  run is bounded on the left by the previous operator's invokeinterface, never
  another iload, so peeling stops cleanly.
- `316` / `314` fold into a stateful `Φ.hone.distill`. `316` copies the filled
  `state-acc` into `state` and wraps `body-acc` into the auto emit-shape
  `astore 1; <fetch; intValue>×N; aload 1; invokestatic lambda$`: the item is
  parked in its own (now-dead) local-1 slot, the N unboxed captures left below
  it exactly as the static `lambda$(int…, Integer)` signature wants. Parking in
  local 1 needs no extra local, so `512`'s max-locals is untouched. This N-ary
  park/reload **replaces v1's single-capture `swap`**, unifying one and many
  captures (omit the reload and the operands silently invert — the verifier
  accepts it, so the e2e asserts the numeric runtime result, not just opcode
  counts). `314`'s single-capture FILTER still uses the `swap` body plus its
  own opening `dup` and a `Φ.hone.frame-item` keep-label (the `"c-filter"`
  start label keeps `431`/`281` away); a multi-capture filter stays native
  (113's `\(I\)` guard declines it) — its park/reload twin, which must also
  defer the keep-frame, is a follow-up puzzle.
- `401` fuses two capturing distills exactly like two `distinct`s: `join`
  concatenates the boxed appends into the List (slots in body order) and the
  two bodies in order, so guard *k*'s fetch reads slot *k*. `502`/`512`/`521`
  emit one `Stream.mapMulti` whose wrapper reads each capture back from the
  List by the per-call counter; `601` widens the factory descriptor by the
  captured List. No capture touches the indy descriptor (they hide inside the
  List), so the `IncompatibleClassChangeError` class CLAUDE.md warns about does
  not arise.
- `443` / `444` are the never-pessimise reverts (twins of `441`/`442`): a LONE
  single-capture operator that never fused — including the issue's literal
  `map(i -> i + x)` — is put back as the native `invokedynamic` +
  `Stream.{map,filter}`, replaying the push and marking the call
  `reverted ↦ Φ.true` so `112`/`113` cannot re-lift it (closed patterns plus
  the marker, the same loop-break `442` uses). `443` matches the park/reload
  single-capture body. A LONE multi-capture map (two or more appends) fails
  that closed pattern and is emitted as a standalone `mapMulti` — correct, only
  marginally slower than native, and rarer than a lone single-capture map;
  reverting it (replaying N pushes, rebuilding the `(I^N)` descriptor) is a
  follow-up puzzle.

Deferred puzzles (each extends the same shared-List channel): a multi-capture
FILTER and the lone-multi-capture-map revert (both above); reference captures
(drop the box/unbox); other type-preserving element types (relax the literal
`Stream<Integer>` instantiated type to a `bridge-input == bridge-output`
guard); category-2 captures; `this`-field captures; and capturing
`peek`/`mapToX`. See the `@todo` in `112`'s header.

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
