/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class ShelfTest {

    @Test
    void countsWeight() {
        Assertions.assertEquals(12, Shelf.weight(1, 2, 3));
    }
}
