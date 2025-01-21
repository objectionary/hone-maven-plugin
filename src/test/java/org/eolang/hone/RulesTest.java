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

/**
 * Test case for {@link Rules}.
 *
 * @since 0.1.0
 */
final class RulesTest {

    @Test
    void filtersAndSaves() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Rules rules = new Rules("n*,aaa*,*{,!f*");
            rules.copyTo(temp.path().resolve("a/b/c"));
            MatcherAssert.assertThat(
                String.format("file must be written, because of %s", rules),
                temp.path().resolve("a/b/c/none.yml").toFile().exists(),
                Matchers.is(true)
            );
        }
    }

    @Test
    void skipsSome() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Rules rules = new Rules("!none,t*");
            rules.copyTo(temp.path().resolve("a/b/c"));
            MatcherAssert.assertThat(
                String.format("file must be written, because of %s", rules),
                temp.path().resolve("a/b/c/thirty-three.yml").toFile().exists(),
                Matchers.is(true)
            );
            MatcherAssert.assertThat(
                String.format("file must be absent, because of %s", rules),
                temp.path().resolve("a/b/c/none.yml").toFile().exists(),
                Matchers.is(false)
            );
        }
    }
}
