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
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for rows written by the skipped paths in {@code rewrite.sh}.
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptStatisticsTest {

    @Test
    void writesFiveColumnsForExcludedAndFreshFiles(@Mktmp final Path home) throws IOException {
        final Path source = Files.createDirectories(home.resolve("source"));
        Files.createDirectories(home.resolve("target"));
        Files.write(
            source.resolve("Foo.xmir"), "<xmir/>".getBytes(StandardCharsets.UTF_8)
        );
        final Path phino = home.resolve("phino");
        Files.write(
            phino,
            String.format("#!/usr/bin/env bash%n.\nprintf '0.0.1\\n'%n")
                .getBytes(StandardCharsets.UTF_8)
        );
        if (!phino.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake phino executable");
        }
        final String script = Paths.get(
            "src/main/resources/org/eolang/hone/scaffolding/rewrite.sh"
        ).toAbsolutePath().toString();
        for (int run = 0; run < 2; ++run) {
            new Jaxec("bash", script)
                .withEnv("PATH", String.format("%s:%s", home, System.getenv("PATH")))
                .withEnv("TARGET", home.resolve("target").toString())
                .withEnv("HONE_XMIR_IN", source.toString())
                .withEnv("HONE_FROM", home.resolve("phi").toString())
                .withEnv("HONE_TO", home.resolve("optimized").toString())
                .withEnv("HONE_XMIR_OUT", home.resolve("out").toString())
                .withEnv("HONE_GREP_IN", "NO_MATCH")
                .withEnv("HONE_RULES", "rules/test.phr")
                .withEnv("HONE_SMALL_STEPS", "false")
                .withEnv("HONE_MAX_CYCLES", "1")
                .withEnv("HONE_MAX_DEPTH", "1")
                .withEnv("HONE_THREADS", "1")
                .withEnv("HONE_TIMEOUT", "5")
                .withEnv("HONE_KILL_GRACE", "1")
                .withEnv("HONE_STATISTICS", "true")
                .withEnv("HONE_VERBOSE", "false")
                .withEnv("HONE_DEBUG", "false")
                .withEnv("HONE_VERSION", "0.1.0")
                .exec();
            RewriteScriptStatisticsTest.assertFiveColumns(home);
        }
    }

    private static void assertFiveColumns(final Path home) throws IOException {
        final List<String> rows = Files.readAllLines(
            home.resolve("target/hone-statistics.csv"), StandardCharsets.UTF_8
        );
        MatcherAssert.assertThat(
            "the statistics report must contain the header and one processed file",
            rows,
            Matchers.hasSize(2)
        );
        MatcherAssert.assertThat(
            "a skipped file row must have five CSV columns",
            rows.get(1).split(",", -1),
            Matchers.arrayWithSize(5)
        );
    }
}
