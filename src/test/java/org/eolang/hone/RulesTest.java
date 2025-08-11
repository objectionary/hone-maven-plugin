/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
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
            final Rules rules = new Rules("!none,33*");
            rules.copyTo(temp.path().resolve("a/b/c"));
            MatcherAssert.assertThat(
                String.format("file must be written, because of %s", rules),
                temp.path().resolve("a/b/c/33-to-42.yml").toFile().exists(),
                Matchers.is(true)
            );
            MatcherAssert.assertThat(
                String.format("file must be absent, because of %s", rules),
                temp.path().resolve("a/b/c/none.yml").toFile().exists(),
                Matchers.is(false)
            );
        }
    }

    @Test
    void discoversRulesFromClasspath() {
        MatcherAssert.assertThat(
            "Should discover none.yml from classpath",
            new Rules("*").yamls(),
            Matchers.hasItems(
                "none.yml",
                "streams/701-lambda-to-invokedynamic.phr"
            )
        );
    }

    @Test
    void yamlsReturnsSortedList() {
        final Iterable<String> yamls = new Rules("*").yamls();
        String previous = null;
        for (String current : yamls) {
            if (previous != null) {
                MatcherAssert.assertThat(
                    String.format(
                        "yamls() is not sorted: '%s' > '%s'",
                        previous, current
                    ),
                    previous.compareTo(current) <= 0
                );
            }
            previous = current;
        }
    }

}
