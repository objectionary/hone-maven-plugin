/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
    void dropsTheRowsOfAPreviousBuild() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path file = temp.path().resolve("bar.csv");
            Files.write(file, "\"old\";1\n".getBytes(StandardCharsets.UTF_8));
            Files.setLastModifiedTime(file, FileTime.fromMillis(0L));
            new Timings(file).through("foo", () -> { });
            MatcherAssert.assertThat(
                "the rows of a build that ran before this one must go, but they stayed",
                new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                Matchers.not(Matchers.containsString("old"))
            );
        }
    }

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
                    Matchers.containsString("\"Mojo\";\"Time\""),
                    Matchers.containsString("\"foo\";"),
                    Matchers.containsString(String.format("%n\"bar\";"))
                )
            );
        }
    }
}
