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

import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link Timings}.
 *
 * @since 0.1.0
 */
final class TimingsTest {

    @Test
    void savesTime() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path file = temp.path().resolve("foo.csv");
            final Timings timings = new Timings(file);
            timings.through("foo", () -> { });
            MatcherAssert.assertThat(
                "file must be written",
                file.toFile().exists(),
                Matchers.is(true)
            );
            timings.through("bar", () -> { });
            MatcherAssert.assertThat(
                "file must have two lines",
                new String(Files.readAllBytes(file)),
                Matchers.allOf(
                    Matchers.containsString("foo,"),
                    Matchers.containsString("\nbar,")
                )
            );
        }
    }
}
