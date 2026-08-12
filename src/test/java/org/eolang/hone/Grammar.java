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
 * The grammar of the Stream API that {@link RandomPipeline} walks.
 *
 * <p>One production per line, as {@code domain|role|fragment}. A {@code turn}
 * fragment carries the domain it lands in as a fourth field, and an {@code end}
 * fragment is prefixed with {@code *} when the API pins how much of the stream
 * it walks. Nothing here is {@code findAny}: the value a pipeline prints has to
 * be the same on every run, or it cannot be an oracle. Nothing here says
 * {@code parallel()} either, because that is a property of a pipeline and not of
 * a step in it — the walk appends it once, at the end, where it means the same
 * thing as it would anywhere else.</p>
 *
 * <p>Four roles beyond the plain ones: a {@code peek} counts the traversal, a
 * {@code self} reads {@code this} and is drawn only in an instance frame, a
 * {@code boom} throws and is drawn only in a frame that catches, and a
 * {@code void} terminal returns nothing and leaves the peek line as the only
 * oracle.</p>
 *
 * <p>The productions reach the three primitive domains, all three of their boxed
 * counterparts — {@code Stream<Long>}, {@code Stream<Integer>} and
 * {@code Stream<Double>} — and two reference domains, one of strings and one of
 * a small value class of its own. They reach the corners of the terminal API as
 * well: collectors assembled by hand out of {@code Collector.of},
 * {@code Collectors.teeing}, {@code Collectors.flatMapping}, and
 * {@code Optional} chains longer than a single {@code orElse}.</p>
 *
 * <p>A production wider than one line is joined from pieces, which is what the
 * few {@code String.join} entries are. The terminals that tee, and the ones that
 * assemble a {@code Collector} by hand, spell their type arguments out: a
 * pipeline is printed through {@code String.valueOf}, and its overloads bound
 * the result of such a terminal by {@code char[]} as well as by {@code Object},
 * which no inferred type can satisfy.</p>
 *
 * @since 0.30.0
 */
final class Grammar {

    /**
     * The productions, one per line.
     */
    private static final List<String> LINES = Arrays.asList(
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
     * The domains a pipeline may travel through, in the order it may start in.
     */
    private static final List<String> DOMAINS = Arrays.asList(
        "boxed", "dbox", "ibox", "ints", "long", "pairs", "reals", "words"
    );

    /**
     * The productions, indexed by {@code domain|role}.
     */
    private final Map<String, List<String>> productions;

    /**
     * Ctor.
     */
    Grammar() {
        this(Grammar.indexed());
    }

    /**
     * Ctor.
     * @param table The productions, indexed by domain and role
     */
    private Grammar(final Map<String, List<String>> table) {
        this.productions = table;
    }

    /**
     * One domain to start a pipeline in.
     * @param rnd The source of randomness
     * @return The domain, as the grammar names it
     */
    static String domain(final Random rnd) {
        return Grammar.DOMAINS.get(rnd.nextInt(Grammar.DOMAINS.size()));
    }

    /**
     * Every fragment the grammar can reach, whatever domain it belongs to.
     * @return The fragments, as they are written in the grammar
     */
    List<String> fragments() {
        final List<String> all = new ArrayList<>(0);
        for (final List<String> allowed : this.productions.values()) {
            all.addAll(allowed);
        }
        return all;
    }

    /**
     * One fragment of the given domain and role.
     * @param domain The element domain the pipeline is in
     * @param role What the fragment must be: a source, a stage, a turn, a peek
     * @param rnd The source of randomness
     * @return The fragment, verbatim from the grammar
     */
    String pick(final String domain, final String role, final Random rnd) {
        final List<String> allowed = this.productions.get(String.join("|", domain, role));
        return allowed.get(rnd.nextInt(allowed.size()));
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
     * @param domain The element domain the pipeline ended in
     * @param observed TRUE if something counts the traversal
     * @param rnd The source of randomness
     * @return The terminal line, as {@code domain|role|fragment}
     */
    String terminal(final String domain, final boolean observed, final Random rnd) {
        final List<String> ends = new ArrayList<>(0);
        for (final String end : this.productions.get(String.join("|", domain, "end"))) {
            if (!observed || end.charAt(0) == '*') {
                ends.add(String.join("|", domain, "end", Grammar.fragment(end)));
            }
        }
        for (final String end : this.productions.get(String.join("|", domain, "void"))) {
            ends.add(String.join("|", domain, "void", end));
        }
        return ends.get(rnd.nextInt(ends.size()));
    }

    /**
     * The Java code of one line of a walk.
     * @param line The line, as {@code domain|role|fragment}
     * @return What the pipeline appends for it
     */
    static String code(final String line) {
        return Grammar.fragment(line.split("\\|", 3)[2]);
    }

    /**
     * The Java code of a fragment, without the marks the grammar wraps it in.
     * @param production The fragment, as it is written in the grammar
     * @return What the walk appends to a pipeline when it picks this fragment
     */
    static String fragment(final String production) {
        String code = production;
        final int lands = code.lastIndexOf('|');
        if (lands >= 0 && Grammar.DOMAINS.contains(code.substring(lands + 1))) {
            code = code.substring(0, lands);
        }
        if (code.charAt(0) == '*') {
            code = code.substring(1);
        }
        return code;
    }

    /**
     * The productions, indexed by domain and role.
     * @return Map from {@code domain|role} to the fragments it allows
     */
    private static Map<String, List<String>> indexed() {
        final Map<String, List<String>> table = new HashMap<>();
        for (final String line : Grammar.LINES) {
            final String[] parts = line.split("\\|", 3);
            table.computeIfAbsent(
                String.join("|", parts[0], parts[1]), key -> new ArrayList<>(0)
            ).add(parts[2]);
        }
        return table;
    }
}
