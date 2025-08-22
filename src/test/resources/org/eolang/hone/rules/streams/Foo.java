/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Foo implements Supplier<Long> {

    public static final int N = 100_000_000;

    private int z = 5;

    public static void main(String[] args) {
        long y = new Foo().get();
        final long w = IntStream.of(1)
            .map(x -> x + 1)
            .map(x -> x + 2)
            .sum();
        System.out.printf("%d\n", y + w);
    }

    @Override
    public Long get() {
        final long r = IntStream.of(1)
            .map(x -> this.z)
            .map(x -> this.z)
            .sum();
        return r;
    }

    public static void func(IntStream xs) {
        xs.map(x -> x).sum();
    }
}
