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
import java.util.concurrent.TimeUnit;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test case for {@link Phino}.
 *
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

    @Test
    void stopsTheProbeWhenInterrupted(@TempDir final Path dir) throws Exception {
        final Path pid = dir.resolve("pid");
        final Phino phino = new Phino(
            PhinoTest.fake(dir, String.format("echo $$ > %s%nexec sleep 30", pid)).toString()
        );
        final Thread probe = new Thread(() -> phino.available("1.2.3"));
        probe.start();
        while (!Files.exists(pid) || Files.size(pid) == 0L) {
            Thread.sleep(10L);
        }
        probe.interrupt();
        probe.join();
        final ProcessHandle proc = ProcessHandle.of(
            Long.parseLong(new String(Files.readAllBytes(pid), StandardCharsets.UTF_8).trim())
        ).orElseThrow(() -> new IllegalStateException("The probe is gone too early"));
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (proc.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        MatcherAssert.assertThat(
            "an interrupted probe must not leave phino running (see #1243)",
            proc.isAlive(),
            Matchers.is(false)
        );
    }

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
