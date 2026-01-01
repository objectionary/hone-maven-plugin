/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class JnaTest {

    @Test
    void retrievesPid() throws Exception {
        Assertions.assertTrue(Jna.INSTANCE.getpid() > 0);
    }
}
