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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the way {@link OptimizeMojo} creates the EO cache directory.
 *
 * @since 0.24.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoCacheTest {

    @Test
    void createsCacheDirectoryBeforeMountingIt(@Mktmp final Path dir) throws Exception {
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
            "the cache directory must be created before it is bind-mounted (see #1066)",
            cache.toFile().isDirectory(),
            Matchers.is(true)
        );
    }
}
