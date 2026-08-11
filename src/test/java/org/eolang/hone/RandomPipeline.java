/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A random Java class with one stream pipeline in it.
 *
 * <p>The pipeline is a walk through a typed grammar of the Stream API: a
 * source, a few intermediate operations, a terminal. Every step is picked from
 * the productions that the current element domain allows, so whatever comes out
 * compiles. The walk is seeded, therefore the same seed yields the same class on
 * every machine and in every run, and a failure found once can be looked at
 * again.</p>
 *
 * <p>One pipeline per class is deliberate. The JVM verifies a class as a whole,
 * so a rewrite that corrupts one pipeline takes down every method around it; in
 * its own class it takes down only itself, and the other pipelines still
 * report.</p>
 *
 * <p>The class prints two lines: the value its pipeline produced and the number
 * its {@code peek} accumulated. The second line is the traversal contract — a
 * pipeline may be fused into anything at all, but it must still walk the same
 * elements. A {@code peek} is therefore only ever placed in front of a terminal
 * that walks the whole stream: {@code count()} is allowed to skip the traversal
 * altogether, and the short-circuiting terminals stop early by design.</p>
 *
 * <p>The grammar does not step around the operations that the rewrite is known
 * to break today (#787, #788, #790). A generator that avoids the defects we
 * already know about would only prove that we know about them.</p>
 *
 * @since 0.30.0
 * @todo #791:60min Widen the grammar of the walk. It reaches neither
 *  {@code flatMapToInt} and its siblings, nor a {@code Collector} beyond
 *  {@code toList} and {@code joining}, nor an infinite source cut by
 *  {@code limit} ({@code Stream.iterate}, {@code Stream.generate}), nor an
 *  object domain other than {@code Long} and {@code String}. Each of those is a
 *  handful of productions, and every one of them is a shape the fixtures under
 *  {@code optimize/streams/} do not spell either.
 */
final class RandomPipeline {

    /**
     * The line separator of this platform, both in the grammar and in the
     * source code the walk prints.
     */
    private static final String EOL = String.format("%n");

    /**
     * What every step of the pipeline is prefixed with, one step per line.
     */
    private static final String STEP = String.format("%n            .");

    /**
     * The grammar, one production per line, as {@code domain|role|fragment}.
     *
     * <p>A {@code turn} fragment carries the domain it lands in as a fourth
     * field, and an {@code end} fragment is prefixed with {@code *} when it
     * walks the whole stream. Nothing here is parallel and nothing is
     * {@code findAny}: the value a pipeline prints has to be the same on every
     * run, or it cannot be an oracle.</p>
     */
    private static final String GRAMMAR = String.join(
        RandomPipeline.EOL,
        "long|source|LongStream.of(NUMBERS)",
        "long|source|Arrays.stream(NUMBERS)",
        "long|source|LongStream.range(1L, 9L)",
        "long|source|LIST.stream().mapToLong(Long::longValue)",
        "long|stage|map(n -> n + 1L)",
        "long|stage|map(n -> n * 2L)",
        "long|stage|filter(n -> n % 3L != 0L)",
        "long|stage|distinct()",
        "long|stage|sorted()",
        "long|stage|skip(2L)",
        "long|stage|limit(9L)",
        "long|stage|takeWhile(n -> n < 40L)",
        "long|stage|dropWhile(n -> n < 3L)",
        "long|stage|mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 1L); })",
        "long|peek|peek(x -> SUM[0] += x)",
        "long|turn|boxed()|boxed",
        "long|turn|mapToObj(Long::toString)|words",
        "long|turn|mapToInt(n -> (int) n)|ints",
        "long|turn|asDoubleStream()|reals",
        "long|end|*sum()",
        "long|end|*reduce(0L, Long::sum)",
        "long|end|*max().orElse(0L)",
        "long|end|*min().orElse(0L)",
        "long|end|*toArray().length",
        "long|end|count()",
        "long|end|anyMatch(n -> n > 5L)",
        "long|end|allMatch(n -> n > 0L)",
        "ints|source|IntStream.of(INTS)",
        "ints|source|Arrays.stream(INTS)",
        "ints|source|IntStream.rangeClosed(1, 7)",
        "ints|stage|map(n -> n + 2)",
        "ints|stage|map(n -> n * 3)",
        "ints|stage|filter(n -> n > 2)",
        "ints|stage|distinct()",
        "ints|stage|sorted()",
        "ints|stage|skip(1L)",
        "ints|stage|limit(8L)",
        "ints|stage|takeWhile(n -> n < 30)",
        "ints|stage|dropWhile(n -> n < 2)",
        "ints|stage|mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 2); })",
        "ints|peek|peek(x -> SUM[0] += x)",
        "ints|turn|asLongStream()|long",
        "ints|turn|asDoubleStream()|reals",
        "ints|turn|mapToObj(Integer::toString)|words",
        "ints|end|*sum()",
        "ints|end|*reduce(1, (a, b) -> a + b)",
        "ints|end|*average().orElse(0.0)",
        "ints|end|*toArray().length",
        "ints|end|count()",
        "ints|end|noneMatch(n -> n < 0)",
        "reals|source|DoubleStream.of(DECIMALS)",
        "reals|source|Arrays.stream(DECIMALS)",
        "reals|stage|map(d -> d * 1.5)",
        "reals|stage|map(d -> d + 0.25)",
        "reals|stage|filter(d -> d > 1.0)",
        "reals|stage|distinct()",
        "reals|stage|sorted()",
        "reals|stage|skip(1L)",
        "reals|stage|limit(7L)",
        "reals|stage|takeWhile(d -> d < 12.0)",
        "reals|stage|mapMulti((d, sink) -> { sink.accept(d); sink.accept(d * 2.0); })",
        "reals|peek|peek(x -> SUM[0] += (long) x)",
        "reals|turn|mapToLong(d -> (long) d)|long",
        "reals|turn|mapToInt(d -> (int) d)|ints",
        "reals|turn|mapToObj(Double::toString)|words",
        "reals|end|*sum()",
        "reals|end|*average().orElse(0.0)",
        "reals|end|*max().orElse(0.0)",
        "reals|end|*toArray().length",
        "reals|end|count()",
        "boxed|source|LIST.stream()",
        "boxed|source|Stream.of(3L, 7L, 7L, 1L, 9L)",
        "boxed|source|Arrays.stream(NUMBERS).boxed()",
        "boxed|stage|map(n -> n + 1L)",
        "boxed|stage|filter(n -> n % 2L == 0L)",
        "boxed|stage|distinct()",
        "boxed|stage|sorted()",
        "boxed|stage|skip(1L)",
        "boxed|stage|limit(6L)",
        "boxed|stage|takeWhile(n -> n < 20L)",
        "boxed|stage|dropWhile(n -> n < 4L)",
        "boxed|stage|<Long>mapMulti((n, sink) -> { sink.accept(n); sink.accept(n * 3L); })",
        "boxed|peek|peek(x -> SUM[0] += x)",
        "boxed|turn|mapToLong(Long::longValue)|long",
        "boxed|turn|mapToInt(Long::intValue)|ints",
        "boxed|turn|mapToDouble(Long::doubleValue)|reals",
        "boxed|turn|map(Object::toString)|words",
        "boxed|end|*collect(Collectors.toList()).size()",
        "boxed|end|*mapToLong(Long::longValue).sum()",
        "boxed|end|*reduce(0L, Long::sum)",
        "boxed|end|count()",
        "boxed|end|findFirst().orElse(-1L)",
        "boxed|end|anyMatch(n -> n > 5L)",
        "words|source|WORDS.stream()",
        "words|source|Stream.of(\"alpha\", \"beta\", \"gamma\", \"beta\")",
        "words|source|Arrays.stream(PROSE.split(\" \"))",
        "words|stage|map(String::toUpperCase)",
        "words|stage|map(s -> s + \"!\")",
        "words|stage|filter(s -> !s.isEmpty())",
        "words|stage|distinct()",
        "words|stage|sorted()",
        "words|stage|skip(1L)",
        "words|stage|limit(5L)",
        "words|stage|takeWhile(s -> s.length() < 8)",
        "words|stage|dropWhile(s -> s.length() < 3)",
        "words|stage|flatMap(s -> Stream.of(s, s))",
        "words|stage|<String>mapMulti((s, sink) -> { sink.accept(s); sink.accept(s); })",
        "words|peek|peek(x -> SUM[0] += x.length())",
        "words|turn|mapToInt(String::length)|ints",
        "words|turn|mapToLong(s -> (long) s.hashCode())|long",
        "words|turn|mapToDouble(s -> (double) s.length())|reals",
        "words|turn|map(s -> (long) s.length())|boxed",
        "words|end|*collect(Collectors.joining(\"-\"))",
        "words|end|*collect(Collectors.toList()).size()",
        "words|end|*reduce(\"\", String::concat)",
        "words|end|count()",
        "words|end|findFirst().orElse(\"none\")",
        "words|end|anyMatch(String::isEmpty)"
    );

    /**
     * The template of the class, with the package, the name, and the pipeline.
     */
    private static final String TEMPLATE = String.join(
        RandomPipeline.EOL,
        "package %1$s;",
        "",
        "import java.util.Arrays;",
        "import java.util.List;",
        "import java.util.stream.Collectors;",
        "import java.util.stream.DoubleStream;",
        "import java.util.stream.IntStream;",
        "import java.util.stream.LongStream;",
        "import java.util.stream.Stream;",
        "",
        "public final class %2$s {",
        "",
        "    private static final long[] NUMBERS = {3L, 7L, 7L, 1L, 9L, 4L, 2L, 9L};",
        "",
        "    private static final int[] INTS = {5, 2, 9, 2, 7, 1, 8};",
        "",
        "    private static final double[] DECIMALS = {1.5, 2.25, 1.5, 8.125, 0.5};",
        "",
        "    private static final List<Long> LIST = List.of(4L, 8L, 8L, 15L, 16L, 23L);",
        "",
        "    private static final List<String> WORDS = List.of(\"phi\", \"rule\", \"phi\");",
        "",
        "    private static final String PROSE = \"the quick brown fox jumps over it\";",
        "",
        "    private static final long[] SUM = new long[1];",
        "",
        "    private %2$s() {",
        "    }",
        "",
        "    private static String pipe() {",
        "        return String.valueOf(",
        "            %3$s",
        "        );",
        "    }",
        "",
        "    public static void main(final String... args) {",
        "        System.out.println(\"%2$s=\" + pipe());",
        "        System.out.println(\"%2$s.peeked=\" + SUM[0]);",
        "    }",
        "}",
        ""
    );

    /**
     * The domains a pipeline may travel through, in the order it may start in.
     */
    private static final List<String> DOMAINS = Arrays.asList(
        "boxed", "ints", "long", "reals", "words"
    );

    /**
     * What an intermediate step may be, an operation being three times as
     * likely as a conversion to another element domain or a peek.
     */
    private static final List<String> ROLES = Arrays.asList(
        "stage", "stage", "stage", "turn", "peek"
    );

    /**
     * The largest number of intermediate operations in one pipeline.
     */
    private static final int STAGES = 5;

    /**
     * The seed of the walk.
     */
    private final long seed;

    /**
     * Ctor.
     * @param sed The seed, which fully determines the class produced
     */
    RandomPipeline(final long sed) {
        this.seed = sed;
    }

    /**
     * Print the Java source of the class.
     * @param pkg The package to put the class in
     * @param name The name of the class, also the label of its printed value
     * @return Java source code, ready for {@code javac}
     */
    String java(final String pkg, final String name) {
        final Random rnd = new Random(this.seed);
        final Map<String, List<String>> table = RandomPipeline.table();
        String domain = RandomPipeline.DOMAINS.get(rnd.nextInt(RandomPipeline.DOMAINS.size()));
        final StringBuilder pipe = new StringBuilder(
            RandomPipeline.pick(table, domain, "source", rnd)
        );
        final int total = 1 + rnd.nextInt(RandomPipeline.STAGES);
        boolean peeked = false;
        for (int stage = 0; stage < total; ++stage) {
            final String role = RandomPipeline.ROLES.get(rnd.nextInt(RandomPipeline.ROLES.size()));
            final String fragment = RandomPipeline.pick(table, domain, role, rnd);
            if ("turn".equals(role)) {
                final int lands = fragment.lastIndexOf('|');
                pipe.append(RandomPipeline.STEP).append(fragment, 0, lands);
                domain = fragment.substring(lands + 1);
            } else {
                pipe.append(RandomPipeline.STEP).append(fragment);
                peeked = peeked || "peek".equals(role);
            }
        }
        pipe.append(RandomPipeline.STEP).append(
            RandomPipeline.terminal(table, domain, peeked, rnd)
        );
        return String.format(RandomPipeline.TEMPLATE, pkg, name, pipe);
    }

    /**
     * The grammar, indexed by domain and role.
     * @return Map from {@code domain|role} to the fragments it allows
     */
    private static Map<String, List<String>> table() {
        final Map<String, List<String>> productions = new HashMap<>();
        for (final String line : RandomPipeline.GRAMMAR.split(RandomPipeline.EOL)) {
            final String[] parts = line.split("\\|", 3);
            productions.computeIfAbsent(
                String.join("|", parts[0], parts[1]), key -> new ArrayList<>(0)
            ).add(parts[2]);
        }
        return productions;
    }

    /**
     * One fragment of the given domain and role.
     * @param table The grammar
     * @param domain The element domain the pipeline is in
     * @param role What the fragment must be: a source, a stage, a turn, a peek
     * @param rnd The source of randomness
     * @return The fragment, verbatim from the grammar
     */
    private static String pick(final Map<String, List<String>> table,
        final String domain, final String role, final Random rnd) {
        final List<String> fragments = table.get(String.join("|", domain, role));
        return fragments.get(rnd.nextInt(fragments.size()));
    }

    /**
     * The terminal to end the pipeline with.
     *
     * <p>A pipeline that counted elements with a {@code peek} may only end in a
     * terminal that walks the whole stream, since no other terminal promises to
     * touch every element.</p>
     *
     * @param table The grammar
     * @param domain The element domain the pipeline ended in
     * @param peeked TRUE if a {@code peek} counted the elements
     * @param rnd The source of randomness
     * @return The terminal fragment, without its whole-stream mark
     */
    private static String terminal(final Map<String, List<String>> table,
        final String domain, final boolean peeked, final Random rnd) {
        final List<String> ends = new ArrayList<>(0);
        for (final String end : table.get(String.join("|", domain, "end"))) {
            if (!peeked || end.charAt(0) == '*') {
                ends.add(end);
            }
        }
        final String end = ends.get(rnd.nextInt(ends.size()));
        final String clean;
        if (end.charAt(0) == '*') {
            clean = end.substring(1);
        } else {
            clean = end;
        }
        return clean;
    }
}
