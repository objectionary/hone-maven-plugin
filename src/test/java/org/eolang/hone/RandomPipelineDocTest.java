/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the random-pipeline paragraph in {@code CLAUDE.md}, which sent
 * the reader to a {@code RandomPipeline} method that commit {@code ebaa2684}
 * deleted, and explained {@code reachesEveryProductionOfTheGrammar} by the
 * re-roll that went away with it (see #1417).
 *
 * @since 0.75.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RandomPipelineDocTest {

    @Test
    void namesNoQuarantineThatWasRemoved() throws IOException {
        MatcherAssert.assertThat(
            "there is no quarantine to extend any more, so the guide must not send the reader to one, and the walk may stay wide only while every production stays reached",
            RandomPipelineDocTest.doc(),
            Matchers.allOf(
                Matchers.not(Matchers.containsString("RandomPipeline.quarantined")),
                Matchers.containsString("reachesEveryProductionOfTheGrammar"),
                Matchers.not(Matchers.containsString("re-roll"))
            )
        );
    }

    @Test
    void sendsABreakingShapeToAFixture() throws IOException {
        MatcherAssert.assertThat(
            "a shape the walk reaches that breaks the rewrite must earn its own fixture, not be skipped",
            RandomPipelineDocTest.doc(),
            Matchers.matchesRegex(
                "(?s).*\\bbreak[a-z]*\\b[^.]*\\bfixture\\b[^.]*optimize/streams/.*"
            )
        );
    }

    @Test
    void referencesOnlyTheMethodsThatExist() throws IOException {
        final String source = new String(
            Files.readAllBytes(Paths.get("src/test/java/org/eolang/hone/RandomPipeline.java")),
            StandardCharsets.UTF_8
        );
        final Collection<String> missing = new ArrayList<>(0);
        final Matcher found = Pattern.compile("RandomPipeline(?!\\.java\\b)[.#]([A-Za-z]\\w*)")
            .matcher(RandomPipelineDocTest.doc());
        while (found.find()) {
            final String declaration = String.format("\\b%s\\s*\\(", found.group(1));
            if (!Pattern.compile(declaration).matcher(source).find()) {
                missing.add(found.group());
            }
        }
        MatcherAssert.assertThat(
            "a reference to a method RandomPipeline does not declare sends an agent to code that is not there",
            missing,
            Matchers.empty()
        );
    }

    private static String doc() throws IOException {
        return new String(Files.readAllBytes(Paths.get("CLAUDE.md")), StandardCharsets.UTF_8);
    }
}
