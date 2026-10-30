/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.lang.reflect.Field;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the numeric parameters of {@link OptimizeMojo}, which used to
 * reach {@code rewrite.sh} unchecked and fail far away from the configuration
 * that caused it (see #1351).
 *
 * @since 0.74.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "PMD.AvoidAccessibilityAlteration"
})
final class OptimizeMojoNumericValidationTest {

    @Test
    void rejectsTimeoutBelowFive(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "the message must name the property and its minimum",
            OptimizeMojoNumericValidationTest.rejects(home, "timeout", 4).getMessage(),
            Matchers.allOf(
                Matchers.containsString("hone.timeout"),
                Matchers.containsString("5")
            )
        );
    }

    @Test
    void rejectsNonPositiveMaxDepth(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "the message must name the property and its minimum",
            OptimizeMojoNumericValidationTest.rejects(home, "maxDepth", 0).getMessage(),
            Matchers.allOf(
                Matchers.containsString("hone.max-depth"),
                Matchers.containsString("1")
            )
        );
    }

    @Test
    void rejectsNonPositiveMaxCycles(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "the message must name the property and its minimum",
            OptimizeMojoNumericValidationTest.rejects(home, "maxCycles", 0).getMessage(),
            Matchers.allOf(
                Matchers.containsString("hone.max-cycles"),
                Matchers.containsString("1")
            )
        );
    }

    @Test
    void rejectsNegativeKillGrace(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "the message must name the property and its minimum",
            OptimizeMojoNumericValidationTest.rejects(home, "killgrace", -1).getMessage(),
            Matchers.allOf(
                Matchers.containsString("hone.kill-grace"),
                Matchers.containsString("0")
            )
        );
    }

    @Test
    void rejectsNegativeThreads(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "the message must name the property and its minimum",
            OptimizeMojoNumericValidationTest.rejects(home, "threads", -1).getMessage(),
            Matchers.allOf(
                Matchers.containsString("hone.threads"),
                Matchers.containsString("0")
            )
        );
    }

    @Test
    void acceptsBoundaryValues(@Mktmp final Path home) throws Exception {
        final OptimizeMojo mojo = OptimizeMojoNumericValidationTest.mojo(home);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "timeout", 5);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "maxDepth", 1);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "maxCycles", 1);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "killgrace", 0);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "threads", 0);
        Assertions.assertDoesNotThrow(
            mojo::exec,
            "values exactly on the boundary must be accepted"
        );
    }

    private static IllegalStateException rejects(
        final Path home, final String param, final int value
    ) throws Exception {
        final OptimizeMojo mojo = OptimizeMojoNumericValidationTest.mojo(home);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, param, value);
        final IllegalStateException error = Assertions.assertThrows(
            IllegalStateException.class, mojo::exec
        );
        MatcherAssert.assertThat(
            "nothing may be created before the configuration is rejected",
            home.resolve("target").toFile().exists()
                || home.resolve("target").resolve("classes").toFile().exists(),
            Matchers.is(false)
        );
        return error;
    }

    private static OptimizeMojo mojo(final Path home) throws Exception {
        final OptimizeMojo mojo = new OptimizeMojo();
        OptimizeMojoNumericValidationTest.set(
            mojo, AbstractMojo.class, "target", home.resolve("target").toFile()
        );
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "classes", "classes");
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "skipIfNoClasses", true);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "timeout", 999);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "maxDepth", 500);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "maxCycles", 1);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "killgrace", 10);
        OptimizeMojoNumericValidationTest.set(mojo, OptimizeMojo.class, "threads", 0);
        return mojo;
    }

    private static void set(
        final Object target, final Class<?> owner, final String name, final Object value
    ) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
