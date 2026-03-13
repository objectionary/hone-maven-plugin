/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

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
    void returnsDefaultPhinoVersion() throws IOException {
        MatcherAssert.assertThat(
            "the default phino version must be returned from resource file",
            new FakeAbstractMojo().phino(),
            Matchers.matchesPattern("\\d+\\.\\d+\\.\\d+\\.\\d+")
        );
    }

    @Test
    void returnsSamePhinoVersionOnMultipleCalls() throws IOException {
        final FakeAbstractMojo mojo = new FakeAbstractMojo();
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
