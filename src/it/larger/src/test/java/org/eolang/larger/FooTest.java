/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import org.cactoos.io.ResourceOf;
import org.cactoos.scalar.LengthOf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class FooTest {

    @Test
    void loadsBinaryClass() throws Exception {
        long len = new LengthOf(new ResourceOf("org/eolang/larger/Foo.class")).value();
        Assertions.assertTrue(len > 0);
    }

}
