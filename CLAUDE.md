# CLAUDE.md

Operational guidance for agents working on the `.phr` rewrite rules.
`README.md` explains *what* the pipeline does and how it is staged
(`1xx`–`7xx`), the shape of a `.phr` file, and the role of each phase — read
it first. This file covers only *where things live, how to add and debug a
rule, how to test, and what to avoid*.

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

## How to add a new rule

1. **Pick the stage.** Decide which of the seven phases (`1xx`–`7xx`,
   described in README) the rule belongs to — or that you are introducing a
   new phase between two — and pick the smallest unused `NNN-` prefix that
   places it correctly. Execution order is alphabetical by filename
   (`Collections.sort` in `Rules.discover()`), so the prefix *is* the
   schedule. Check `streams/` for the nearest neighbours before choosing.
2. **Write the `.phr` file** under
   `src/main/resources/org/eolang/hone/rules/streams/Nxx/` (the `Nxx/`
   subdirectory whose digit matches the rule's hundreds prefix). Start from
   the closest existing rule and keep its header block (`SPDX` +
   `# yamllint disable rule:line-length`).
3. **Run small-steps locally** on a representative `.phi` to see what your
   rule produces:

   ```bash
   mvn -Dhone.small-steps=true -Dhone.rules='streams/*' \
       org.eolang:hone-maven-plugin:build \
       org.eolang:hone-maven-plugin:optimize
   ```

   Inspect `target/hone/phi-optimized/...phi.NN` files to verify the delta.
4. **Add a single-rule unit test** under `src/test/phino/` (a `.yml` pack
   named after the rule) with a minimal input and the expected output.
5. **Add or update an end-to-end YAML** in
   `src/test/resources/org/eolang/hone/optimize/`. These specify the Java
   source to compile, the expected `log` lines, and the expected opcode
   counts (`before` = `javac` bytecode, `after` = optimized bytecode; a count
   of `0` asserts absence). Updating the `after` counts is normal when a rule
   changes how a pipeline is lowered.
6. **Bump the phino version** in `default-phino-version.txt` only if the rule
   depends on syntax or behaviour that ships in a newer phino.

## How to debug a rule

Run a single rule by hand without Maven:

```bash
phino rewrite --max-cycles 1 --max-depth 500 --sweet \
  --rule src/main/resources/org/eolang/hone/rules/streams/4xx/401-fuse.phr \
  /tmp/Foo.phi
```

The rewritten expression is printed on stdout. In small-steps mode
(`<smallSteps>true</smallSteps>` or `-Dhone.small-steps=true`) `rewrite.sh`
calls phino once per rule and saves `Foo.phi.01`, `Foo.phi.02`, … in
`target/hone/phi-optimized/`; `diff Foo.phi.07 Foo.phi.08` then shows exactly
what rule `nnn` did — the canonical way to bisect a misbehaving rule.

## How to extend beyond `streams/`

Two independent mechanisms:

- **Built-in subfolder.** Add a sibling directory next to `streams/` (for
  example `arithmetic/`), drop `.phr` files in with the same conventions, and
  select them with `<rules>arithmetic/*</rules>`. Anything under
  `org/eolang/hone/rules/` is discovered automatically via ClassGraph. The
  selection grammar supports wildcards and negation:
  `streams/4xx/*,!streams/4xx/411-*`.
- **External (extra) rules.** Without rebuilding the plugin, point `<extra>`
  at a directory of `.phr`/`.yml` files. `OptimizeMojo` copies them into a
  `hone-extra/` directory and `entry.sh` appends them *after* the built-in
  selection. Useful for project-local experiments.

## Running the test suite

`pom.xml` sets `<excludedGroups>deep</excludedGroups>` by default, which skips
the end-to-end optimize fixtures (`optimize/streams/*.yml`) and the
single-rule packs (`src/test/phino/*.yml`) — so `mvn test` alone exercises
only about half the suite and silently hides `.phr` regressions. Always run

```bash
mvn -Pdeep test
```

when verifying a rule change; the `deep` profile clears `excludedGroups` so
every `@Tag("deep")` test runs against the real `phino` binary on the host.

## Tools to keep on hand

- **phino** — install on the host (`cabal install phino` matching the version
  pin in `default-phino-version.txt`) so the plugin skips Docker. `Phino`
  checks `phino --version` and runs directly if it matches; otherwise it falls
  back to Docker, which is slower.
- **jeo-maven-plugin** — the bytecode↔XMIR translator. When debugging a
  roundtrip failure, run `jeo:disassemble` and `jeo:assemble` by hand to
  bisect whether phino or jeo introduced the corruption.
- **GNU coreutils** — `entry.sh` and `rewrite.sh` need GNU `realpath` (or
  `grealpath`) and `parallel`. On macOS: `brew install coreutils parallel`.
- **`hone.debug=true` and `hone.verbose`** — enable `set -x` in the scripts
  and `--log-level=debug` in phino. Voluminous, but shows every phino
  invocation and shell expansion.
- **`hone.grep-in`** — pre-filter regex on the XMIR text. The default skips
  classes containing neither `map` nor `filter`; set it to `.*` to disable.
- **`target/hone-statistics.csv` and `target/timings.csv`** — produced by
  `rewrite.sh` and `entry.sh`; useful for spotting rules that fire on many
  lines or files that take disproportionate time.

## What not to do

- Do not assume rules are independent. The `4xx` fuse pass relies on every
  operation having already been folded into `distill` by `3xx`; inserting an
  unfolded pragma at `350-` would silently break fusion on that method.
- Do not change `Collections.sort(names)` in `Rules.discover()`. The pipeline
  depends on alphabetical ordering as its scheduling mechanism.
- Do not hand-edit `.class` files in `target/classes-before-hone/`. That
  directory is the backup the plugin makes before mutation; it is the only way
  to recover the pre-optimization bytecode without recompiling.
- Do not introduce a rule whose `result` contains constructs that
  `jeo:assemble` cannot translate back to bytecode. It is easy to invent
  𝜑-calculus that jeo cannot lower; verify roundtripping by running the
  end-to-end optimize goal, not just phino in isolation.
