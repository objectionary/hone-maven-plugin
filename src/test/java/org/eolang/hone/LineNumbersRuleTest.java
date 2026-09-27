/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@code 151-remove-line-numbers}, whose pattern is not scoped
 * to a method and therefore takes the line numbers of the whole class, the
 * methods no rule touches included, which the header used to call "fine for
 * transformed code" (see #1084).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class LineNumbersRuleTest {

    @Test
    void saysItTakesTheLineNumbersOfTheWholeClass() throws IOException {
        MatcherAssert.assertThat(
            "the header must say the whole class loses its line numbers, since that is what the pattern does",
            LineNumbersRuleTest.text(LineNumbersRuleTest.rule()),
            Matchers.containsString("including the")
        );
    }

    @Test
    void namesTheWayToLeaveItOut() throws IOException {
        MatcherAssert.assertThat(
            "a project that needs its stack traces must be told how to exclude the rule",
            LineNumbersRuleTest.text(LineNumbersRuleTest.rule()),
            Matchers.containsString("!streams/1xx/151-")
        );
    }

    @Test
    void saysTheSameInTheReadme() throws IOException {
        MatcherAssert.assertThat(
            "the README describes the pipeline, so it must not promise line numbers the rule removes",
            LineNumbersRuleTest.text(Paths.get("README.md")),
            Matchers.containsString("!streams/1xx/151-")
        );
    }

    private static Path rule() {
        return Paths.get(
            "src/main/resources/org/eolang/hone/rules/streams/1xx/151-remove-line-numbers.phr"
        );
    }

    private static String text(final Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
