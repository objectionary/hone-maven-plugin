/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link Greppable}.
 * @since 0.27.2
 */
final class GreppableTest {

    @Test
    void convertsSingleMethodToAnchoredHexPattern() {
        MatcherAssert.assertThat(
            "the 'map' method must become its anchored hex-byte form",
            new Greppable("map").toString(),
            Matchers.equalTo(">(6D-61-70)<")
        );
    }

    @Test
    void joinsSeveralMethodsAsAlternatives() {
        MatcherAssert.assertThat(
            "all method names must be joined into one anchored alternation",
            new Greppable("filter", "map", "mapMulti").toString(),
            Matchers.equalTo(">(66-69-6C-74-65-72|6D-61-70|6D-61-70-4D-75-6C-74-69)<")
        );
    }
}
