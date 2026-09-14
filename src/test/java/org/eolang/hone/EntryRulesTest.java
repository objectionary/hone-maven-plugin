/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test case for the rules summary printed by {@code entry.sh}.
 *
 * @since 0.6.1
 */
final class EntryRulesTest {

    @Test
    void countsEveryRuleOfTheList() throws IOException {
        MatcherAssert.assertThat(
            "all three rules must be counted and listed one per line",
            this.summary("a.yml b.yml c.yml"),
            Matchers.allOf(
                Matchers.containsString("Using the following 3 rules:"),
                Matchers.containsString("\n\ta.yml\n\tb.yml\n\tc.yml")
            )
        );
    }

    @Test
    void countsAnEmptyListAsNothing() throws IOException {
        MatcherAssert.assertThat(
            "an empty list must not be reported as one rule",
            this.summary(""),
            Matchers.containsString("Using the following 0 rules:")
        );
    }

    /**
     * Run the printing fragment of {@code entry.sh} with the given rules.
     * @param rules The list of rules, separated by spaces
     * @return What the fragment prints
     * @throws IOException If fails to read the script
     */
    private String summary(final String rules) throws IOException {
        final Path script = Paths.get(
            "src/main/resources/org/eolang/hone/scaffolding/entry.sh"
        );
        final Matcher matcher = Pattern.compile(
            "printf 'Using the following.*?\\n\\n",
            Pattern.DOTALL
        ).matcher(new String(Files.readAllBytes(script), StandardCharsets.UTF_8));
        Assertions.assertTrue(matcher.find(), "No rules summary found in entry.sh");
        return new Jaxec("bash", "-c", matcher.group())
            .withEnv("RULES", rules)
            .exec()
            .stdout();
    }
}
