/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.File;
import java.io.IOException;
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
            "a relative -Dhone.target must be absolutized for entry.sh as well",
            AbstractMojo.targetOrDefault(new File("target/custom")).isAbsolute(),
            Matchers.is(true)
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
