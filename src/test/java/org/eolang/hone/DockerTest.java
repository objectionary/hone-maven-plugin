/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link Docker}.
 *
 * @since 0.1.0
 */
@ExtendWith(RandomImageResolver.class)
final class DockerTest {

    @Test
    @DisabledWithoutDocker
    void printsVersion() throws Exception {
        MatcherAssert.assertThat(
            "docker version must be printed",
            new Docker().exec("--version"),
            Matchers.is(Matchers.notNullValue())
        );
    }

    @Test
    void checksDockerPresence() {
        MatcherAssert.assertThat(
            "checks if docker is present",
            new Docker().available(),
            Matchers.either(Matchers.is(true)).or(Matchers.is(false))
        );
    }

    @Test
    void makesNiceImageName(@RandomImage final String image) {
        MatcherAssert.assertThat(
            "random image name is in a proper format",
            image,
            Matchers.matchesPattern("[a-z-]+:[a-z][a-zA-Z0-9]+")
        );
    }
}
