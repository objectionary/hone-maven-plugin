/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
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
    void usesAbsoluteLocalTargetWithoutMavenProject() {
        MatcherAssert.assertThat(
            "a goal without a Maven project must absolutize the target, since entry.sh chdirs",
            AbstractMojo.targetOrDefault(null),
            Matchers.equalTo(new File("target").getAbsoluteFile())
        );
    }

    @Test
    void absolutizesConfiguredRelativeTarget() {
        MatcherAssert.assertThat(
            "the helper is total: the configured directory must survive, only its prefix changes",
            AbstractMojo.targetOrDefault(new File("target/custom")),
            Matchers.equalTo(new File("target/custom").getAbsoluteFile())
        );
    }

    @Test
    void preservesConfiguredTarget() {
        final File target = new File("build-output").getAbsoluteFile();
        MatcherAssert.assertThat(
            "a configured Maven target must remain the timings directory",
            AbstractMojo.targetOrDefault(target),
            Matchers.equalTo(target)
        );
    }

    @Test
    void appliesTargetOrDefaultOnExecute() throws IOException {
        MatcherAssert.assertThat(
            "without this line the configured target never reaches the helper, and TARGET stays relative",
            AbstractMojoTest.source("org/eolang/hone/AbstractMojo.java"),
            Matchers.containsString("this.target = AbstractMojo.targetOrDefault(this.target);")
        );
    }

    @Test
    void passesAbsoluteTargetToEntryScript() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh chdirs before resolving TARGET, so it must get an absolute path even if the helper is bypassed",
            AbstractMojoTest.source("org/eolang/hone/OptimizeMojo.java"),
            Matchers.containsString(".withEnv(\"TARGET\", this.target.getAbsolutePath())")
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
    void skipsWhenParallelIsMissingAndDockerIsUnavailable(@TempDir final Path dir)
        throws Exception {
        final AtomicBoolean executed = new AtomicBoolean(false);
        final AbstractMojoTest.FakeAbstractMojo mojo = new AbstractMojoTest.FakeAbstractMojo(
            AbstractMojoTest.fakeTool(
                dir, "phino", new AbstractMojoTest.FakeAbstractMojo().phino(), 0
            ).toString(),
            AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
            dir.resolve("missing-parallel").toString(),
            AbstractMojoTest.fakeTool(dir, "docker", "no daemon", 1).toString(),
            executed
        );
        mojo.target = dir.resolve("target").toFile();
        mojo.session = AbstractMojoTest.mavenSession();
        AbstractMojoTest.setField(mojo, AbstractMojo.class, "skipWithoutDocker", true);
        mojo.execute();
        MatcherAssert.assertThat(
            "goal must skip when Docker and parallel are missing with default threads=0",
            executed.get(),
            Matchers.is(false)
        );
    }

    @Test
    void runsOnHostWhenParallelIsMissingAndThreadsIsOne(@TempDir final Path dir)
        throws Exception {
        final AtomicBoolean executed = new AtomicBoolean(false);
        final AbstractMojoTest.FakeAbstractMojo mojo = new AbstractMojoTest.FakeAbstractMojo(
            AbstractMojoTest.fakeTool(
                dir, "phino", new AbstractMojoTest.FakeAbstractMojo().phino(), 0
            ).toString(),
            AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
            dir.resolve("missing-parallel").toString(),
            AbstractMojoTest.fakeTool(dir, "docker", "no daemon", 1).toString(),
            executed
        );
        mojo.target = dir.resolve("target").toFile();
        mojo.session = AbstractMojoTest.mavenSession();
        mojo.threads = 1;
        AbstractMojoTest.setField(mojo, AbstractMojo.class, "skipWithoutDocker", true);
        mojo.execute();
        MatcherAssert.assertThat(
            "goal must keep host execution when parallel is missing and hone.threads=1",
            executed.get(),
            Matchers.is(true)
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

    // @checkstyle ParameterNumberCheck (4 lines)
    static Path fakeTool(
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

    static Path recordingDocker(final Path dir, final Path log) throws IOException {
        final Path script = dir.resolve("docker");
        Files.write(
            script,
            String.format(
                Locale.ROOT,
                "#!/bin/sh%necho \"$*\" >> '%s'%nexit 0%n",
                log
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
        return script;
    }

    @SuppressWarnings("deprecation")
    static MavenSession mavenSession() {
        final DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setStartTime(Date.from(Instant.now()));
        return new MavenSession(
            null,
            request,
            (MavenExecutionResult) null,
            Collections.emptyList()
        );
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    // @checkstyle ParameterNumberCheck (4 lines)
    static void setField(
        final Object target, final Class<?> owner, final String name, final Object value
    ) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String source(final String name) throws IOException {
        return new String(
            Files.readAllBytes(Paths.get("src/main/java", name)), StandardCharsets.UTF_8
        );
    }

    /**
     * Fake implementation of AbstractMojo for testing.
     *
     * @since 0.1.0
     */
    private static final class FakeAbstractMojo extends AbstractMojo {

        /**
         * Flag set when exec() is called.
         */
        private final AtomicBoolean ran;

        FakeAbstractMojo() {
            this("phino", "mvn", "parallel", "docker", new AtomicBoolean(false));
        }

        /**
         * Ctor.
         *
         * @param phino Phino executable
         * @param mvn Maven executable
         * @param parallel GNU Parallel executable
         * @param docker Docker executable
         * @param flag Flag set when exec() is called
         * @checkstyle ParameterNumberCheck (6 lines)
         */
        FakeAbstractMojo(
            final String phino,
            final String mvn,
            final String parallel,
            final String docker,
            final AtomicBoolean flag
        ) {
            super(phino, mvn, parallel, docker);
            this.ran = flag;
        }

        @Override
        void exec() {
            this.ran.set(true);
        }
    }
}
