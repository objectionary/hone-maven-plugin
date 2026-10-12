/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
            Files.write(
                file,
                String.format("\"old\";1%n").getBytes(StandardCharsets.UTF_8)
            );
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
    void forgetsRowsOfAnEarlierBuildInTheSameJvm() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path file = temp.path().resolve("baz.csv");
            Files.write(
                file,
                String.format("\"old\";1%n").getBytes(StandardCharsets.UTF_8)
            );
            new Timings(file, System.currentTimeMillis() + 60_000L).through("foo", () -> { });
            MatcherAssert.assertThat(
                "the rows of an earlier build in the same JVM must go, as under mvnd (see #1198)",
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

    @Test
    void keepsTheFailureOfTheActionWhenTheTimingFileCannotBeWritten() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path blocker = temp.path().resolve("blocker");
            Files.write(blocker, "not a directory".getBytes(StandardCharsets.UTF_8));
            final IOException thrown = Assertions.assertThrows(
                IOException.class,
                () -> new Timings(blocker.resolve("timings.csv")).through(
                    "foo",
                    () -> {
                        throw new IOException("action failed");
                    }
                ),
                "The action must fail"
            );
            MatcherAssert.assertThat(
                "The failure of the action must stay the primary one",
                thrown.getMessage(),
                Matchers.equalTo("action failed")
            );
            MatcherAssert.assertThat(
                "The failure of the timing file must be attached to it",
                thrown.getSuppressed().length,
                Matchers.greaterThan(0)
            );
        }
    }
}
