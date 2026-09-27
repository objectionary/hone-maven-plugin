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
 * Test case for the rule-debugging command in {@code CLAUDE.md}, which named
 * no version and therefore ran the released plugin, whose rules live inside
 * its own jar, instead of the working copy (see #1074).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RuleDebugDocTest {

    @Test
    void namesAVersionInEveryGoalItTellsToRun() throws IOException {
        MatcherAssert.assertThat(
            "the documented command must name a version, or Maven resolves the plugin to the latest release and the new rule is never loaded",
            new String(
                Files.readAllBytes(Paths.get("CLAUDE.md")),
                StandardCharsets.UTF_8
            ),
            Matchers.not(
                Matchers.matchesRegex("(?s).*org\\.eolang:hone-maven-plugin:(build|optimize).*")
            )
        );
    }
}
