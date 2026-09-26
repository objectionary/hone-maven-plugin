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
 * Test case for the {@code actionlint} CI workflow, whose reviewdog
 * action must be told to fail the job on a finding, otherwise it
 * defaults to {@code fail_level: none} and always exits zero (see #1072).
 *
 * @since 0.24.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class ActionlintWorkflowTest {

    @Test
    void setsFailLevelToErrorOnTheActionlintAction() throws IOException {
        MatcherAssert.assertThat(
            "the actionlint job must set fail_level to error, or the action's own none default hides every finding",
            new String(
                Files.readAllBytes(Paths.get(".github/workflows/actionlint.yml")),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("fail_level: error")
        );
    }
}
