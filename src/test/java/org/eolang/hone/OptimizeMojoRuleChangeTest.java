/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the skip check of {@code rewrite.sh}.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoRuleChangeTest {

    @Test
    void rewritesAgainAfterTheRuleIsEdited() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path home = temp.path();
            final Path phino = home.resolve("phino");
            Files.write(
                phino,
                String.format("#!/usr/bin/env bash%ncat \"${@: -1}\"%n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            if (!phino.toFile().setExecutable(true)) {
                throw new IllegalStateException("Can't make the fake phino executable");
            }
            final Path rule = home.resolve("simple.phr");
            Files.write(rule, "rule".getBytes(StandardCharsets.UTF_8));
            Files.write(home.resolve("in.xmir"), "<xmir/>".getBytes(StandardCharsets.UTF_8));
            final String script = Paths.get(
                "src/main/resources/org/eolang/hone/scaffolding/rewrite.sh"
            ).toAbsolutePath().toString();
            MatcherAssert.assertThat(
                "an edited rule must not be skipped",
                new Jaxec(
                    "bash", "-c",
                    String.format(
                        String.join(
                            String.format("%n"),
                            "set -e",
                            "export PATH=%1$s:${PATH} TARGET=%1$s HONE_RULES=%1$s/simple.phr",
                            "export HONE_MAX_CYCLES=1 HONE_MAX_DEPTH=1 HONE_SMALL_STEPS=false",
                            "export HONE_STATISTICS=false HONE_VERBOSE=false HONE_DEBUG=false",
                            "run() { bash %2$s rewrite 1/1 %1$s/a.phi %1$s/b.phi %1$s/in.xmir %1$s/out.xmir; }",
                            "run > /dev/null",
                            "touch -d '+1 minute' %1$s/simple.phr",
                            "run"
                        ),
                        home,
                        script
                    )
                ).exec().stdout(),
                Matchers.not(Matchers.containsString("skipping"))
            );
        }
    }
}
