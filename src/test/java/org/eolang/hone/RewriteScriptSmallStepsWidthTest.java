/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the small-steps suffix width in {@code rewrite.sh}, which
 * must run it for real, not just grep the script: with the width frozen at
 * 2 (the #1078 bug again) a rule set past 99 sorts {@code .100} between
 * {@code .10} and {@code .11} (see #1201).
 *
 * @since 0.24.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptSmallStepsWidthTest {

    @Test
    void sortsPastNinetyNineRulesInOrder(@Mktmp final Path home) throws IOException {
        final Path phino = home.resolve("phino");
        Files.write(
            phino, String.format("#!/usr/bin/env bash%nprintf '0.0.1\\n'%n")
                .getBytes(StandardCharsets.UTF_8)
        );
        if (!phino.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake phino executable");
        }
        final StringBuilder rules = new StringBuilder();
        for (int r = 1; r <= 152; ++r) {
            rules.append(String.format("rules/ssw%03d.phr ", r));
        }
        final Path source = Files.createDirectories(home.resolve("source"));
        Files.write(
            source.resolve("Foo.xmir"), "<xmir/>".getBytes(StandardCharsets.UTF_8)
        );
        new Jaxec(
            "bash",
            Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                .toAbsolutePath().toString()
        )
            .withEnv("PATH", String.format("%s:%s", home, System.getenv("PATH")))
            .withEnv("TARGET", home.resolve("target").toString())
            .withEnv("HONE_XMIR_IN", source.toString())
            .withEnv("HONE_FROM", home.resolve("phi").toString())
            .withEnv("HONE_TO", home.resolve("optimized").toString())
            .withEnv("HONE_XMIR_OUT", home.resolve("out").toString())
            .withEnv("HONE_RULES", rules.toString().trim())
            .withEnv("HONE_SMALL_STEPS", "true")
            .withEnv("HONE_MAX_CYCLES", "1")
            .withEnv("HONE_MAX_DEPTH", "1")
            .withEnv("HONE_THREADS", "1")
            .withEnv("HONE_TIMEOUT", "5")
            .withEnv("HONE_KILL_GRACE", "1")
            .withEnv("HONE_STATISTICS", "false")
            .withEnv("HONE_VERBOSE", "false")
            .withEnv("HONE_DEBUG", "false")
            .withEnv("HONE_VERSION", "0.1.0")
            .exec();
        final StringBuilder expected = new StringBuilder();
        for (int r = 1; r <= 152; ++r) {
            if (expected.length() > 0) {
                expected.append(' ');
            }
            expected.append(String.format("%03d", r));
        }
        MatcherAssert.assertThat(
            "rewrite.sh must size the small-steps suffix width to the rule count itself: "
                + "with 152 rules the intermediates it creates must sort 001..152, "
                + "so .007 stays before .099 and .100 right after it (see #1078, #1201)",
            RewriteScriptSmallStepsWidthTest.suffixes(home.resolve("optimized")),
            Matchers.equalTo(expected.toString())
        );
    }

    private static String suffixes(final Path optimized) throws IOException {
        final List<String> names = new ArrayList<>();
        try (
            java.util.stream.Stream<Path> files = Files.list(optimized)
        ) {
            files.map(
                p -> p.getFileName().toString()
            ).filter(
                n -> n.matches(".*\\.phi\\.\\d{3}")
            ).map(
                n -> n.substring(n.length() - 3)
            ).forEach(names::add);
        }
        java.util.Collections.sort(names);
        return String.join(" ", names);
    }
}
