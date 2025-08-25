/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.file.Path;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;

/**
 * Test case for {@link Rules}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class RulesTest {

    @Test
    void filtersAndSaves(@Mktmp final Path temp) throws Exception {
        final Rules rules = new Rules("n*,aaa*,*{,!f*");
        rules.copyTo(temp.resolve("a/b/c"));
        MatcherAssert.assertThat(
            String.format("file must be written, because of %s", rules),
            temp.resolve("a/b/c/none.yml").toFile().exists(),
            Matchers.is(true)
        );
    }

    @Test
    void skipsSome(@Mktmp final Path temp) throws Exception {
        final Rules rules = new Rules("!none,33*");
        rules.copyTo(temp.resolve("a/b/c"));
        MatcherAssert.assertThat(
            String.format("file must be written, because of %s", rules),
            temp.resolve("a/b/c/33-to-42.yml").toFile().exists(),
            Matchers.is(true)
        );
        MatcherAssert.assertThat(
            String.format("file must be absent, because of %s", rules),
            temp.resolve("a/b/c/none.yml").toFile().exists(),
            Matchers.is(false)
        );
    }

    @Test
    void discoversOneRuleFromClasspathWithoutSuffix() {
        MatcherAssert.assertThat(
            "Should discover none from classpath (without YML suffix)",
            new Rules("none").yamls(),
            Matchers.iterableWithSize(1)
        );
    }

    @Test
    void discoversNothingWithSuffix() {
        MatcherAssert.assertThat(
            "Should NOT discover none.yml from classpath",
            new Rules("none.yml").yamls(),
            Matchers.emptyIterable()
        );
    }

    @Test
    void discoversRulesFromClasspath() {
        MatcherAssert.assertThat(
            "Should discover none.yml from classpath",
            new Rules("*").yamls(),
            Matchers.hasItems(
                "none.yml",
                "streams/701-static-lambda-to-invokedynamic.phr"
            )
        );
    }

    @Test
    void copiesAllRulesFromClasspath(@Mktmp final Path temp) throws IOException {
        new Rules("*").copyTo(temp.resolve("copies"));
        MatcherAssert.assertThat(
            "Should copy .phr rules too",
            temp.resolve(
                "copies/streams/701-static-lambda-to-invokedynamic.phr"
            ).toFile().exists(),
            Matchers.is(true)
        );
    }

    @Test
    void yamlsReturnsSortedList() {
        final Iterable<String> yamls = new Rules("*").yamls();
        String previous = null;
        for (final String yaml : yamls) {
            if (previous != null) {
                MatcherAssert.assertThat(
                    String.format(
                        "yamls() is not sorted: '%s' > '%s'",
                        previous, yaml
                    ),
                    previous.compareTo(yaml),
                    Matchers.lessThanOrEqualTo(0)
                );
            }
            previous = yaml;
        }
    }

}
