/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test case for {@link Phino}.
 * @since 0.17.0
 */
final class PhinoTest {

    @Test
    void returnsTrueWhenVersionMatches(@TempDir final Path dir) throws Exception {
        MatcherAssert.assertThat(
            "must be available when the reported version matches",
            new Phino(
                PhinoTest.fake(dir, String.format("echo '1.2.3'%nexit 0")).toString()
            ).available("1.2.3"),
            Matchers.is(true)
        );
    }

    @Test
    void returnsFalseWhenVersionMismatches(@TempDir final Path dir) throws Exception {
        MatcherAssert.assertThat(
            "must not be available when the reported version doesn't match",
            new Phino(
                PhinoTest.fake(dir, String.format("echo '1.2.3'%nexit 0")).toString()
            ).available("9.9.9"),
            Matchers.is(false)
        );
    }

    @Test
    void returnsFalseWhenExecutableExitsNonZero(@TempDir final Path dir) throws Exception {
        MatcherAssert.assertThat(
            "must not be available when the executable fails",
            new Phino(PhinoTest.fake(dir, "exit 1").toString()).available("1.2.3"),
            Matchers.is(false)
        );
    }

    @Test
    void returnsFalseWhenExecutableIsMissing(@TempDir final Path dir) {
        MatcherAssert.assertThat(
            "must not be available when the executable doesn't exist",
            new Phino(dir.resolve("no-such-executable").toString()).available("1.2.3"),
            Matchers.is(false)
        );
    }

    /**
     * Create a fake executable script in the given directory.
     * @param dir The directory to create the script in
     * @param body The body of the script, after the shebang line
     * @return The path to the script
     * @throws Exception If something goes wrong
     */
    private static Path fake(final Path dir, final String body) throws Exception {
        final Path script = dir.resolve("phino");
        Files.write(
            script,
            String.format("#!/bin/sh%n%s%n", body).getBytes(StandardCharsets.UTF_8)
        );
        Files.setPosixFilePermissions(
            script,
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            )
        );
        return script;
    }
}
