/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link Timings}.
 *
 * @since 0.1.0
 */
final class TimingsTest {

    @Test
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void savesTime() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path file = temp.path().resolve("foo.csv");
            final Timings timings = new Timings(file);
            timings.through("foo", () -> { });
            MatcherAssert.assertThat(
                "file must be written",
                file.toFile().exists(),
                Matchers.is(true)
            );
            timings.through("bar", () -> { });
            MatcherAssert.assertThat(
                "file must have two lines",
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                Matchers.allOf(
                    Matchers.containsString("foo,"),
                    Matchers.containsString("\nbar,")
                )
            );
        }
    }
}
