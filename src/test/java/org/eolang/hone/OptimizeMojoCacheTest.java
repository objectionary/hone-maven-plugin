/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the EO cache directory, which {@link OptimizeMojo} created
 * and bind-mounted on every run, although nothing reads it (see #1190).
 *
 * @since 0.24.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoCacheTest {

    @Test
    void createsNoCacheDirectory(@Mktmp final Path dir) throws Exception {
        final Path cache = dir.resolve("absent-eo-cache");
        new Farea(dir).together(
            f -> {
                f.files().file("src/main/java/Foo.java").write(
                    "class Foo { }".getBytes(StandardCharsets.UTF_8)
                );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("optimize")
                    .configuration()
                    .set("alwaysWithDocker", true)
                    .set("image", "hone-image-that-does-not-exist:0.0.0")
                    .set("cache", cache.toString());
                f.execQuiet("process-classes");
            }
        );
        MatcherAssert.assertThat(
            "the goal must reach the docker command and still leave no cache directory behind",
            Arrays.asList(dir.resolve("target/hone-extra").toFile(), cache.toFile()),
            Matchers.contains(
                FileMatchers.anExistingDirectory(),
                Matchers.not(FileMatchers.anExistingFileOrDirectory())
            )
        );
    }
}
