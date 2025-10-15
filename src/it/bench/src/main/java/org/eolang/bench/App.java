/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.bench;

import java.util.stream.IntStream;

public class App {
    public static void main() {
        int[] v = new int[] {1, 2, 3};
        int r = IntStream.of(v)
            .map(d -> d * 1)
            .map(d -> d * 2)
            .sum();
        if (r != 12) {
            throw new RuntimeException("Expected 12");
        }
    }
}
