/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class 𝜑Test {

    @Test
    void printsName() throws Exception {
        Assertions.assertEquals("Иван", new 𝜑("Иван").اسم());
    }
}
