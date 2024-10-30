/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Objectionary.com
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

import java.util.Arrays;
import org.cactoos.iterable.Mapped;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matchers;

/**
 * This matcher is used by Hamcrest to verify Maven logs for
 * the presence of mistakes in them.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "JTCOP.RuleAllTestsHaveProductionClass",
    "JTCOP.RuleCorrectTestName",
    "JTCOP.RuleInheritanceInTests"
})
final class LogMatcher extends BaseMatcher<String> {

    /**
     * The log we've seen by the {@link #matches(Object)}.
     */
    private String seen;

    /**
     * Extra strings to match.
     */
    private final String[] extras;

    /**
     * Ctor.
     * @param ext Extra lines to match/find
     */
    LogMatcher(final String... ext) {
        super();
        this.extras = ext.clone();
    }

    @Override
    public boolean matches(final Object log) {
        this.seen = log.toString();
        return Matchers.allOf(
            Matchers.containsString("BUILD SUCCESS"),
            Matchers.not(Matchers.containsString("BUILD FAILURE")),
            Matchers.allOf(
                new Mapped<>(
                    Matchers::containsString,
                    Arrays.asList(this.extras)
                )
            )
        ).matches(log);
    }

    @Override
    public void describeTo(final Description desc) {
        desc.appendValue(this.seen);
    }
}
