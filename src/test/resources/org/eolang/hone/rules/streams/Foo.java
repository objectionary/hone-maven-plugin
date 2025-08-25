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
        System.out.printf("%d\n", y);
    }
    
    @Override
    public Long get() {
        int w = this.func(5);
        final long r = IntStream.of(1)
            .map(this::func)
            .sum();
        return r;
    }

    private int func(int num) {
        return this.z + num;
    }
}
