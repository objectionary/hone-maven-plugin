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
 * Test case for the per-file timeout of {@code rewrite.sh}.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoTimeoutTest {

    @Test
    void sweepsTempFilesOfKilledWorker() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Path home = temp.path();
            final Path phino = home.resolve("phino");
            Files.write(
                phino,
                String.format("#!/usr/bin/env bash%necho partial%nsleep 30%n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            if (!phino.toFile().setExecutable(true)) {
                throw new IllegalStateException("Can't make the fake phino executable");
            }
            Files.write(home.resolve("in.xmir"), "<xmir/>".getBytes(StandardCharsets.UTF_8));
            MatcherAssert.assertThat(
                "a worker killed by the timeout must not leave its temp files behind",
                new Jaxec(
                    "bash", "-c",
                    String.format(
                        String.join(
                            String.format("%n"),
                            "set -e",
                            "export PATH=%1$s:${PATH} TARGET=%1$s HONE_RULES=%1$s/simple.phr",
                            "export HONE_MAX_CYCLES=1 HONE_MAX_DEPTH=1 HONE_SMALL_STEPS=false",
                            "export HONE_STATISTICS=false HONE_VERBOSE=false HONE_DEBUG=false",
                            "export HONE_TIMEOUT=1 HONE_KILL_GRACE=1",
                            "cd %1$s",
                            "bash %2$s rewrite_with_timeout 1/1 a/a.phi b/a.phi in.xmir c/a.xmir > /dev/null",
                            "find %1$s -name '*.tmp.*'"
                        ),
                        home,
                        Paths.get(
                            "src/main/resources/org/eolang/hone/scaffolding/rewrite.sh"
                        ).toAbsolutePath()
                    )
                ).exec().stdout(),
                Matchers.emptyString()
            );
        }
    }
}
