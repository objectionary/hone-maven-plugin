/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class Foo implements Supplier<Long> {

    public static final int N = 100_000_000;

    private static final long[] VALS = IntStream.range(0, N).mapToLong(i -> i % 1000).toArray();

    private final int z = 5;

    public static void main(String[] args) {
        final int[] input = { 1, 2, 4, 8, 16 };
        final long r = IntStream.of(input)
            .map(x -> x + 1)
            .map(Foo::acceptsDouble)
            .boxed()
            .filter(Foo::filterBool)
            .filter(Foo::filterBoolean)
            .mapToDouble(x -> x)
            .filter(x -> x > 1)
            .boxed()
            .filter(Foo::filterPrim)
            .map(Foo::acceptsDouble)
            .map(String::valueOf)
            .filter(Foo::filterObject)
            .map(Integer::valueOf)
            .map(Foo::acceptsDouble)
            .filter(Foo::filterBoolean)
            .filter(Foo::filterBool)
            .map(x -> x.shortValue())
            .map(Foo::bothPrimitives)
            .map(Double::longValue)
            .filter(x -> x > 5)
            .filter(Foo::filterPrim)
            .map(Foo::returnsLong)
            .mapToLong(Foo::mapToLong)
            .sum();
        long x = LongStream.of(VALS).map(d -> d + d).sum();
        long w = new Foo().get();
        System.out.printf("%d\n", r + x + w);
    }

    private static Integer mapToLong(double x) {
        return Double.valueOf(x).intValue();
    }

    private static long returnsLong(long x) {
        return x;
    }

    private static Integer acceptsDouble(double x) {
        return Double.valueOf(x).intValue();
    }

    private static boolean filterBool(Integer x) {
        return x > 1;
    }

    private static Boolean filterBoolean(long x) {
        return x > 1;
    }

    private static double bothPrimitives(short x) {
        return (double) x;
    }

    private static boolean filterPrim(double x) {
        return true;
    }

    private static boolean filterObject(Object x) {
        return true;
    }

    @Override
    public Long get() {
        final int[] input = { 1, 2, 4, 8, 16 };
        final long r = IntStream.of(input)
            .map(x -> x + 1)
            .map(this::func)
            .boxed()
            .filter(Foo::filterBool)
            .map(x -> this.func(this.z) + x)
            .filter(this::filter)
            .mapToDouble(x -> x)
            .filter(x -> this.z + x > 2)
            .boxed()
            .mapToLong(Foo::mapToLong)
            .sum();
        return r;
    }

    public int func(int x) {
        return this.z + x;
    }

    public boolean filter(int x) {
        return x > 1;
    }
}
