/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link RmiMojo}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class RmiMojoTest {

    @Test
    void recordsTimingEvenWhenImageIsNotRemoved(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution()
                    .phase("process-classes")
                    .goals("rmi")
                    .configuration()
                    .set("image", "hone-absent-image:0")
                    .set("alwaysWithDocker", "true");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "the rmi goal must record its time in hone-timings.csv",
                    f.files().file("target/hone-timings.csv").content(),
                    Matchers.containsString("\"rmi\"")
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    void pullsAndRemovesDockerImage(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution()
                    .phase("process-classes")
                    .goals("pull", "rmi")
                    .configuration()
                    .set("image", "hello-world");
                f.exec("test");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
    }
}
