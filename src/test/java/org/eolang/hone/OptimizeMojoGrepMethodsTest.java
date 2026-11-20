/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.util.Arrays;
import java.util.Collections;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test case for the {@code grep-methods} parameter of {@link OptimizeMojo},
 * which lets the {@code grep-in} filter be written as method names instead of
 * hex bytes (see #453).
 *
 * @since 0.33.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoGrepMethodsTest {

    @Test
    void buildsPatternFromMethodNames() {
        MatcherAssert.assertThat(
            "the method names must give the same pattern as the documented hex example",
            OptimizeMojo.grepPattern(
                OptimizeMojo.DEFAULT_GREP_IN, Arrays.asList("filter", "map")
            ),
            Matchers.equalTo(">(66-69-6C-74-65-72|6D-61-70)<")
        );
    }

    @Test
    void ignoresSpacesAroundMethodNames() {
        MatcherAssert.assertThat(
            "the spaces after the commas of '-Dhone.grep-methods' must not get into the pattern",
            OptimizeMojo.grepPattern(
                OptimizeMojo.DEFAULT_GREP_IN, Arrays.asList(" filter", " map ", "")
            ),
            Matchers.equalTo(new Greppable("filter", "map").toString())
        );
    }

    @Test
    void keepsGrepInWithoutMethodNames() {
        MatcherAssert.assertThat(
            "without method names the configured grep-in must be used as is",
            OptimizeMojo.grepPattern(".*", null),
            Matchers.equalTo(".*")
        );
    }

    @Test
    void keepsGrepInWithEmptyMethodNames() {
        MatcherAssert.assertThat(
            "an empty list of method names must not replace the configured grep-in",
            OptimizeMojo.grepPattern(".*", Collections.emptyList()),
            Matchers.equalTo(".*")
        );
    }

    @Test
    void refusesMethodNamesTogetherWithCustomGrepIn() {
        MatcherAssert.assertThat(
            "a custom grep-in and method names together must be refused, not silently mixed",
            Assertions.assertThrows(
                IllegalStateException.class,
                () -> OptimizeMojo.grepPattern(".*", Collections.singletonList("map"))
            ).getMessage(),
            Matchers.containsString("hone.grep-methods")
        );
    }
}
