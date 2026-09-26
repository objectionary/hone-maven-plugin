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
 * Test case for {@link Mktemp}.
 *
 * @since 0.1.0
 */
final class MktempTest {

    @Test
    void runsInDirectory() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path file = temp.path().resolve("test.txt");
            Files.write(file, "Hello, world!".getBytes(StandardCharsets.UTF_8));
            MatcherAssert.assertThat(
                "file must be written",
                file.toFile().exists(),
                Matchers.is(true)
            );
        }
    }

    @Test
    void deletesDirectoryOnClose() throws Exception {
        final Path path;
        try (Mktemp temp = new Mktemp()) {
            path = temp.path();
        }
        MatcherAssert.assertThat(
            "the directory must be gone after close, or every run leaves one behind",
            path.toFile().exists(),
            Matchers.is(false)
        );
    }

    @Test
    void deletesDirectoryWithFilesInIt() throws Exception {
        final Path path;
        try (Mktemp temp = new Mktemp()) {
            path = temp.path();
            path.resolve("a").resolve("b").toFile().mkdirs();
            Files.write(
                path.resolve("a/b/test.txt"),
                "Hello, world!".getBytes(StandardCharsets.UTF_8)
            );
        }
        MatcherAssert.assertThat(
            "a directory with files in it must be gone after close too",
            path.toFile().exists(),
            Matchers.is(false)
        );
    }

    @Test
    void runsInLargerDirectory() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            temp.path().resolve("a").resolve("b").toFile().mkdirs();
            final Path file = temp.path().resolve("a/b/test.txt");
            Files.write(file, "Hello, world!".getBytes(StandardCharsets.UTF_8));
            MatcherAssert.assertThat(
                "file must be written",
                file.toFile().exists(),
                Matchers.is(true)
            );
        }
    }
}
