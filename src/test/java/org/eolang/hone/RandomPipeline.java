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
 * elements, and it must still stop where it stopped. A {@code peek} may
 * therefore sit in front of anything except {@code count()}, the one terminal
 * the API allows to skip the traversal altogether.</p>
 *
 * <p>The grammar does not step around the operations that the rewrite has been
 * known to break — every shape behind #787, #788, #790, #794, #798, #799, #804,
 * #805 and #811 is still reachable, since a generator that avoids the defects we
 * already know about would only prove that we know about them, and nothing
 * about a regression. Nothing is quarantined at the moment: {@code
 * quarantined()} names the shapes that break the rewrite today, so that a suite
 * failing on them every night does not report what is already filed, and both
 * of the clauses it held have been deleted along with the issue that owned
 * them.</p>
 *
 * <p>The walk reaches the three primitive domains, all three of their boxed
 * counterparts — {@code Stream<Long>}, {@code Stream<Integer>} and
 * {@code Stream<Double>} — and two reference domains, one of strings and one of
 * a small value class of its own. It reaches the corners of the terminal API as
 * well: collectors assembled by hand out of {@code Collector.of},
 * {@code Collectors.teeing}, {@code Collectors.flatMapping}, and {@code Optional}
 * chains longer than a single {@code orElse}.</p>
 *
 * <p>Reaching a shape is not the same as reaching it soon, and widening the
 * grammar re-deals every seed. #811 — two operators in a primitive-stream run
 * whose trailing {@code boxed()} keeps it in the boxed domain — was found at seed
 * 334 of the walk as it stood before this grammar grew, already well outside the
 * 120 seeds walked by default, and the re-dealt walk does not reach it inside 360
 * at all. That is the argument for the deterministic pack: a shape the walk finds
 * earns one of its own under {@code optimize/streams/} the day it is understood,
 * because the walk is the net that catches a defect, not the test that keeps it
 * caught.</p>
 *
 * @since 0.30.0
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
     * field, and an {@code end} fragment is prefixed with {@code *} when the API
     * pins how much of the stream it walks. Nothing here is parallel and nothing
     * is {@code findAny}: the value a pipeline prints has to be the same on
     * every run, or it cannot be an oracle.</p>
     *
     * <p>A production wider than one line is joined from pieces, which is what
     * the few {@code String.join} entries are. The terminals that tee, and the
     * ones that assemble a {@code Collector} by hand, spell their type arguments
     * out: a pipeline is printed through {@code String.valueOf}, and its
     * overloads bound the result of such a terminal by {@code char[]} as well as
     * by {@code Object}, which no inferred type can satisfy.</p>
     */
    private static final String GRAMMAR = String.join(
        RandomPipeline.EOL,
        "long|source|LongStream.of(NUMBERS)",
        "long|source|Arrays.stream(NUMBERS)",
        "long|source|LongStream.range(1L, 9L)",
        "long|source|LIST.stream().mapToLong(Long::longValue)",
        "long|source|LongStream.iterate(1L, n -> n + 3L).limit(7L)",
        "long|source|LongStream.iterate(1L, n -> n < 50L, n -> n * 2L)",
        "long|source|LongStream.concat(LongStream.of(NUMBERS), LongStream.range(1L, 4L))",
        "long|stage|map(n -> n + 1L)",
        "long|stage|map(n -> n * 2L)",
        "long|stage|filter(n -> n % 3L != 0L)",
        "long|stage|distinct()",
        "long|stage|sorted()",
        "long|stage|skip(2L)",
        "long|stage|limit(9L)",
        "long|stage|takeWhile(n -> n < 40L)",
        "long|stage|dropWhile(n -> n < 3L)",
        "long|stage|flatMap(n -> LongStream.of(n, n + 1L))",
        "long|stage|mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 1L); })",
        "long|peek|peek(x -> SUM[0] += x)",
        "long|turn|boxed()|boxed",
        "long|turn|mapToObj(Long::toString)|words",
        "long|turn|mapToObj(n -> new Pair(n, Long.toString(n)))|pairs",
        "long|turn|mapToInt(n -> (int) n)|ints",
        "long|turn|asDoubleStream()|reals",
        "long|turn|mapToObj(n -> (double) n)|dbox",
        "long|end|*sum()",
        "long|end|*reduce(0L, Long::sum)",
        "long|end|*max().orElse(0L)",
        "long|end|*min().orElse(0L)",
        "long|end|*average().orElse(0.0)",
        "long|end|*summaryStatistics().getSum()",
        "long|end|*toArray().length",
        "long|end|*mapToObj(Long::toString).collect(Collectors.joining(\"+\"))",
        "long|end|*boxed().collect(Collectors.toList()).size()",
        "long|end|*max().stream().mapToObj(Long::toString).findFirst().orElse(\"none\")",
        "long|end|count()",
        "long|end|*anyMatch(n -> n > 5L)",
        "long|end|*allMatch(n -> n > 0L)",
        "ints|source|IntStream.of(INTS)",
        "ints|source|Arrays.stream(INTS)",
        "ints|source|IntStream.rangeClosed(1, 7)",
        "ints|source|IntStream.iterate(2, n -> n + 5).limit(5L)",
        "ints|source|PROSE.chars()",
        "ints|source|IntStream.concat(IntStream.of(INTS), IntStream.range(0, 3))",
        "ints|stage|map(n -> n + 2)",
        "ints|stage|map(n -> n * 3)",
        "ints|stage|filter(n -> n > 2)",
        "ints|stage|distinct()",
        "ints|stage|sorted()",
        "ints|stage|skip(1L)",
        "ints|stage|limit(8L)",
        "ints|stage|takeWhile(n -> n < 30)",
        "ints|stage|dropWhile(n -> n < 2)",
        "ints|stage|flatMap(n -> IntStream.of(n, n + 1))",
        "ints|stage|mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 2); })",
        "ints|peek|peek(x -> SUM[0] += x)",
        "ints|turn|asLongStream()|long",
        "ints|turn|asDoubleStream()|reals",
        "ints|turn|boxed()|ibox",
        "ints|turn|mapToObj(n -> n / 2.0)|dbox",
        "ints|turn|mapToObj(Integer::toString)|words",
        "ints|end|*sum()",
        "ints|end|*reduce(1, (a, b) -> a + b)",
        "ints|end|*average().orElse(0.0)",
        "ints|end|*max().orElse(0)",
        "ints|end|*min().orElse(0)",
        "ints|end|*summaryStatistics().getMax()",
        "ints|end|*toArray().length",
        "ints|end|*boxed().toList().size()",
        "ints|end|*max().stream().boxed().findFirst().map(Object::toString).orElse(\"none\")",
        "ints|end|count()",
        "ints|end|*noneMatch(n -> n < 0)",
        "reals|source|DoubleStream.of(DECIMALS)",
        "reals|source|Arrays.stream(DECIMALS)",
        "reals|source|DoubleStream.iterate(0.5, d -> d + 1.25).limit(6L)",
        "reals|source|DoubleStream.concat(DoubleStream.of(DECIMALS), DoubleStream.of(0.75))",
        "reals|stage|map(d -> d * 1.5)",
        "reals|stage|map(d -> d + 0.25)",
        "reals|stage|filter(d -> d > 1.0)",
        "reals|stage|distinct()",
        "reals|stage|sorted()",
        "reals|stage|skip(1L)",
        "reals|stage|limit(7L)",
        "reals|stage|takeWhile(d -> d < 12.0)",
        "reals|stage|flatMap(d -> DoubleStream.of(d, d * 0.5))",
        "reals|stage|mapMulti((d, sink) -> { sink.accept(d); sink.accept(d * 2.0); })",
        "reals|peek|peek(x -> SUM[0] += (long) x)",
        "reals|turn|boxed()|dbox",
        "reals|turn|mapToLong(d -> (long) d)|long",
        "reals|turn|mapToInt(d -> (int) d)|ints",
        "reals|turn|mapToObj(Double::toString)|words",
        "reals|end|*sum()",
        "reals|end|*average().orElse(0.0)",
        "reals|end|*max().orElse(0.0)",
        "reals|end|*min().orElse(0.0)",
        "reals|end|*summaryStatistics().getCount()",
        "reals|end|*toArray().length",
        "reals|end|*mapToObj(Double::toString).collect(Collectors.joining(\";\"))",
        "reals|end|*max().stream().boxed().findFirst().map(d -> d * 2.0).orElse(0.0)",
        "reals|end|count()",
        "boxed|source|LIST.stream()",
        "boxed|source|Stream.of(3L, 7L, 7L, 1L, 9L)",
        "boxed|source|Arrays.stream(NUMBERS).boxed()",
        "boxed|source|Stream.iterate(1L, n -> n + 2L).limit(6L)",
        "boxed|source|Stream.generate(() -> 7L).limit(4L)",
        "boxed|source|Stream.concat(LIST.stream(), Stream.of(42L))",
        "boxed|stage|map(n -> n + 1L)",
        "boxed|stage|filter(n -> n % 2L == 0L)",
        "boxed|stage|distinct()",
        "boxed|stage|sorted()",
        "boxed|stage|sorted(Comparator.reverseOrder())",
        "boxed|stage|skip(1L)",
        "boxed|stage|limit(6L)",
        "boxed|stage|takeWhile(n -> n < 20L)",
        "boxed|stage|dropWhile(n -> n < 4L)",
        "boxed|stage|flatMap(n -> Stream.of(n, n + 2L))",
        "boxed|stage|<Long>mapMulti((n, sink) -> { sink.accept(n); sink.accept(n * 3L); })",
        "boxed|peek|peek(x -> SUM[0] += x)",
        "boxed|turn|mapToLong(Long::longValue)|long",
        "boxed|turn|mapToInt(Long::intValue)|ints",
        "boxed|turn|mapToDouble(Long::doubleValue)|reals",
        "boxed|turn|map(Long::doubleValue)|dbox",
        "boxed|turn|flatMapToLong(n -> LongStream.of(n, n + 1L))|long",
        "boxed|turn|flatMapToInt(n -> IntStream.of(n.intValue()))|ints",
        "boxed|turn|flatMapToDouble(n -> DoubleStream.of(n, n / 2.0))|reals",
        "boxed|turn|mapMultiToLong((n, sink) -> { sink.accept(n); sink.accept(n * 2L); })|long",
        "boxed|turn|mapMultiToDouble((n, sink) -> sink.accept(n / 4.0))|reals",
        "boxed|turn|map(Object::toString)|words",
        "boxed|turn|map(n -> new Pair(n, Long.toString(n)))|pairs",
        "boxed|end|*collect(Collectors.toList()).size()",
        "boxed|end|*collect(Collectors.toSet()).size()",
        "boxed|end|*collect(Collectors.counting())",
        "boxed|end|*collect(Collectors.summingLong(Long::longValue))",
        "boxed|end|*toList().size()",
        "boxed|end|*mapToLong(Long::longValue).sum()",
        "boxed|end|*reduce(0L, Long::sum)",
        "boxed|end|*min(Comparator.naturalOrder()).orElse(0L)",
        "boxed|end|*collect(Collectors.flatMapping(n -> Stream.of(n, n), Collectors.counting()))",
        String.join(
            "",
            "boxed|end|*collect(Collector.<Long, long[], Long>of(() -> new long[1], ",
            "(a, n) -> a[0] += n, (a, b) -> new long[] {a[0] + b[0]}, a -> a[0]))"
        ),
        "boxed|end|count()",
        "boxed|end|*findFirst().orElse(-1L)",
        "boxed|end|*max(Comparator.naturalOrder()).map(n -> n + 1L).orElse(0L)",
        "boxed|end|*anyMatch(n -> n > 5L)",
        "ibox|source|INTEGERS.stream()",
        "ibox|source|IntStream.of(INTS).boxed()",
        "ibox|source|Stream.of(5, 2, 9, 2, 7)",
        "ibox|stage|map(n -> n + 1)",
        "ibox|stage|filter(n -> n % 2 == 0)",
        "ibox|stage|distinct()",
        "ibox|stage|sorted()",
        "ibox|stage|sorted(Comparator.reverseOrder())",
        "ibox|stage|skip(1L)",
        "ibox|stage|limit(5L)",
        "ibox|stage|takeWhile(n -> n < 9)",
        "ibox|stage|dropWhile(n -> n < 3)",
        "ibox|stage|flatMap(n -> Stream.of(n, n))",
        "ibox|stage|<Integer>mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 3); })",
        "ibox|peek|peek(x -> SUM[0] += x)",
        "ibox|turn|mapToInt(Integer::intValue)|ints",
        "ibox|turn|mapToLong(Integer::longValue)|long",
        "ibox|turn|mapToDouble(Integer::doubleValue)|reals",
        "ibox|turn|map(Integer::doubleValue)|dbox",
        "ibox|turn|flatMapToInt(n -> IntStream.of(n, n + 1))|ints",
        "ibox|turn|map(n -> n.toString())|words",
        "ibox|end|*mapToInt(Integer::intValue).sum()",
        "ibox|end|*collect(Collectors.summingInt(Integer::intValue))",
        "ibox|end|*collect(Collectors.toSet()).size()",
        "ibox|end|*toList().size()",
        "ibox|end|*max(Comparator.naturalOrder()).orElse(-1)",
        "ibox|end|*collect(Collectors.flatMapping(n -> Stream.of(n, -n), Collectors.counting()))",
        "ibox|end|count()",
        "ibox|end|*findFirst().orElse(-1)",
        "ibox|end|*findFirst().map(n -> n * 2).filter(n -> n > 4).orElse(-1)",
        "words|source|WORDS.stream()",
        "words|source|Stream.of(\"alpha\", \"beta\", \"gamma\", \"beta\")",
        "words|source|Arrays.stream(PROSE.split(\" \"))",
        "words|source|Stream.generate(() -> \"zeta\").limit(3L)",
        "words|source|Stream.iterate(\"a\", s -> s + \"b\").limit(4L)",
        "words|source|Stream.concat(WORDS.stream(), Stream.of(\"omega\"))",
        "words|stage|map(String::toUpperCase)",
        "words|stage|map(s -> s + \"!\")",
        "words|stage|filter(s -> !s.isEmpty())",
        "words|stage|distinct()",
        "words|stage|sorted()",
        "words|stage|sorted(Comparator.comparingInt(String::length))",
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
        "words|turn|flatMapToInt(String::chars)|ints",
        "words|turn|mapMultiToInt((s, sink) -> sink.accept(s.length()))|ints",
        "words|turn|map(s -> (long) s.length())|boxed",
        "words|turn|map(s -> (double) s.length())|dbox",
        "words|turn|map(String::length)|ibox",
        "words|turn|map(s -> new Pair((long) s.length(), s))|pairs",
        "words|end|*collect(Collectors.joining(\"-\"))",
        "words|end|*collect(Collectors.joining(\"-\", \"[\", \"]\"))",
        "words|end|*collect(Collectors.toList()).size()",
        "words|end|*collect(Collectors.averagingInt(String::length))",
        "words|end|*collect(Collectors.mapping(String::toUpperCase, Collectors.toSet())).size()",
        "words|end|*collect(Collectors.partitioningBy(String::isEmpty)).get(false).size()",
        "words|end|*collect(ArrayList::new, List::add, List::addAll).size()",
        "words|end|*collect(Collectors.groupingBy(String::length, Collectors.counting())).size()",
        "words|end|*collect(Collectors.toMap(s -> s, String::length, (a, b) -> a, TreeMap::new))",
        "words|end|*collect(Collectors.flatMapping(s -> Stream.of(s, s), Collectors.counting()))",
        String.join(
            "",
            "words|end|*collect(Collectors.<String, Long, String, String>teeing(",
            "Collectors.counting(), Collectors.joining(), (n, j) -> n + j))"
        ),
        String.join(
            "",
            "words|end|*collect(Collector.<String, StringBuilder, String>of(",
            "StringBuilder::new, StringBuilder::append, StringBuilder::append, ",
            "StringBuilder::toString))"
        ),
        "words|end|*toList().size()",
        "words|end|*reduce(\"\", String::concat)",
        "words|end|*max(Comparator.naturalOrder()).orElse(\"none\")",
        "words|end|count()",
        "words|end|*findFirst().orElse(\"none\")",
        "words|end|*findFirst().filter(s -> !s.isEmpty()).map(String::toUpperCase).orElse(\"x\")",
        "words|end|*anyMatch(String::isEmpty)",
        "pairs|source|PAIRS.stream()",
        "pairs|source|Stream.of(new Pair(3L, \"a\"), new Pair(1L, \"b\"), new Pair(3L, \"a\"))",
        "pairs|stage|map(p -> new Pair(p.key() + 1L, p.text()))",
        "pairs|stage|filter(p -> p.key() > 1L)",
        "pairs|stage|distinct()",
        "pairs|stage|sorted()",
        "pairs|stage|sorted(Comparator.comparing(Pair::text))",
        "pairs|stage|skip(1L)",
        "pairs|stage|limit(4L)",
        "pairs|stage|takeWhile(p -> p.key() < 9L)",
        "pairs|stage|dropWhile(p -> p.key() < 2L)",
        "pairs|stage|flatMap(p -> Stream.of(p, p))",
        "pairs|stage|<Pair>mapMulti((p, sink) -> { sink.accept(p); sink.accept(p); })",
        "pairs|peek|peek(x -> SUM[0] += x.key())",
        "pairs|turn|mapToLong(Pair::key)|long",
        "pairs|turn|mapToInt(p -> p.text().length())|ints",
        "pairs|turn|map(Pair::text)|words",
        "pairs|turn|map(Pair::key)|boxed",
        "pairs|turn|map(p -> p.key() / 2.0)|dbox",
        "pairs|end|*map(Pair::text).collect(Collectors.joining(\"-\"))",
        "pairs|end|*mapToLong(Pair::key).sum()",
        "pairs|end|*collect(Collectors.counting())",
        "pairs|end|*toList().size()",
        "pairs|end|count()",
        "pairs|end|*findFirst().map(Pair::text).orElse(\"none\")",
        "pairs|end|*findFirst().map(Pair::text).filter(s -> s.length() > 1).orElse(\"x\")",
        "dbox|source|DOUBLES.stream()",
        "dbox|source|DoubleStream.of(DECIMALS).boxed()",
        "dbox|source|Stream.of(1.5, 2.25, 1.5, 8.125)",
        "dbox|source|Stream.iterate(0.5, d -> d + 1.5).limit(5L)",
        "dbox|source|Stream.concat(DOUBLES.stream(), Stream.of(9.5))",
        "dbox|stage|map(d -> d * 1.5)",
        "dbox|stage|map(d -> d + 0.25)",
        "dbox|stage|filter(d -> d > 1.0)",
        "dbox|stage|distinct()",
        "dbox|stage|sorted()",
        "dbox|stage|sorted(Comparator.reverseOrder())",
        "dbox|stage|skip(1L)",
        "dbox|stage|limit(6L)",
        "dbox|stage|takeWhile(d -> d < 12.0)",
        "dbox|stage|dropWhile(d -> d < 1.0)",
        "dbox|stage|flatMap(d -> Stream.of(d, d + 0.5))",
        "dbox|stage|<Double>mapMulti((d, sink) -> { sink.accept(d); sink.accept(d * 2.0); })",
        "dbox|peek|peek(x -> SUM[0] += x.longValue())",
        "dbox|turn|mapToDouble(Double::doubleValue)|reals",
        "dbox|turn|mapToLong(Double::longValue)|long",
        "dbox|turn|mapToInt(Double::intValue)|ints",
        "dbox|turn|map(Double::longValue)|boxed",
        "dbox|turn|map(Double::intValue)|ibox",
        "dbox|turn|map(d -> Double.toString(d))|words",
        "dbox|turn|map(d -> new Pair(d.longValue(), Double.toString(d)))|pairs",
        "dbox|turn|flatMapToDouble(d -> DoubleStream.of(d, d * 0.5))|reals",
        "dbox|turn|mapMultiToDouble((d, sink) -> sink.accept(d / 2.0))|reals",
        "dbox|end|*mapToDouble(Double::doubleValue).sum()",
        "dbox|end|*reduce(0.0, Double::sum)",
        "dbox|end|*collect(Collectors.summingDouble(Double::doubleValue))",
        "dbox|end|*collect(Collectors.averagingDouble(Double::doubleValue))",
        String.join(
            "",
            "dbox|end|*collect(Collectors.<Double, Long, List<Double>, Long>teeing(",
            "Collectors.counting(), Collectors.toList(), (n, l) -> n + l.size()))"
        ),
        "dbox|end|*collect(Collectors.toSet()).size()",
        "dbox|end|*toList().size()",
        "dbox|end|*max(Comparator.naturalOrder()).orElse(0.0)",
        "dbox|end|*findFirst().map(d -> d * 2.0).filter(d -> d > 1.0).orElse(0.0)",
        "dbox|end|count()"
    );

    /**
     * The template of the class, with the package, the name, and the pipeline.
     */
    private static final String TEMPLATE = String.join(
        RandomPipeline.EOL,
        "package %1$s;",
        "",
        "import java.util.ArrayList;",
        "import java.util.Arrays;",
        "import java.util.Comparator;",
        "import java.util.List;",
        "import java.util.TreeMap;",
        "import java.util.stream.Collector;",
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
        "    private static final List<Integer> INTEGERS = List.of(6, 3, 6, 11, 4);",
        "",
        "    private static final List<Double> DOUBLES = List.of(0.5, 2.5, 0.5, 3.75);",
        "",
        "    private static final List<String> WORDS = List.of(\"phi\", \"rule\", \"phi\");",
        "",
        "    private static final String PROSE = \"the quick brown fox jumps over it\";",
        "",
        "    private static final List<Pair> PAIRS =",
        "        List.of(new Pair(2L, \"two\"), new Pair(5L, \"five\"), new Pair(2L, \"two\"));",
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
        "",
        "    static final class Pair implements Comparable<Pair> {",
        "",
        "        private final long num;",
        "",
        "        private final String word;",
        "",
        "        Pair(final long key, final String text) {",
        "            this.num = key;",
        "            this.word = text;",
        "        }",
        "",
        "        long key() {",
        "            return this.num;",
        "        }",
        "",
        "        String text() {",
        "            return this.word;",
        "        }",
        "",
        "        @Override",
        "        public String toString() {",
        "            return this.num + \":\" + this.word;",
        "        }",
        "",
        "        @Override",
        "        public boolean equals(final Object other) {",
        "            return other instanceof Pair",
        "                && ((Pair) other).num == this.num",
        "                && ((Pair) other).word.equals(this.word);",
        "        }",
        "",
        "        @Override",
        "        public int hashCode() {",
        "            return (int) this.num * 31 + this.word.hashCode();",
        "        }",
        "",
        "        @Override",
        "        public int compareTo(final Pair other) {",
        "            return Long.compare(this.num, other.num);",
        "        }",
        "    }",
        "}",
        ""
    );

    /**
     * The domains a pipeline may travel through, in the order it may start in.
     */
    private static final List<String> DOMAINS = Arrays.asList(
        "boxed", "dbox", "ibox", "ints", "long", "pairs", "reals", "words"
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
    private static final int STAGES = 7;

    /**
     * How many times a seed may re-roll away from a quarantined shape before
     * the generator gives up and says so.
     */
    private static final int ATTEMPTS = 32;

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
        final List<String> walk = this.rolled();
        final StringBuilder pipe = new StringBuilder(RandomPipeline.code(walk.get(0)));
        for (final String step : walk.subList(1, walk.size())) {
            pipe.append(RandomPipeline.STEP).append(RandomPipeline.code(step));
        }
        return String.format(RandomPipeline.TEMPLATE, pkg, name, pipe);
    }

    /**
     * Every fragment the grammar can reach, whatever domain it belongs to.
     * @return The fragments, as they are written in the grammar
     */
    static List<String> productions() {
        final List<String> all = new ArrayList<>(0);
        for (final List<String> fragments : RandomPipeline.table().values()) {
            all.addAll(fragments);
        }
        return all;
    }

    /**
     * The Java code of a production, without the marks the grammar wraps it in.
     * @param production The production, as it is written in the grammar
     * @return What the walk appends to a pipeline when it picks this production
     */
    static String fragment(final String production) {
        String code = production;
        final int lands = code.lastIndexOf('|');
        if (lands >= 0 && RandomPipeline.DOMAINS.contains(code.substring(lands + 1))) {
            code = code.substring(0, lands);
        }
        if (code.charAt(0) == '*') {
            code = code.substring(1);
        }
        return code;
    }

    /**
     * The walk this seed settles on, re-rolled away from every quarantined
     * shape.
     * @return The grammar lines the class is printed from
     */
    private List<String> rolled() {
        List<String> found = new ArrayList<>(0);
        for (int attempt = 0; attempt < RandomPipeline.ATTEMPTS; ++attempt) {
            final List<String> walk =
                RandomPipeline.walk(new Random(this.seed * 31L + attempt));
            if (RandomPipeline.quarantined(walk).isEmpty()) {
                found = walk;
                break;
            }
        }
        if (found.isEmpty()) {
            throw new IllegalStateException(
                String.format(
                    "seed %d walks into a quarantined shape %d times in a row",
                    this.seed, RandomPipeline.ATTEMPTS
                )
            );
        }
        return found;
    }

    /**
     * One walk through the grammar, as the lines of it that were picked.
     * @param rnd The source of randomness
     * @return The grammar lines, a source first, a terminal last
     */
    private static List<String> walk(final Random rnd) {
        final Map<String, List<String>> table = RandomPipeline.table();
        String domain = RandomPipeline.DOMAINS.get(rnd.nextInt(RandomPipeline.DOMAINS.size()));
        final List<String> steps = new ArrayList<>(0);
        steps.add(
            String.join("|", domain, "source", RandomPipeline.pick(table, domain, "source", rnd))
        );
        final int total = 1 + rnd.nextInt(RandomPipeline.STAGES);
        boolean peeked = false;
        for (int stage = 0; stage < total; ++stage) {
            final String role = RandomPipeline.ROLES.get(rnd.nextInt(RandomPipeline.ROLES.size()));
            final String fragment = RandomPipeline.pick(table, domain, role, rnd);
            steps.add(String.join("|", domain, role, fragment));
            peeked = peeked || "peek".equals(role);
            if ("turn".equals(role)) {
                domain = fragment.substring(fragment.lastIndexOf('|') + 1);
            }
        }
        steps.add(
            String.join("|", domain, "end", RandomPipeline.terminal(table, domain, peeked, rnd))
        );
        return steps;
    }

    /**
     * The Java code of one line of a walk.
     * @param line The line, as {@code domain|role|fragment}
     * @return What the pipeline appends for it
     */
    private static String code(final String line) {
        return RandomPipeline.fragment(line.split("\\|", 3)[2]);
    }

    /**
     * The issue that quarantines this walk, if one does.
     *
     * <p>A shape that breaks the rewrite today, emitting a class the verifier
     * rejects, gets a clause here once it is reported upstream: a walk that
     * reaches it is re-rolled from a derived seed instead of being generated, so
     * the suite stays a net for regressions rather than a standing failure. Each
     * clause is as narrow as the shape it owns and is named by its issue, so
     * closing the issue widens the walk again by deleting one.</p>
     *
     * <p>There is nothing to quarantine right now. The two clauses this method
     * held are both gone: #804's, a {@code filter} behind a {@code dropWhile},
     * which lost the copy of the item its predicate eats, and #805's, a
     * conversion into an object domain with a {@code peek}, a {@code filter} or
     * another guard between it and a stateful guard, whose keep-frame was read
     * off whatever opcode happened to sit in front of it. Add the next one the
     * day the walk finds a shape we cannot fix at once.</p>
     *
     * @param walk The lines the walk picked
     * @return The issue that owns the shape, or an empty string
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static String quarantined(final List<String> walk) {
        return "";
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
     * <p>A pipeline that counted elements with a {@code peek} may not end in a
     * terminal the API lets skip the traversal, which is {@code count()} and
     * nothing else. Short-circuiting terminals stay allowed on purpose: how far
     * a sequential stream walks before it stops is part of what the rewrite has
     * to preserve.</p>
     *
     * @param table The grammar
     * @param domain The element domain the pipeline ended in
     * @param peeked TRUE if a {@code peek} counted the elements
     * @param rnd The source of randomness
     * @return The terminal fragment, without its pinned-traversal mark
     */
    private static String terminal(final Map<String, List<String>> table,
        final String domain, final boolean peeked, final Random rnd) {
        final List<String> ends = new ArrayList<>(0);
        for (final String end : table.get(String.join("|", domain, "end"))) {
            if (!peeked || end.charAt(0) == '*') {
                ends.add(end);
            }
        }
        return RandomPipeline.fragment(ends.get(rnd.nextInt(ends.size())));
    }
}
