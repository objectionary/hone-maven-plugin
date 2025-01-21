/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024-2025 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
    void makesNiceImageName(@RandomImage final String image) {
        MatcherAssert.assertThat(
            "random image name is in a proper format",
            image,
            Matchers.matchesPattern("[a-z-]+:[a-z][a-zA-Z0-9]+")
        );
    }
}
