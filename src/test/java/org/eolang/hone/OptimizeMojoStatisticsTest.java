/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Test case for {@link OptimizeMojo}, against the statistics CSV it writes.
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoStatisticsTest {

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutPhino
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void generatesStatisticsWithoutDocker(@Mktmp final Path home) throws IOException {
        new Farea(home).together(OptimizeMojoStatisticsTest::runWithoutDocker);
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(600L)
    @DisabledWithoutDocker
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void generatesStatisticsWithDocker(
        @Mktmp final Path home,
        @RandomImage final String image
    ) throws IOException {
        new Farea(home).together(f -> OptimizeMojoStatisticsTest.runWithDocker(f, image));
    }

    /**
     * Body of {@link #generatesStatisticsWithoutDocker}.
     * @param fea Fake Maven project
     * @throws IOException If the build fails to run
     */
    private static void runWithoutDocker(final Farea fea) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/statistics/Statistics.java").write(
                """
                package statistics;
                class Statistics {
                    byte[] foo() {
                        return new byte[] {(byte) 0x01, (byte) 0x02};
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/statistics/SntatisticsSecond.java").write(
                """
                package statistics;
                class StatisticsSecond {
                    byte[] foo() {
                        return new byte[] {(byte) 0x01, (byte) 0x02};
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("optimize")
            .configuration()
            .set("debug", "true")
            .set("alwaysWithDocker", "false")
            .set("grepIn", ".*");
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            "a statistics file must be created and the content of the statistics file must be correct",
            fea.files().file("target/hone-statistics.csv").content(),
            Matchers.allOf(
                Matchers.containsString("ID,Before,After,Changed,LinesPerSec"),
                Matchers.containsString(
                    String.format(
                        "%s,\"%s\",\"%s\",%d",
                        "1/2",
                        fea.files()
                            .file("target/hone/phi/statistics/Statistics.phi")
                            .path(),
                        fea.files()
                            .file("target/hone/phi-optimized/statistics/Statistics.phi")
                            .path(),
                        0
                    )
                ),
                Matchers.containsString(
                    String.format(
                        "%s,\"%s\",\"%s\",%d",
                        "2/2",
                        fea.files().file(
                            "target/hone/phi/statistics/StatisticsSecond.phi"
                        ).path(),
                        fea.files().file(
                            "target/hone/phi-optimized/statistics/StatisticsSecond.phi"
                        ).path(),
                        0
                    )
                )
            )
        );
    }

    /**
     * Body of {@link #generatesStatisticsWithDocker}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runWithDocker(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/statistics/StatisticsFromDocker.java").write(
                """
                package statistics;
                class StatisticsFromDocker {
                    byte[] foo() {
                        return new byte[] {(byte) 0x01, (byte) 0x02};
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("debug", "true")
            .set("alwaysWithDocker", "true")
            .set("image", image)
            .set("grepIn", ".*");
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            "a statistics file mus be created and the content of statistics file must be correct",
            fea.files().file("target/hone-statistics.csv").content(),
            Matchers.allOf(
                Matchers.containsString("ID,Before,After,Changed,LinesPerSec"),
                Matchers.containsString(
                    String.format(
                        "%s,\"%s\",\"%s\",%d",
                        "1/1",
                        "/target/hone/phi/statistics/StatisticsFromDocker.phi",
                        "/target/hone/phi-optimized/statistics/StatisticsFromDocker.phi",
                        0
                    )
                )
            )
        );
    }
}
