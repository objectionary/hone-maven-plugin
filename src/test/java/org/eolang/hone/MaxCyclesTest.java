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
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@code hone.max-cycles}, which defaults to one ordered pass
 * over the rule list, while the rules and the README said the set runs to a
 * fixpoint and leaned on coming round again (see #1111).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class MaxCyclesTest {

    @Test
    void saysWhatTheDefaultMeans() throws IOException {
        MatcherAssert.assertThat(
            "the parameter must say that its default walks the list once, since that is what phino is told",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/java/org/eolang/hone/OptimizeMojo.java")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("walks the rule list once")
        );
    }

    @Test
    void keepsTheDefaultAtOne() throws IOException {
        MatcherAssert.assertThat(
            "the document above describes a default of one, so the annotation must carry it",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/java/org/eolang/hone/OptimizeMojo.java")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("property = \"hone.max-cycles\", defaultValue = \"1\"")
        );
    }

    @Test
    void promisesNoFixpointTheDefaultDoesNotGive() throws IOException {
        final Collection<String> claims = new ArrayList<>(0);
        final Collection<Path> sources = new ArrayList<>(0);
        sources.add(Paths.get("README.md"));
        try (Stream<Path> rules = Files.walk(Paths.get("src/main/resources/org/eolang/hone"))) {
            rules.filter(Files::isRegularFile).forEach(sources::add);
        }
        for (final Path source : sources) {
            for (final String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                if (line.contains("runs to a fixpoint") || line.contains("runs to fixpoint")) {
                    claims.add(String.format("%s: %s", source, line.trim()));
                }
            }
        }
        MatcherAssert.assertThat(
            "nothing may promise a fixpoint the default single cycle does not reach",
            claims,
            Matchers.empty()
        );
    }
}
