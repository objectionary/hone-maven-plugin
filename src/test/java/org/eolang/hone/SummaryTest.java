/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.nio.file.Files;
import java.nio.file.Path;
import org.cactoos.bytes.BytesOf;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.TextOf;
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
final class SummaryTest {

    @Test
    void compilesCommonSummaryStatistics(@Mktmp final Path temp) throws Exception {
        final Path dir = SummaryTest.modular(temp);
        final Summary summary = new Summary(dir);
        final Path report = summary.collect();
        MatcherAssert.assertThat(
            "report must contain statistics from both modules",
            new TextOf(report).asString(),
            Matchers.allOf(
                Matchers.containsString(
                    "\"/phi/org/eolang/hone/client/Client.phi\",\"/phi-optimized/org/eolang/hone/client/Client.phi\",2,4000"
                ),
                Matchers.containsString(
                    "\"/phi/org/eolang/hone/server/Server.phi\",\"/phi-optimized/org/eolang/hone/server/Server.phi\",10,3200"
                )
            )
        );
    }

    @Test
    void skipsModulesWithoutStatistics(@Mktmp final Path temp) throws Exception {
        final Path dir = SummaryTest.modular(temp);
        Files.deleteIfExists(dir.resolve("server/hone-statistics.csv"));
        Files.deleteIfExists(dir.resolve("client/hone-statistics.csv"));
        final Summary summary = new Summary(dir);
        final Path report = summary.collect();
        MatcherAssert.assertThat(
            "report shouldn't be generated if no statistics found",
            Matchers.not(report.toFile().exists())
        );
    }

    private static Path modular(final Path root) throws Exception {
        Files.createDirectories(root.resolve("server"));
        Files.createDirectories(root.resolve("client"));
        Files.write(
            root.resolve("server/hone-statistics.csv"),
            new BytesOf(new ResourceOf("csv/hone-statistics-server.csv")).asBytes()
        );
        Files.write(
            root.resolve("client/hone-statistics.csv"),
            new BytesOf(new ResourceOf("csv/hone-statistics-client.csv")).asBytes()
        );
        return root;
    }
}
