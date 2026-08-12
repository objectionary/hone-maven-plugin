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
 * <p>A pipeline is more than the operators in it, so the walk also picks the
 * frame the pipeline sits in, and each frame opens productions the others
 * cannot reach. It picks who holds the method: a {@code static} one, or an
 * instance one, where a lambda may read {@code this} and become an instance
 * method itself, which is the whole population that {@code 121} and {@code 131}
 * exist for and that #689 found empty. It picks whether the body catches what
 * the pipeline throws, and only a body that catches may draw a {@code boom}
 * operator, which throws from inside the walk and names the element it threw on:
 * the message is the oracle for how far a fused body got before it unwound. The
 * four arguments the method takes — a {@code long}, an {@code int}, a
 * {@code double} and a {@code String} — are captured by lambdas all over the
 * grammar, so the capturing metafactory shapes that {@code 112}–{@code 126} lift
 * are reachable from any domain, in any count and any mix of types.</p>
 *
 * <p>Two properties of the pipeline as a whole are picked last, once the walk
 * knows where it ended. A pipeline may go {@code parallel()}, which no rule may
 * fuse a {@code skip} or a {@code distinct} through — {@code 222}–{@code 225}
 * revert exactly that — and which is offered only when nothing counts the
 * traversal and the terminal reduces no floating point, so that what the class
 * prints stays the same on every run. And a terminal may return nothing at all:
 * a {@code forEach} that accumulates, whose only oracle is the number the peek
 * line carries.</p>
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
     * The grammar, one production per line, as {@code domain|role|fragment}.
     *
     * <p>A {@code turn} fragment carries the domain it lands in as a fourth
     * field, and an {@code end} fragment is prefixed with {@code *} when the API
     * pins how much of the stream it walks. Nothing here is {@code findAny}: the
     * value a pipeline prints has to be the same on every run, or it cannot be
     * an oracle. Nothing here says {@code parallel()} either, because that is a
     * property of a pipeline and not of a step in it — {@code walk} appends it
     * once, at the end, where it means the same thing as it would anywhere
     * else.</p>
     *
     * <p>Four roles beyond the plain ones: a {@code peek} counts the traversal,
     * a {@code self} reads {@code this} and is drawn only in an instance frame,
     * a {@code boom} throws and is drawn only in a frame that catches, and a
     * {@code void} terminal returns nothing and leaves the peek line as the only
     * oracle.</p>
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
        "long|source|LongStream.empty()",
        "long|source|LIST.stream().mapToLong(Long::longValue)",
        "long|source|LongStream.iterate(1L, n -> n + 3L).limit(7L)",
        "long|source|LongStream.iterate(1L, n -> n < 50L, n -> n * 2L)",
        "long|source|LongStream.concat(LongStream.of(NUMBERS), LongStream.range(1L, 4L))",
        "long|stage|map(n -> n + 1L)",
        "long|stage|map(n -> n * 2L)",
        "long|stage|map(n -> n + by)",
        "long|stage|map(n -> n * cut + by)",
        "long|stage|map(n -> n + (long) frac)",
        "long|stage|filter(n -> n % 3L != 0L)",
        "long|stage|filter(n -> n != by)",
        "long|stage|distinct()",
        "long|stage|sorted()",
        "long|stage|skip(2L)",
        "long|stage|limit(9L)",
        "long|stage|takeWhile(n -> n < 40L)",
        "long|stage|dropWhile(n -> n < 3L)",
        "long|stage|flatMap(n -> LongStream.of(n, n + 1L))",
        "long|stage|mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 1L); })",
        "long|self|map(n -> this.bump(n))",
        "long|self|filter(n -> n != this.step)",
        "long|boom|map(n -> { if (n > 5L) { throw boom(n); } return n; })",
        "long|peek|peek(x -> SUM[0] += x)",
        "long|turn|boxed()|boxed",
        "long|turn|mapToObj(Long::toString)|words",
        "long|turn|mapToObj(n -> tag + n)|words",
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
        "long|void|forEach(x -> SUM[0] += x)",
        "long|void|forEachOrdered(x -> SUM[0] += x * 2L)",
        "ints|source|IntStream.of(INTS)",
        "ints|source|Arrays.stream(INTS)",
        "ints|source|IntStream.rangeClosed(1, 7)",
        "ints|source|IntStream.iterate(2, n -> n + 5).limit(5L)",
        "ints|source|PROSE.chars()",
        "ints|source|IntStream.concat(IntStream.of(INTS), IntStream.range(0, 3))",
        "ints|stage|map(n -> n + 2)",
        "ints|stage|map(n -> n * 3)",
        "ints|stage|map(n -> n + cut)",
        "ints|stage|map(n -> n * cut + (int) by)",
        "ints|stage|filter(n -> n > 2)",
        "ints|stage|filter(n -> n != cut)",
        "ints|stage|distinct()",
        "ints|stage|sorted()",
        "ints|stage|skip(1L)",
        "ints|stage|limit(8L)",
        "ints|stage|takeWhile(n -> n < 30)",
        "ints|stage|dropWhile(n -> n < 2)",
        "ints|stage|flatMap(n -> IntStream.of(n, n + 1))",
        "ints|stage|mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 2); })",
        "ints|self|map(n -> (int) this.bump(n))",
        "ints|boom|map(n -> { if (n > 5) { throw boom(n); } return n; })",
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
        "ints|void|forEach(x -> SUM[0] += x)",
        "reals|source|DoubleStream.of(DECIMALS)",
        "reals|source|Arrays.stream(DECIMALS)",
        "reals|source|DoubleStream.iterate(0.5, d -> d + 1.25).limit(6L)",
        "reals|source|DoubleStream.concat(DoubleStream.of(DECIMALS), DoubleStream.of(0.75))",
        "reals|stage|map(d -> d * 1.5)",
        "reals|stage|map(d -> d + 0.25)",
        "reals|stage|map(d -> d * frac)",
        "reals|stage|map(d -> d + by)",
        "reals|stage|filter(d -> d > 1.0)",
        "reals|stage|filter(d -> d != frac)",
        "reals|stage|distinct()",
        "reals|stage|sorted()",
        "reals|stage|skip(1L)",
        "reals|stage|limit(7L)",
        "reals|stage|takeWhile(d -> d < 12.0)",
        "reals|stage|flatMap(d -> DoubleStream.of(d, d * 0.5))",
        "reals|stage|mapMulti((d, sink) -> { sink.accept(d); sink.accept(d * 2.0); })",
        "reals|self|map(d -> d + this.step)",
        "reals|boom|map(d -> { if (d > 5.0) { throw boom(d); } return d; })",
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
        "reals|void|forEach(x -> SUM[0] += (long) x)",
        "boxed|source|LIST.stream()",
        "boxed|source|Stream.of(3L, 7L, 7L, 1L, 9L)",
        "boxed|source|Arrays.stream(NUMBERS).boxed()",
        "boxed|source|Stream.iterate(1L, n -> n + 2L).limit(6L)",
        "boxed|source|Stream.generate(() -> 7L).limit(4L)",
        "boxed|source|Stream.concat(LIST.stream(), Stream.of(42L))",
        "boxed|stage|map(n -> n + 1L)",
        "boxed|stage|map(n -> n + by)",
        "boxed|stage|map(n -> n + by + cut)",
        "boxed|stage|filter(n -> n % 2L == 0L)",
        "boxed|stage|filter(n -> n > by)",
        "boxed|stage|distinct()",
        "boxed|stage|sorted()",
        "boxed|stage|sorted(Comparator.reverseOrder())",
        "boxed|stage|skip(1L)",
        "boxed|stage|limit(6L)",
        "boxed|stage|takeWhile(n -> n < 20L)",
        "boxed|stage|dropWhile(n -> n < 4L)",
        "boxed|stage|flatMap(n -> Stream.of(n, n + 2L))",
        "boxed|stage|<Long>mapMulti((n, sink) -> { sink.accept(n); sink.accept(n * 3L); })",
        "boxed|self|map(n -> this.bump(n))",
        "boxed|self|filter(n -> n > this.step)",
        "boxed|boom|map(n -> { if (n > 5L) { throw boom(n); } return n; })",
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
        "boxed|turn|map(n -> tag + n)|words",
        "boxed|turn|map(n -> new Pair(n, Long.toString(n)))|pairs",
        "boxed|end|*collect(Collectors.toList()).size()",
        "boxed|end|*collect(Collectors.toSet()).size()",
        "boxed|end|*collect(Collectors.counting())",
        "boxed|end|*collect(Collectors.summingLong(Long::longValue))",
        "boxed|end|*toList().size()",
        "boxed|end|*toArray(Long[]::new).length",
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
        "boxed|void|forEach(x -> SUM[0] += x)",
        "ibox|source|INTEGERS.stream()",
        "ibox|source|IntStream.of(INTS).boxed()",
        "ibox|source|Stream.of(5, 2, 9, 2, 7)",
        "ibox|stage|map(n -> n + 1)",
        "ibox|stage|map(n -> n + cut)",
        "ibox|stage|filter(n -> n % 2 == 0)",
        "ibox|stage|filter(n -> n != cut)",
        "ibox|stage|distinct()",
        "ibox|stage|sorted()",
        "ibox|stage|sorted(Comparator.reverseOrder())",
        "ibox|stage|skip(1L)",
        "ibox|stage|limit(5L)",
        "ibox|stage|takeWhile(n -> n < 9)",
        "ibox|stage|dropWhile(n -> n < 3)",
        "ibox|stage|flatMap(n -> Stream.of(n, n))",
        "ibox|stage|<Integer>mapMulti((n, sink) -> { sink.accept(n); sink.accept(n + 3); })",
        "ibox|self|map(n -> (int) this.bump(n))",
        "ibox|boom|filter(n -> { if (n > 5) { throw boom(n); } return true; })",
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
        "ibox|void|forEach(x -> SUM[0] += x)",
        "words|source|WORDS.stream()",
        "words|source|Stream.of(\"alpha\", \"beta\", \"gamma\", \"beta\")",
        "words|source|Arrays.stream(PROSE.split(\" \"))",
        "words|source|Stream.<String>empty()",
        "words|source|Stream.generate(() -> \"zeta\").limit(3L)",
        "words|source|Stream.iterate(\"a\", s -> s + \"b\").limit(4L)",
        "words|source|Stream.concat(WORDS.stream(), Stream.of(\"omega\"))",
        "words|stage|map(String::toUpperCase)",
        "words|stage|map(s -> s + \"!\")",
        "words|stage|map(s -> s + tag)",
        "words|stage|map(tag::concat)",
        "words|stage|filter(s -> !s.isEmpty())",
        "words|stage|filter(s -> !s.equals(tag))",
        "words|stage|distinct()",
        "words|stage|sorted()",
        "words|stage|sorted(Comparator.comparingInt(String::length))",
        "words|stage|skip(1L)",
        "words|stage|limit(5L)",
        "words|stage|takeWhile(s -> s.length() < 8)",
        "words|stage|dropWhile(s -> s.length() < 3)",
        "words|stage|flatMap(s -> Stream.of(s, s))",
        "words|stage|<String>mapMulti((s, sink) -> { sink.accept(s); sink.accept(s); })",
        "words|self|map(this::decorate)",
        "words|self|filter(s -> !s.equals(this.mark))",
        "words|boom|map(s -> { if (s.length() > 3) { throw boom(s); } return s; })",
        "words|peek|peek(x -> SUM[0] += x.length())",
        "words|turn|mapToInt(String::length)|ints",
        "words|turn|mapToInt(s -> s.length() + cut)|ints",
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
        "words|end|*toArray(String[]::new).length",
        "words|end|*reduce(\"\", String::concat)",
        "words|end|*max(Comparator.naturalOrder()).orElse(\"none\")",
        "words|end|count()",
        "words|end|*findFirst().orElse(\"none\")",
        "words|end|*findFirst().filter(s -> !s.isEmpty()).map(String::toUpperCase).orElse(\"x\")",
        "words|end|*anyMatch(String::isEmpty)",
        "words|void|forEach(x -> SUM[0] += x.length())",
        "pairs|source|PAIRS.stream()",
        "pairs|source|Stream.of(new Pair(3L, \"a\"), new Pair(1L, \"b\"), new Pair(3L, \"a\"))",
        "pairs|stage|map(p -> new Pair(p.key() + 1L, p.text()))",
        "pairs|stage|map(p -> new Pair(p.key() + by, p.text()))",
        "pairs|stage|filter(p -> p.key() > 1L)",
        "pairs|stage|filter(p -> p.key() != by)",
        "pairs|stage|distinct()",
        "pairs|stage|sorted()",
        "pairs|stage|sorted(Comparator.comparing(Pair::text))",
        "pairs|stage|skip(1L)",
        "pairs|stage|limit(4L)",
        "pairs|stage|takeWhile(p -> p.key() < 9L)",
        "pairs|stage|dropWhile(p -> p.key() < 2L)",
        "pairs|stage|flatMap(p -> Stream.of(p, p))",
        "pairs|stage|<Pair>mapMulti((p, sink) -> { sink.accept(p); sink.accept(p); })",
        "pairs|self|map(p -> new Pair(this.bump(p.key()), p.text()))",
        "pairs|boom|map(p -> { if (p.key() > 3L) { throw boom(p); } return p; })",
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
        "pairs|void|forEach(x -> SUM[0] += x.key())",
        "dbox|source|DOUBLES.stream()",
        "dbox|source|DoubleStream.of(DECIMALS).boxed()",
        "dbox|source|Stream.of(1.5, 2.25, 1.5, 8.125)",
        "dbox|source|Stream.iterate(0.5, d -> d + 1.5).limit(5L)",
        "dbox|source|Stream.concat(DOUBLES.stream(), Stream.of(9.5))",
        "dbox|stage|map(d -> d * 1.5)",
        "dbox|stage|map(d -> d + 0.25)",
        "dbox|stage|map(d -> d * frac)",
        "dbox|stage|filter(d -> d > 1.0)",
        "dbox|stage|filter(d -> d > frac)",
        "dbox|stage|distinct()",
        "dbox|stage|sorted()",
        "dbox|stage|sorted(Comparator.reverseOrder())",
        "dbox|stage|skip(1L)",
        "dbox|stage|limit(6L)",
        "dbox|stage|takeWhile(d -> d < 12.0)",
        "dbox|stage|dropWhile(d -> d < 1.0)",
        "dbox|stage|flatMap(d -> Stream.of(d, d + 0.5))",
        "dbox|stage|<Double>mapMulti((d, sink) -> { sink.accept(d); sink.accept(d * 2.0); })",
        "dbox|self|map(d -> d + this.step)",
        "dbox|boom|map(d -> { if (d > 3.0) { throw boom(d); } return d; })",
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
        "dbox|end|count()",
        "dbox|void|forEach(x -> SUM[0] += x.longValue())"
    );

    /**
     * The template of the class, with the package, the name, the body of the
     * method that holds the pipeline, the modifiers of that method, and the
     * call {@code main} reaches it by.
     *
     * <p>The four arguments the method takes are what a capturing lambda
     * captures, one of every category the metafactory distinguishes. They are
     * arguments and not locals on purpose: javac folds a constant local into the
     * body that reads it, and a folded capture is no capture at all.</p>
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
        "    private final long step;",
        "",
        "    private final String mark;",
        "",
        "    private %2$s(final long start) {",
        "        this.step = start;",
        "        this.mark = \"m\" + start;",
        "    }",
        "",
        "    private static IllegalStateException boom(final Object item) {",
        "        return new IllegalStateException(\"boom at \" + item);",
        "    }",
        "",
        "    private long bump(final long num) {",
        "        return num + this.step;",
        "    }",
        "",
        "    private String decorate(final String text) {",
        "        return this.mark + text;",
        "    }",
        "",
        "    private %4$sString pipe(final long by, final int cut,",
        "        final double frac, final String tag) {",
        "%3$s",
        "    }",
        "",
        "    public static void main(final String... args) {",
        "        System.out.println(\"%2$s=\" + %5$s);",
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
     * The body that returns what the pipeline produced.
     */
    private static final String VALUE = String.join(
        RandomPipeline.EOL,
        "        return String.valueOf(",
        "            %s",
        "        );"
    );

    /**
     * The body of a pipeline whose terminal returns nothing, where the peek
     * line is the whole oracle.
     */
    private static final String WALK = String.join(
        RandomPipeline.EOL,
        "        %s;",
        "        return \"walked\";"
    );

    /**
     * What a guarded frame wraps a body in, so that a {@code boom} operator
     * names the element it threw on instead of killing the JVM.
     */
    private static final String GUARD = String.join(
        RandomPipeline.EOL,
        "        try {",
        "%s",
        "        } catch (final RuntimeException ex) {",
        "            return \"threw:\" + ex.getMessage();",
        "        }"
    );

    /**
     * The domains a pipeline may travel through, in the order it may start in.
     */
    private static final List<String> DOMAINS = Arrays.asList(
        "boxed", "dbox", "ibox", "ints", "long", "pairs", "reals", "words"
    );

    /**
     * The domains whose terminals reduce floating point, where the order the
     * additions happen in is the order they are printed in, and a parallel
     * pipeline would therefore stop being an oracle.
     */
    private static final List<String> FLOATS = Arrays.asList("dbox", "reals");

    /**
     * Who holds the method the pipeline sits in.
     */
    private static final List<String> HOLDERS = Arrays.asList("static", "instance");

    /**
     * Whether the body catches what the pipeline throws.
     */
    private static final List<String> GUARDS = Arrays.asList("plain", "guarded");

    /**
     * What an intermediate step may be, an operation being three times as
     * likely as a conversion to another element domain or a peek. A frame adds
     * its own roles to this list, one each, so an instance method reads
     * {@code this} and a guarded body throws about as often as it peeks.
     */
    private static final List<String> ROLES = Arrays.asList(
        "stage", "stage", "stage", "stage", "stage", "stage",
        "turn", "turn", "peek", "peek"
    );

    /**
     * The largest number of intermediate operations in one pipeline.
     */
    private static final int STAGES = 7;

    /**
     * One walk in this many goes parallel, when it is allowed to.
     */
    private static final int RACES = 5;

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
        final String[] frame = walk.get(0).split("\\|", 3);
        final StringBuilder pipe = new StringBuilder(RandomPipeline.code(walk.get(1)));
        for (final String step : walk.subList(2, walk.size())) {
            pipe.append(RandomPipeline.EOL).append('.').append(RandomPipeline.code(step));
        }
        return String.format(
            RandomPipeline.TEMPLATE,
            pkg,
            name,
            RandomPipeline.body(
                RandomPipeline.shape(
                    frame[2], walk.get(walk.size() - 1).split("\\|", 3)[1]
                ),
                pipe.toString()
            ),
            RandomPipeline.modifier(frame[1]),
            RandomPipeline.call(frame[1], name)
        );
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
     * @return The grammar lines the class is printed from, the frame first
     */
    private List<String> rolled() {
        List<String> found = new ArrayList<>(0);
        for (int attempt = 0; attempt < RandomPipeline.ATTEMPTS; ++attempt) {
            final List<String> walk = RandomPipeline.walk(
                new Random(RandomPipeline.scrambled(this.seed * 31L + attempt))
            );
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
     * The seed, mixed.
     *
     * <p>{@link Random} scrambles a seed by exclusive-or alone, which moves the
     * state of two neighbouring seeds by so little that the first few bits they
     * draw are the same bits — the first {@code nextInt(2)} of seeds 0 to 200
     * answers the same way almost every time. The walk draws the frame it sits
     * in first of all, so without a mix of its own three quarters of the seeds
     * would land in one frame and the axis would be an axis in name only. This
     * is SplitMix64's finalizer, which spreads one increment across all sixty
     * four bits.</p>
     *
     * @param seed The seed, as the caller counted it
     * @return A seed whose neighbours are nowhere near it
     */
    private static long scrambled(final long seed) {
        long mixed = seed + 0x9E37_79B9_7F4A_7C15L;
        mixed = (mixed ^ mixed >>> 30) * 0xBF58_476D_1CE4_E5B9L;
        mixed = (mixed ^ mixed >>> 27) * 0x94D0_49BB_1331_11EBL;
        return mixed ^ mixed >>> 31;
    }

    /**
     * One walk through the grammar, as the lines of it that were picked.
     *
     * <p>The first line is not a step but the frame the pipeline sits in, as
     * {@code frame|holder|guard}. The frame is picked before anything else
     * because it decides which roles the walk may draw from.</p>
     *
     * @param rnd The source of randomness
     * @return The grammar lines, a frame first, a source second, a terminal last
     */
    private static List<String> walk(final Random rnd) {
        final Map<String, List<String>> table = RandomPipeline.table();
        final String holder =
            RandomPipeline.HOLDERS.get(rnd.nextInt(RandomPipeline.HOLDERS.size()));
        final String guard =
            RandomPipeline.GUARDS.get(rnd.nextInt(RandomPipeline.GUARDS.size()));
        final List<String> roles = new ArrayList<>(RandomPipeline.ROLES);
        if ("instance".equals(holder)) {
            roles.add("self");
        }
        if ("guarded".equals(guard)) {
            roles.add("boom");
        }
        String domain = RandomPipeline.DOMAINS.get(rnd.nextInt(RandomPipeline.DOMAINS.size()));
        final List<String> steps = new ArrayList<>(0);
        steps.add(String.join("|", "frame", holder, guard));
        steps.add(
            String.join("|", domain, "source", RandomPipeline.pick(table, domain, "source", rnd))
        );
        final int total = 1 + rnd.nextInt(RandomPipeline.STAGES);
        boolean observed = false;
        for (int stage = 0; stage < total; ++stage) {
            final String role = roles.get(rnd.nextInt(roles.size()));
            final String fragment = RandomPipeline.pick(table, domain, role, rnd);
            steps.add(String.join("|", domain, role, fragment));
            observed = observed || "peek".equals(role) || "boom".equals(role);
            if ("turn".equals(role)) {
                domain = fragment.substring(fragment.lastIndexOf('|') + 1);
            }
        }
        final String end = RandomPipeline.terminal(table, domain, observed, rnd);
        if (RandomPipeline.raceable(end, observed, domain)
            && rnd.nextInt(RandomPipeline.RACES) == 0) {
            steps.add(String.join("|", domain, "race", "parallel()"));
        }
        steps.add(end);
        return steps;
    }

    /**
     * Whether this pipeline may go parallel.
     *
     * <p>Two things forbid it. A traversal that is counted, by a {@code peek}
     * or by a {@code forEach} that accumulates, is counted by several ForkJoin
     * workers at once into one unsynchronized slot, and what they lose is not
     * the same on every run. And a terminal that reduces floating point adds in
     * whatever order the splits happened to combine in. Everything else the
     * grammar can build is ordered, so a parallel run prints what a sequential
     * one prints — which is the very contract {@code 222}-{@code 225} keep by
     * refusing to fuse a {@code skip} or a {@code distinct} into a parallel
     * pipeline.</p>
     *
     * @param end The terminal line the walk picked
     * @param observed TRUE if something counts the traversal
     * @param domain The element domain the pipeline ended in
     * @return TRUE if {@code parallel()} may be appended
     */
    private static boolean raceable(final String end, final boolean observed,
        final String domain) {
        return !observed
            && !"void".equals(end.split("\\|", 3)[1])
            && !RandomPipeline.FLOATS.contains(domain);
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
     * The template of the body of the method that holds the pipeline.
     * @param guard Either {@code plain} or {@code guarded}
     * @param role The role of the terminal, either {@code end} or {@code void}
     * @return A template with one {@code %s} where the pipeline goes
     */
    private static String shape(final String guard, final String role) {
        final String body;
        if ("void".equals(role)) {
            body = RandomPipeline.WALK;
        } else {
            body = RandomPipeline.VALUE;
        }
        final String shape;
        if ("guarded".equals(guard)) {
            shape = String.format(RandomPipeline.GUARD, RandomPipeline.deeper(body));
        } else {
            shape = body;
        }
        return shape;
    }

    /**
     * The body of the method, with the pipeline in it.
     *
     * <p>The pipeline arrives with one step per line and no indentation at all,
     * and every line but the first is pushed out to where the {@code %s} of the
     * shape stands, so that the shapes carry their own layout and nothing has
     * to be told twice how deep a body is nested.</p>
     *
     * @param shape The template of the body
     * @param pipe The pipeline, one step per line
     * @return The Java statements of the body
     */
    private static String body(final String shape, final String pipe) {
        final int mark = shape.indexOf("%s");
        return String.format(
            shape,
            pipe.replace(
                RandomPipeline.EOL,
                RandomPipeline.EOL.concat(
                    shape.substring(shape.lastIndexOf('\n', mark) + 1, mark)
                )
            )
        );
    }

    /**
     * The same block, one level deeper.
     * @param block The Java statements
     * @return The very same statements, indented by four more spaces
     */
    private static String deeper(final String block) {
        return "    ".concat(
            block.replace(RandomPipeline.EOL, RandomPipeline.EOL.concat("    "))
        );
    }

    /**
     * The modifiers of the method that holds the pipeline.
     * @param holder Either {@code static} or {@code instance}
     * @return What stands in front of the return type
     */
    private static String modifier(final String holder) {
        final String modifier;
        if ("instance".equals(holder)) {
            modifier = "";
        } else {
            modifier = "static ";
        }
        return modifier;
    }

    /**
     * The expression {@code main} reaches the pipeline by.
     * @param holder Either {@code static} or {@code instance}
     * @param name The name of the class
     * @return The Java expression that runs the pipeline once
     */
    private static String call(final String holder, final String name) {
        final String call;
        if ("instance".equals(holder)) {
            call = String.format("new %s(4L).pipe(2L, 3, 1.5, \"z\")", name);
        } else {
            call = "pipe(2L, 3, 1.5, \"z\")";
        }
        return call;
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
     * @param walk The lines the walk picked, the frame first
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
     * <p>A pipeline whose traversal is counted — by a {@code peek}, or by a
     * {@code boom} that names the element it threw on — may not end in a
     * terminal the API lets skip the traversal, which is {@code count()} and
     * nothing else. Short-circuiting terminals stay allowed on purpose: how far
     * a sequential stream walks before it stops is part of what the rewrite has
     * to preserve. A terminal that returns nothing walks everything by
     * contract, so it is offered whatever else the walk did.</p>
     *
     * @param table The grammar
     * @param domain The element domain the pipeline ended in
     * @param observed TRUE if something counts the traversal
     * @param rnd The source of randomness
     * @return The terminal line, as {@code domain|role|fragment}
     */
    private static String terminal(final Map<String, List<String>> table,
        final String domain, final boolean observed, final Random rnd) {
        final List<String> ends = new ArrayList<>(0);
        for (final String end : table.get(String.join("|", domain, "end"))) {
            if (!observed || end.charAt(0) == '*') {
                ends.add(String.join("|", domain, "end", RandomPipeline.fragment(end)));
            }
        }
        for (final String end : table.get(String.join("|", domain, "void"))) {
            ends.add(String.join("|", domain, "void", end));
        }
        return ends.get(rnd.nextInt(ends.size()));
    }
}
