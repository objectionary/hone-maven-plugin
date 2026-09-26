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
 * Test case for the {@code docker} CI workflow, which built the image from a
 * context whose {@code rules/} holds only a {@code .gitkeep}, so it spent its
 * half hour on an image with no rewrite rules in it (see #1121).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class DockerWorkflowTest {

    @Test
    void stagesTheRulesIntoTheBuildContext() throws IOException {
        MatcherAssert.assertThat(
            "the job must copy the rules into the context the way the release does, or it builds an image the release never publishes",
            new String(
                Files.readAllBytes(Paths.get(".github/workflows/docker.yml")),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("cp -R ../rules rules")
        );
    }
}
