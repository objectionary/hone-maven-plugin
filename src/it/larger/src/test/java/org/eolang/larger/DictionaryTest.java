/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class DictionaryTest {

    @Test
    void savesAndRetrieves() throws Exception {
        Dictionary<String, Long> d = new Dictionary<>();
        d.put("привет", 42L);
        Assertions.assertEquals(42L, d.get("привет"));
    }
}
