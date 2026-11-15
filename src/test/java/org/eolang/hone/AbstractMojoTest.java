/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test case for {@link AbstractMojo}.
 *
 * @since 0.1.0
 */
final class AbstractMojoTest {

    @Test
    void usesLocalTargetWithoutMavenProject() {
        MatcherAssert.assertThat(
            "a goal without Maven project must still have a target directory for timings",
            AbstractMojo.targetOrDefault(null),
            Matchers.equalTo(new File("target"))
        );
    }

    @Test
    void preservesConfiguredTarget() {
        final File target = new File("build-output");
        MatcherAssert.assertThat(
            "a configured Maven target must remain the timings directory",
            AbstractMojo.targetOrDefault(target),
            Matchers.equalTo(target)
        );
    }

    @Test
    void returnsDefaultPhinoVersion() throws IOException {
        MatcherAssert.assertThat(
            "the default phino version must be returned from resource file",
            new AbstractMojoTest.FakeAbstractMojo().phino(),
            Matchers.matchesPattern("\\d+\\.\\d+\\.\\d+")
        );
    }

    @Test
    void returnsSamePhinoVersionOnMultipleCalls() throws IOException {
        final AbstractMojoTest.FakeAbstractMojo mojo = new AbstractMojoTest.FakeAbstractMojo();
        MatcherAssert.assertThat(
            "the phino version must be consistent across multiple calls",
            mojo.phino(),
            Matchers.equalTo(mojo.phino())
        );
    }

    @Test
    void reportsHostReadyWhenAllToolsExist(@TempDir final Path dir) throws IOException {
        MatcherAssert.assertThat(
            "host must be ready when phino, mvn, and parallel all succeed",
            AbstractMojo.toolsReady(
                new Phino(AbstractMojoTest.fakeTool(dir, "phino", "0.0.140", 0).toString()),
                "0.0.140",
                AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
                AbstractMojoTest.fakeTool(dir, "parallel", "GNU parallel", 0).toString()
            ),
            Matchers.is(true)
        );
    }

    @Test
    void rejectsHostWhenMavenIsMissing(@TempDir final Path dir) throws IOException {
        MatcherAssert.assertThat(
            "host must not be ready when mvn executable is absent",
            AbstractMojo.toolsReady(
                new Phino(AbstractMojoTest.fakeTool(dir, "phino", "0.0.140", 0).toString()),
                "0.0.140",
                dir.resolve("missing-mvn").toString(),
                AbstractMojoTest.fakeTool(dir, "parallel", "GNU parallel", 0).toString()
            ),
            Matchers.is(false)
        );
    }

    @Test
    void rejectsHostWhenMavenFails(@TempDir final Path dir) throws IOException {
        MatcherAssert.assertThat(
            "host must not be ready when mvn exits with non-zero status",
            AbstractMojo.toolsReady(
                new Phino(AbstractMojoTest.fakeTool(dir, "phino", "0.0.140", 0).toString()),
                "0.0.140",
                AbstractMojoTest.fakeTool(dir, "mvn", "error", 1).toString(),
                AbstractMojoTest.fakeTool(dir, "parallel", "GNU parallel", 0).toString()
            ),
            Matchers.is(false)
        );
    }

    @Test
    void rejectsHostWhenParallelIsMissingAndRequired(@TempDir final Path dir) throws IOException {
        MatcherAssert.assertThat(
            "host must not be ready when parallel is absent and threads is not 1",
            AbstractMojo.toolsReady(
                new Phino(AbstractMojoTest.fakeTool(dir, "phino", "0.0.140", 0).toString()),
                "0.0.140",
                AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
                dir.resolve("missing-parallel").toString()
            ),
            Matchers.is(false)
        );
    }

    @Test
    void allowsHostWhenParallelIsMissingButNotRequired(@TempDir final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "host must be ready without parallel when single-threaded mode omits it",
            AbstractMojo.toolsReady(
                new Phino(AbstractMojoTest.fakeTool(dir, "phino", "0.0.140", 0).toString()),
                "0.0.140",
                AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString()
            ),
            Matchers.is(true)
        );
    }

    private static Path fakeTool(
        final Path dir, final String name, final String output, final int code
    ) throws IOException {
        final boolean win = System.getProperty("os.name")
            .toLowerCase(Locale.ENGLISH)
            .startsWith("win");
        final Path script;
        if (win) {
            final String crlf = new String(
                new byte[] {13, 10},
                StandardCharsets.US_ASCII
            );
            script = dir.resolve(String.format("%s.cmd", name));
            Files.write(
                script,
                String.join(
                    crlf,
                    "@echo off",
                    String.format(Locale.ROOT, "echo %s", output),
                    String.format(Locale.ROOT, "exit /b %d", code),
                    ""
                ).getBytes(StandardCharsets.UTF_8)
            );
        } else {
            script = dir.resolve(name);
            Files.write(
                script,
                String.format(
                    Locale.ROOT,
                    "#!/bin/sh%necho '%s'%nexit %d%n",
                    output,
                    code
                ).getBytes(StandardCharsets.UTF_8)
            );
            Files.setPosixFilePermissions(
                script,
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                )
            );
        }
        return script;
    }

    /**
     * Fake implementation of AbstractMojo for testing.
     *
     * @since 0.1.0
     */
    private static final class FakeAbstractMojo extends AbstractMojo {

        @Override
        void exec() {
            // nothing
        }
    }
}
