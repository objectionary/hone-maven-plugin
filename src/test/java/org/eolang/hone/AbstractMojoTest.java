/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

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

        @Override
        void exec() {
            // nothing
        }
    }
}
