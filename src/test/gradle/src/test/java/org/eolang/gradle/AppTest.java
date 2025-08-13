/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.gradle;

import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link App}.
 * @since 1.0
 */
public final class AppTest {
    @Test
    void returnsFormattedTextWithTheNumber33() {
        assertThat(
            new App().txt(),
            is("int is 42\n")
        );
    }
}
