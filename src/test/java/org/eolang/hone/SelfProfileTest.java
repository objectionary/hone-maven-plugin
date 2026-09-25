/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the {@code self} profile in {@code pom.xml}, whose
 * execution must not hardcode a {@code <rules>} value that would shadow
 * the {@code hone.rules} command line property (see #1070).
 *
 * @since 0.24.0
 */
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "PMD.UnitTestContainsTooManyAsserts"
})
final class SelfProfileTest {

    @Test
    void doesNotHardcodeRulesInTheSelfProfileExecution() throws IOException {
        final String pom = new String(
            Files.readAllBytes(Paths.get("pom.xml")),
            StandardCharsets.UTF_8
        );
        final int start = pom.indexOf("<id>self</id>");
        final int end = pom.indexOf("</profile>", start);
        MatcherAssert.assertThat(
            "the self profile must exist",
            start, Matchers.greaterThanOrEqualTo(0)
        );
        MatcherAssert.assertThat(
            "a hardcoded <rules> in the self profile's execution shadows the hone.rules command line property",
            pom.substring(start, end),
            Matchers.not(Matchers.containsString("<rules>"))
        );
    }
}
