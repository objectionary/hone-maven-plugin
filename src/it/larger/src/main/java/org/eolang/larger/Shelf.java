/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import java.util.stream.IntStream;

class Shelf {
    static int weight(final int... books) {
        // an identity map keeps the two map stages the fusion rules fold into one
        return IntStream.of(books)
            .map(book -> book * 1)
            .map(book -> book * 2)
            .sum();
    }
}
