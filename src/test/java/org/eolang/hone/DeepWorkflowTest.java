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
 * Test case for the {@code deep} CI workflow, which downloaded phino over
 * plain http with no checksum of its own, so the binary that the
 * {@code @DisabledWithoutPhino} tests run could be swapped in transit
 * (see #1177).
 *
 * @since 0.31.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class DeepWorkflowTest {

    @Test
    void runsTheSharedInstallScript() throws IOException {
        MatcherAssert.assertThat(
            "the deep workflow must install phino through install-phino.sh, which checks the sha512",
            new String(
                Files.readAllBytes(Paths.get(".github/workflows/deep.yml")),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("./.github/install-phino.sh")
        );
    }

    @Test
    void hasNoUnverifiedPhinoDownload() throws IOException {
        MatcherAssert.assertThat(
            "deep.yml must not download phino itself, or the bytes arrive with no checksum",
            new String(
                Files.readAllBytes(Paths.get(".github/workflows/deep.yml")),
                StandardCharsets.UTF_8
            ),
            Matchers.not(Matchers.containsString("http://phino.objectionary.com"))
        );
    }
}
