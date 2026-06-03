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
| End-to-end test fixtures             | `src/test/resources/org/eolang/hone/optimize/streams/*.yml`           |
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
- `random-tau` — generate a fresh, unused `𝜏-` name (phino guarantees
  it never collides with an existing name, so it takes no args).
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
1xx  prep         strip line-numbers (102 before a Stream op, 106 before a
                  primitive-stream .boxed()), desugar method refs to lambdas
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
user mapToX().boxed() roundtrip  → 106 (anchor) → 205,206 → 209 → 2-opcode
                                    object→wrapper distill (fuses + emits one
                                    mapMulti); see "boxed() round-trips" below
type / transform adjustments     → 241..243, 251..261, 271, 272
dup insertion (for filter)       → 281, 282, 283 (after distinct/skip)
dup, transform, type, filter, map → 301..306 → Φ.hone.distill (auto body)
load `this` into a pre-distill   → 311 → flips Φ.hone.pre-distill to distill
box-distill-unbox collapse       → 411 → primitive-typed distill
trailing mapToX into a distill   → 451 → type-transition distill
                                    (empty-state OR stateful; preserves state)
transform-distill normalisation  → 421, 422
dup before a filter distill      → 431
distinct                         → 216 → 307 → state distill;
                                    441 reverts it to invokeinterface
                                    if it never fused
skip                             → 220 → 308 → state distill;
                                    442 reverts it (count and call)
                                    to invokeinterface if it never fused
capturing map (N prim OR 1 ref)  → 112 → 114 (int) / 116 (long) / 117 (double)
                                    / 115 (ref peeler) → 316 → c-map state
                                    distill; 443 reverts a lone single-int-capture
                                    map and 446 a lone TWO-int-capture map if
                                    unfused (3+-capture, category-2 and lone-ref
                                    stay mapMulti)
capturing filter (N prim)        → 113 → 118 (int) / 120 (long) / 122 (double)
                                    → 314 → c-filter state distill; 444
                                    reverts a LONE single-int-capture filter and
                                    447 a lone TWO-int-capture filter to
                                    invokedynamic if unfused (3+-capture,
                                    category-2 stay mapMulti)
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
the single distill in front of it, widening that distill into a
**type-transition** distill — `bridge-input` stays the object element type
but `bridge-output` becomes the primitive (`I`/`J`/`D`). It captures and
**preserves the predecessor's `state` block** (`𝐵-state`), so it fires for
an empty-state pointwise distill AND for a stateful one (distinct/skip,
possibly with a `boxed()` round-trip already fused in): a trailing mapToX
that ends a distinct/skip pipeline folds into the same stateful distill
rather than staying native, collapsing the whole pipeline to ONE
`Stream.mapMultiToX` (this is #570 reaching the stateful path; see
`stateful-boxed-tail.yml` and `full-non-terminal.yml`). A mapToX with no
distill in front of it never matches and stays native via `603` — the same
"do not pessimise a lone operator" stance as the `441`/`442` reverts.

Because a transition distill has `bridge-input ≠ bridge-output`, the emit
pass is **output-aware**: `Stream.mapMultiToX` takes a plain
`BiConsumer<T, XConsumer>`, so the mapMulti SAM and its erased
samMethodType still follow the INPUT (exactly as an ordinary mapMulti),
while the `consumer` the body drives, the resulting stream class and the
method name (`mapMulti` vs `mapMultiToInt`/`…Long`/`…Double`) follow the
OUTPUT. Both emit flavours thread two extra pragma fields — `stream-method`
and `result-stream-class` — to carry that split: `501`/`511`/`601` for an
empty-state distill, and their stateful twins `502`/`512` (which compute
the same input/output split and drive the closing dup / `XConsumer.accept`
off the OUTPUT consumer, exactly as `511` does). A symmetric distill sets
both fields to its input-side values, so nothing changes there. `503`/`504`
still fill the guard keep-frame off `bridge-input` (the item there is the
object element, before the trailing transition runs), so they need no
change.

### boxed() round-trips (mapToX().boxed())

A user `mapToInt/Long/Double(...).boxed()` is the dual of a trailing mapToX:
the element is unboxed to a primitive and immediately re-boxed to its wrapper,
a net object→object pointwise step (e.g. `Integer → long → Long`). `205`
recognises the mapToX as a `Φ.hone.unbox` and `206` the boxed() as a
`Φ.hone.box`; `106-remove-boxed-label` first strips the line-number anchor
javac -g wedges before boxed (sibling of `102`, which only reaches a `Stream`
op — boxed is an invokeinterface on `Int/Long/DoubleStream`), leaving the pair
adjacent. `209-unbox-box-to-distill` then folds that pair into a single
empty-state distill whose body is **two** opcodes — the mapper invocation that
yields the primitive and the `Wrapper.valueOf` that re-boxes it — with
`bridge-output` the wrapper type. Because it is an ordinary empty-state
object→object distill, `401` fuses it with adjacent pointwise distills and
`501`/`511` lower the run to one `Stream.mapMulti`, exactly as for a map.

The subtle part is **ordering, not gating**. `209` MUST run before `211`. An
`unbox(lambda$), box` adjacency is a genuine user round-trip ONLY before `211`
splits a primitive `IntStream.filter/map` into `box + boxed-primitive-func +
unbox`: after that split, a user `mapToInt(ref)` that feeds a primitive op
(e.g. `mapToInt(Integer::intValue).filter(p)`) shows the SAME adjacency — but
its box is `211`'s compensating head-box for the next primitive op, and folding
it as a boxed() splices a stray `Integer.valueOf` into the primitive chain and
fails verification. Running `209` before `211` guarantees every box it sees is
a real `206` boxed(); `221-unbox-box-to-map` (after `211`, unchanged) then
collapses `211`'s synthetic pairs as before. (`209`'s `lambda$` target guard is
belt-and-suspenders — every `205` unbox targets a lambda$ anyway.)

This fuses a *pointwise* boxed() chain to one mapMulti (see
`boxed-roundtrip.yml`). A type-changing boxed() distill that re-boxes to a
wrapper stays object→object and `401` already fuses it into a stateful
(distinct/skip) distill — that part always worked. What used to break the run
into TWO mapMultis was the *trailing* mapToX (object→primitive) at the end of
the boxing tail: the old empty-state-only `451` left it native. `451` now
preserves the predecessor's `state` block and `502`/`512` are output-aware, so
the trailing mapToX folds into the stateful distill too and the whole stateful
pipeline collapses to ONE `Stream.mapMultiToX` (see `stateful-boxed-tail.yml`
and `full-non-terminal.yml`, now 9 → 1) — closing #570 for the stateful path.

A pipeline that *begins* with `distinct()`/`skip()` (before any typed
`map`/`filter`) has `bridge-input ↦ Ljava/lang/Object;`, because distinct/skip
erase the element type. A boxing tail whose desugared `lambda$` expects a
specific wrapper (e.g. `Integer::doubleValue`, an `(Ljava/lang/Integer;)D`
handle) used to VerifyError — the object item is not assignable to `Integer`
without a `checkcast`. `505-distill-state-item-cast` (issue #649) closes this:
after `503` resolves the guard's keep-label `frame-item` into a `Φ.jeo.frame`,
the very next opcode is that first typed `invokestatic`, so `505` splices a
`checkcast <wrapper>` between the frame and the invoke whenever `bridge-input`
is `Object` and the invoke takes a single non-`Object` reference argument. The
cast runs *after* the frame, which still rightly declares `Object`, so `503` is
unchanged. It reaches the first typed op directly after the (last) guard — the
boxed()/mapToX-after-distinct/skip shape; a `filter`/`peek` wedged between the
guard and the boxing tail opens its body with a `dup`, breaking the
`frame → invokestatic` adjacency, so narrowing through that interleaving is a
follow-up puzzle (see `stateful-object-tail.yml`).

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

Limitations: the INPUT item is object-only (`512` loads it with a fixed
`aload 1`); the OUTPUT may now be a primitive — when `451` fuses a trailing
mapToX into the stateful distill, `512`'s closing dup / `XConsumer.accept`
follow the output consumer exactly as `511` does, so a distinct/skip pipeline
ending in a mapToX lowers to one `Stream.mapMultiToX`. The `Object`-`bridge-input`
boxed() case (a pipeline that *begins* with distinct/skip) is now handled by
`505-distill-state-item-cast`, which narrows the Object item with a `checkcast`
before the boxing tail's first typed invoke (issue #649; see "boxed()
round-trips" above). Still future work: a primitive INPUT item; `503` reading
the frame type off `bridge-input` (correct only while every operator before the
distinct preserves the element type); and narrowing through a `filter`/`peek`
wedged between the guard and the boxing tail.

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

The path (object streams; map handles any number of category-1/2 primitive
captures over any type-preserving reference element type, filter one OR MORE int
captures over Integer):

- `112` / `113` lift a capturing map / primitive-filter indy to
  `Φ.hone.c-map` / `Φ.hone.cp-filter`. Both are now arity-agnostic and grab **no**
  push: `113`'s guard is `\(I+\)L…Predicate;` (one OR MORE int captures) and it
  seeds two EMPTY accumulators exactly as `112` does, leaving the run of `iload`s
  inline for `118` to peel. `112` is arity-agnostic too: its guard is
  `\([IJD]+\)L…Function;` (one OR MORE category-1/2
  primitives) OR `\(L…;\)L…Function;` (a SINGLE reference), and it grabs **no**
  push — it leaves the whole run of `iload`/`lload`/`dload`s / the lone `aload`
  inline in front of the c-map and seeds two EMPTY accumulators (`state-acc`,
  `body-acc`). They partition the indy space with `111`: `\(\)` matches zero
  captures, `\([IJD]…\)` matches primitive captures, `\(L…;\)` a single reference
  capture, a handle-6 static `lambda$` target, and a **type-preserving**
  instantiated type — matched as `(LX;)LX;` for any reference `X` via a
  backreference guard (Integer, String, …), with `X` sed-extracted into the four
  bridge fields. When a `J`/`D` capture is present `112` also BUMPS the caller
  `max-stack` by 2 (the two-slot boxing append `116`/`117` relocate peaks one slot
  higher than an int's; the two maxs values ride generated binding names, so they
  are matched positionally with `𝜏-max-stack`/`𝜏-max-locals`). A
  MULTI-reference / mixed-with-reference run, `this`-capture (handle 5), a
  **type-transforming** map (`(LX;)LY;`, declined by the backreference and left
  to the unbox/box machinery), computed/field push — all stay native.
- `114` (int) / `116` (long) / `117` (double) / `115` (reference) gather `112`'s
  inline pushes into the c-map, one per firing, re-applying at fixpoint (the same
  self-iteration `521` uses); `118` is `114`'s twin for the `cp-filter`, peeling
  the filter's int pushes into its accumulators byte-for-byte the same way. Each
  matches its own push opcode **directly in front
  of** the c-map; phino's leading group is greedy, so the bound push is always the
  LAST one and the rule peels right-to-left — a mixed int/long/double run is
  gathered by the three primitive peelers interleaving. `114` PREPENDS one boxing
  append (`dup; iload k; Integer.valueOf; List.add; pop`) to `state-acc` and one
  `fetch; intValue` to `body-acc`, so the captures end up appended in
  left-to-right (x, y, z) order — slot 0 = x, guard *k* reads capture *k*.
  `116` / `117` are byte-for-byte twins that box via `Long`/`Double.valueOf`
  and unbox via `longValue`/`doubleValue` (their two-slot append is why `112`
  bumps max-stack). `115` is the same shape minus the box/unbox: a reference is
  already an Object, so it appends `dup; aload k; List.add; pop` and a bare
  `fetch` typed with the
  capture's class (sed-extracted from `target.signature`'s first L-type, which
  is why only a SINGLE reference capture is admitted). The run is bounded on the
  left by the previous operator's invokeinterface, never another push, so
  peeling stops cleanly.
- `316` / `314` fold into a stateful `Φ.hone.distill`. `316` copies the filled
  `state-acc` into `state` and wraps `body-acc` into the auto emit-shape
  `astore 1; <fetch; intValue>×N; aload 1; invokestatic lambda$`: the item is
  parked in its own (now-dead) local-1 slot, the N unboxed captures left below
  it exactly as the static `lambda$(int…, Integer)` signature wants. Parking in
  local 1 needs no extra local, so `512`'s max-locals is untouched. This N-ary
  park/reload **replaces v1's single-capture `swap`**, unifying one and many
  captures (omit the reload and the operands silently invert — the verifier
  accepts it, so the e2e asserts the numeric runtime result, not just opcode
  counts). `314` is now the FILTER twin of `316`: it folds an N-int-capture
  `cp-filter` with the same N-ary park/reload body, opened by a `dup` that keeps
  the item at the BOTTOM of the stack so the predicate (whose captures + a
  reloaded item copy ride above it) can run and the keep label still sees a lone
  `[item]` — the standard `Φ.hone.frame-item` `503` fills, no separate empty-stack
  keep-frame needed. The `"c-filter"` start label keeps `431`/`281` away. `444`
  reverts a LONE single-capture filter (closed on the single-capture park/reload
  body); a lone multi-capture filter is emitted as a standalone mapMulti, exactly
  as `443` leaves a lone multi-capture map. A category-2 (`J`/`D`) capture in a
  filter is now done (issue #661): `120`/`122` are the `cp-filter` mirrors of
  `116`/`117` and `113` bumps the caller max-stack by 2 exactly as `112` does for
  the map (a single reference capture, `119`, was done earlier in #659).
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
  the marker, the same loop-break `442` uses). `443`/`444` both match the
  park/reload single-capture body. `446`/`447` are their TWO-capture twins
  (issue #663): a LONE two-int-capture map or filter (e.g. `map(n -> n + p + q)`,
  `filter(n -> n > lo && n < hi)` with no neighbour to fuse) matches a CLOSED
  state and body of EXACTLY two appends and two fetches, so it reverts to the
  native `invokedynamic` + `Stream.{map,filter}` with the `(II)` factory
  descriptor and both pushes replayed, while a fused operator (two
  park/reload/call triples) cannot match and is left for `502`. A LONE map OR
  filter with THREE OR MORE captures fails both the single- and two-capture
  closed patterns and is still emitted as a standalone `mapMulti` — correct, only
  marginally slower than native, and rarer than a lone one- or two-capture
  operator. Reverting it is **not** a matter of writing the next ladder rung: an
  arity-agnostic lone-vs-fused revert is not expressible with phino 0.0.0.73,
  because a variadic `𝐵-` group backtracks over every split (`Matcher.hs:86`), so
  one pattern matches a fused two-operator body as readily as a lone one — only
  enumerating the EXACT capture count pins the `reload` position that tells them
  apart. The real unblock is a phino counted-repetition matcher (filed as
  `objectionary/phino#747`); until it lands, a lone three-or-more-capture
  operator stays a `mapMulti`.

Other type-preserving element types are **done** (issue #640): `112`'s
`(LX;)LX;` backreference guard admits a capturing map over any reference element
type (`Stream<String>`, …), and `443` rebuilds the reverted SAM type from the
distill's bridge fields so a lone such map still comes out native — see the
`streams/closure-string.yml` end-to-end fixture.

A SINGLE reference capture is **done** (issue #647): `112`'s `(L…;)` guard
branch lifts `map(n -> n + base.size())` over a captured `List`, `115` peels the
`aload` into the shared List with no box/unbox, and the whole stateful emit path
(`316`/`401`/`502`/`512`/`521`/`601`) lowers it to one `Stream.mapMulti` — see
the `streams/closure-reference.yml` end-to-end fixture. A lone reference-capture
map is reverted to its native `invokedynamic` + `Stream.map` by
`445-unfused-reference-capturing-map-to-invokedynamic` (issue #657, the
reference-capture twin of `443`, closed on the box/unbox-free reference state and
park/reload body), so it is never pessimised — see the
`streams/closure-reference-lone.yml` end-to-end fixture.

Category-2 (`J`/`D`) captures are **done** (issue #652): `112`'s `\([IJD]+\)`
guard admits one or more long/double captures (and mixed int/long/double runs),
`116`/`117` peel the `lload`/`dload` pushes with `Long`/`Double` box/unbox, and
`112` bumps the caller max-stack by 2 to absorb the two-slot append — see the
`streams/closure-long.yml` end-to-end fixture. A lone single long/double-capture
map is not reverted by `443` (closed on the int box/unbox shape), so it is emitted
as a standalone `mapMulti`, like the lone-reference and lone-multi-capture maps.

The multi-capture FILTER is **done** (issue #655): `113`'s `\([IJD]+\)` guard
admits one or more primitive captures, `118` peels the int pushes (`120`/`122`
the long / double ones, issue #661) into the shared List, and `314` folds them
with the same N-ary park/reload body as a capturing map — the keep-frame stays
`503`'s plain `Φ.hone.frame-item` because the body keeps the item at the bottom
of the stack. So `filter(n -> n > lo && n < hi)` fuses with its trailing
`mapToInt` into one `Stream.mapMultiToInt`; see the `multiFil` case in
`streams/closures.yml`. A lone TWO-capture filter is reverted by `447` (the
two-capture twin of `444`; see below); a lone filter with three or more captures
is still emitted as a standalone `mapMulti`.

The lone-TWO-capture revert is **done** (issue #663): `446`/`447` are the
two-capture twins of `443`/`444`. A lone two-int-capture map or filter that never
fused — e.g. `map(n -> n + p + q)` ending in a terminal `reduce`, or
`filter(n -> n > lo && n < hi)` ending in `count` — is reverted to its native
`invokedynamic` + `Stream.{map,filter}`, replaying BOTH `iload` pushes and the
`(II)` factory descriptor and marking the call `reverted ↦ Φ.true` so `112`/`113`
cannot re-lift it. Both rules match a CLOSED state and body of EXACTLY two appends
and two fetches, so a FUSED operator (two park/reload/call triples) cannot match
and is left for `502` — the never-touch-a-fused-distill guarantee `443`/`444` get,
extended by one capture. See the `streams/closure-multi-lone.yml` end-to-end
fixture. A lone map/filter with THREE OR MORE captures still stays a standalone
`mapMulti` (a follow-up puzzle).

The category-2 (`J`/`D`) capture FILTER is **done** (issue #661): `113`'s
`\([IJD]+\)Ljava/util/function/Predicate;` guard admits long/double captures
and bumps the caller max-stack by 2 exactly as `112` does for the map,
`120`/`122` peel the `lload`/`dload` pushes with `Long`/`Double` box/unbox (the
`cp-filter` mirrors of `116`/`117`), and `314` folds them into a stateful
distill — see `streams/closure-long-filter.yml`.

Deferred puzzles (each extends the same shared-List channel): a MULTI-reference /
mixed-with-reference capture run (needs positional capture-type extraction);
`this`-field captures; and capturing `peek`/`mapToX`. The lone-THREE-OR-MORE-capture
map/filter revert is **blocked upstream**, not deferred work to do here: an
arity-agnostic lone-vs-fused revert is not expressible with phino 0.0.0.73 (a
variadic `𝐵-` group backtracks over every split — `Matcher.hs:86` — so one
pattern matches a fused body as readily as a lone one, which is why the revert is
one rule per arity), so it waits on a phino counted-repetition matcher
(`objectionary/phino#747`) rather than another `446`/`447`-style ladder rung. See
the puzzle marker in `112`'s header. (The lone-reference-map
revert is **done** via `445`; the lone-two-capture map/filter revert is **done**
via `446`/`447`; a category-2 or reference capture in a FILTER is **done** via
`120`/`122` and `119`.)

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
which causes the end-to-end optimize fixtures (the `streams/*.yml`
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
