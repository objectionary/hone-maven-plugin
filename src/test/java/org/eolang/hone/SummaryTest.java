/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.cactoos.bytes.BytesOf;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link Rules}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class SummaryTest {

    @Test
    void compilesCommonSummaryStatistics(@Mktmp final Path temp) throws Exception {
        MatcherAssert.assertThat(
            "report must contain statistics from both modules",
            new TextOf(
                new Summary(SummaryTest.modules(SummaryTest.modular(temp)), temp).collect()
            ).asString(),
            Matchers.allOf(
                Matchers.containsString(
                    "/phi/org/eolang/hone/client/Client.phi,/phi-optimized/org/eolang/hone/client/Client.phi,2,4000"
                ),
                Matchers.containsString(
                    "/phi/org/eolang/hone/server/Server.phi,/phi-optimized/org/eolang/hone/server/Server.phi,10,3200"
                )
            )
        );
    }

    @Test
    void keepsTheReportStableAcrossRepeatedCollections(@Mktmp final Path temp) throws Exception {
        final Path root = SummaryTest.modular(temp);
        final Summary summary = new Summary(
            SummaryTest.modules(root),
            Files.createDirectories(root.resolve("target"))
        );
        final Path report = summary.collect();
        final String expected = new TextOf(report).asString();
        summary.collect();
        MatcherAssert.assertThat(
            "report must be stable across repeated collections",
            new TextOf(report).asString(),
            Matchers.equalTo(expected)
        );
    }

    @Test
    void removesPreviousSummaryWhenNoStatistics(@Mktmp final Path temp) throws Exception {
        final Path dir = SummaryTest.modular(temp);
        final Summary summary = new Summary(SummaryTest.modules(dir), dir);
        summary.collect();
        Files.deleteIfExists(dir.resolve("server/hone-statistics.csv"));
        Files.deleteIfExists(dir.resolve("client/hone-statistics.csv"));
        MatcherAssert.assertThat(
            "stale report shouldn't remain if no statistics are found",
            summary.collect().toFile().exists(),
            Matchers.is(false)
        );
    }

    @Test
    void includesSymlinkedModuleStatistics(@Mktmp final Path temp) throws Exception {
        Files.write(
            Files.createSymbolicLink(
                temp.resolve("linked"),
                Files.createDirectories(temp.resolve("real"))
            ).resolve("hone-statistics.csv"),
            new BytesOf(new ResourceOf("csv/hone-statistics-server.csv")).asBytes()
        );
        MatcherAssert.assertThat(
            "report must include statistics reached through a symlinked module (see #866)",
            new TextOf(
                new Summary(Collections.singletonList(temp.resolve("linked")), temp).collect()
            ).asString(),
            Matchers.containsString("Server.phi")
        );
    }

    @Test
    void keepsStatisticsOfSingleModule(@Mktmp final Path temp) throws Exception {
        final Path own = Files.createDirectories(temp.resolve("target"))
            .resolve("hone-statistics.csv");
        Files.write(
            own,
            new BytesOf(new ResourceOf("csv/hone-statistics-server.csv")).asBytes()
        );
        new Summary(temp, temp.resolve("target")).collect();
        MatcherAssert.assertThat(
            "statistics of a project without modules must survive the summary",
            own.toFile().exists(),
            Matchers.is(true)
        );
    }

    @Test
    void countsSymlinkedModuleOnce(@Mktmp final Path temp) throws Exception {
        Files.write(
            Files.createDirectories(temp.resolve("server")).resolve("hone-statistics.csv"),
            new BytesOf(new ResourceOf("csv/hone-statistics-server.csv")).asBytes()
        );
        Files.createSymbolicLink(temp.resolve("linked"), temp.resolve("server"));
        MatcherAssert.assertThat(
            "report must count a module reached through a symlink only once",
            new CSV(
                new Summary(
                    Arrays.asList(temp.resolve("server"), temp.resolve("linked")), temp
                ).collect()
            ).size(),
            Matchers.equalTo(1)
        );
    }

    @Test
    void ignoresStatisticsOfModulesOutsideTheBuild(@Mktmp final Path temp) throws Exception {
        final Path root = SummaryTest.modular(temp);
        MatcherAssert.assertThat(
            "report must not include statistics of a module that is not in the build",
            new TextOf(
                new Summary(
                    Collections.singletonList(root.resolve("client")),
                    Files.createDirectories(root.resolve("target"))
                ).collect()
            ).asString(),
            Matchers.not(Matchers.containsString("Server.phi"))
        );
    }

    @Test
    void ignoresStatisticsOfProjectsNestedInTheBuildDirectory(@Mktmp final Path temp)
        throws Exception {
        final Path target = Files.createDirectories(temp.resolve("target"));
        Files.write(
            target.resolve("hone-statistics.csv"),
            new BytesOf(new ResourceOf("csv/hone-statistics-client.csv")).asBytes()
        );
        Files.write(
            Files.createDirectories(target.resolve("it/modular/server/target"))
                .resolve("hone-statistics.csv"),
            new BytesOf(new ResourceOf("csv/hone-statistics-server.csv")).asBytes()
        );
        MatcherAssert.assertThat(
            "report must not include statistics of a project kept inside the build directory (see #1197)",
            new TextOf(
                new Summary(Collections.singletonList(target), temp).collect()
            ).asString(),
            Matchers.not(Matchers.containsString("Server.phi"))
        );
    }

    private static List<Path> modules(final Path root) {
        return Arrays.asList(root.resolve("server"), root.resolve("client"));
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
