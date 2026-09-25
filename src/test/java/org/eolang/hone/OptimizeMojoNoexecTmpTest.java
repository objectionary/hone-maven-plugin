/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the way {@link OptimizeMojo} launches its scaffolding
 * scripts on the non-Docker path, where a {@code noexec} {@code /tmp}
 * would otherwise refuse a direct exec (see #1068).
 *
 * @since 0.24.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoNoexecTmpTest {

    @Test
    void runsEntryScriptThroughBashNotDirectExec() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh must be launched through bash, so a noexec tmpdir does not block it",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/java/org/eolang/hone/OptimizeMojo.java")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("new Jaxec(\"bash\", temp.path().resolve(\"entry.sh\")")
        );
    }

    @Test
    void runsRewriteScriptThroughBashNotDirectExec() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh must launch rewrite.sh through bash too, one level down",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("bash \"${SELF}/rewrite.sh\"")
        );
    }
}
