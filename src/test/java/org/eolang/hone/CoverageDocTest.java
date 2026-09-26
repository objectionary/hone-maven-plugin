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
 * Test case for the coverage table description in {@code README.md}, which
 * said the {@code Edits} column compares MD5 checksums, while the script has
 * counted stream references since {@code 9cf35567} (see #1120).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class CoverageDocTest {

    @Test
    void doesNotPromiseAChecksumComparison() throws IOException {
        MatcherAssert.assertThat(
            "the script counts stream references, so the README must not promise MD5 checksums",
            CoverageDocTest.readme(),
            Matchers.not(Matchers.containsString("MD5 checksums"))
        );
    }

    @Test
    void countsWhatTheScriptCounts() throws IOException {
        MatcherAssert.assertThat(
            "the script and the README must agree on what makes a class an edit",
            new String(
                Files.readAllBytes(Paths.get(".github/hone-it.sh")),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("java/util/stream/(Int|Long|Double)?Stream")
        );
    }

    private static String readme() throws IOException {
        return new String(Files.readAllBytes(Paths.get("README.md")), StandardCharsets.UTF_8);
    }
}
