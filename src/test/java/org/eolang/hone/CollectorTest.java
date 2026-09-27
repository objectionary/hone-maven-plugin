/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link Collector}, which carried a guard against picking up
 * the summary it writes, although the summary never shares the name the walk
 * looks for and the guard could not fire (see #1113).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
final class CollectorTest {

    @Test
    void takesEveryStatisticsFileItWalks(@Mktmp final Path home) throws Exception {
        Files.write(
            Files.createDirectories(home.resolve("one")).resolve("hone-statistics.csv"),
            CollectorTest.csv().getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            Files.createDirectories(home.resolve("two")).resolve("hone-statistics.csv"),
            CollectorTest.csv().getBytes(StandardCharsets.UTF_8)
        );
        final List<CSV> found = new ArrayList<>(0);
        Files.walkFileTree(home, new Collector(found, "hone-statistics.csv"));
        MatcherAssert.assertThat(
            "both statistics files must be taken",
            found,
            Matchers.hasSize(2)
        );
    }

    @Test
    void ignoresAFileOfAnotherName(@Mktmp final Path home) throws Exception {
        Files.write(
            home.resolve("hone-summary.csv"),
            CollectorTest.csv().getBytes(StandardCharsets.UTF_8)
        );
        final List<CSV> found = new ArrayList<>(0);
        Files.walkFileTree(home, new Collector(found, "hone-statistics.csv"));
        MatcherAssert.assertThat(
            "the summary is named differently, so the walk must leave it alone",
            found,
            Matchers.empty()
        );
    }

    private static String csv() {
        return String.format(
            "ID,Before,After,Changed,LinesPerSec%n1/1,/phi/Foo.phi,/phi-optimized/Foo.phi,3,100%n"
        );
    }
}
