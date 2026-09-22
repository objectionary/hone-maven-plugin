/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the extra rules of {@link OptimizeMojo}.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoExtraTest {

    @Test
    void keepsOneCopyOfExtraRuleAfterTwoRuns(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.files().file("src/main/java/Foo.java").write(
                    "class Foo { }".getBytes(StandardCharsets.UTF_8)
                );
                f.files().file("src/rules/one.yml").write(
                    "name: one".getBytes(StandardCharsets.UTF_8)
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
                    .set("extra", new String[] {"src/rules/one.yml"});
                f.execQuiet("process-classes");
                f.execQuiet("process-classes");
            }
        );
        try (Stream<Path> files = Files.list(dir.resolve("target/hone-extra"))) {
            MatcherAssert.assertThat(
                "the extra rule must be copied once, no matter how many runs were before",
                files.map(p -> p.getFileName().toString()).collect(Collectors.toList()),
                Matchers.contains("0000-one.yml")
            );
        }
    }
}
