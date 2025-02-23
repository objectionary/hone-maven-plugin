/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

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
            Files.write(file, "Hello, world!".getBytes());
            MatcherAssert.assertThat(
                "file must be written",
                file.toFile().exists(),
                Matchers.is(true)
            );
        }
    }

    @Test
    void runsInLargerDirectory() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            temp.path().resolve("a").resolve("b").toFile().mkdirs();
            final Path file = temp.path().resolve("a/b/test.txt");
            Files.write(file, "Hello, world!".getBytes());
            MatcherAssert.assertThat(
                "file must be written",
                file.toFile().exists(),
                Matchers.is(true)
            );
        }
    }
}
