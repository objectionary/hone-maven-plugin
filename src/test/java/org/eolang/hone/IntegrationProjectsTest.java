/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the integration projects under {@code src/it}, which never ran
 * on a pull request because every gating Maven command passed
 * {@code -Dinvoker.skip} (see #1071).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class IntegrationProjectsTest {

    @Test
    void runsThemInAGatingWorkflow() throws IOException {
        final Collection<String> running = new ArrayList<>(0);
        try (Stream<Path> workflows = Files.list(Paths.get(".github/workflows"))) {
            for (final Path workflow : workflows.toArray(Path[]::new)) {
                final String text = new String(
                    Files.readAllBytes(workflow), StandardCharsets.UTF_8
                );
                if (text.contains("pull_request") && text.contains("invoker:run")) {
                    running.add(workflow.getFileName().toString());
                }
            }
        }
        MatcherAssert.assertThat(
            "at least one workflow that gates a pull request must run the projects under src/it",
            running,
            Matchers.not(Matchers.empty())
        );
    }
}
