/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the CSV header {@code smoke.sh} writes, which must match
 * the columns {@code hone-it.sh} actually appends, the same columns
 * {@code coverage.sh} already declares for the same rows (see #1076).
 *
 * @since 0.24.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class SmokeScriptCsvHeaderTest {

    @Test
    void writesTheSameHeaderAsCoverageScript() throws IOException {
        MatcherAssert.assertThat(
            "smoke.sh must write the same 11-column header coverage.sh writes for hone-it.sh's rows",
            SmokeScriptCsvHeaderTest.header(".github/smoke.sh"),
            Matchers.equalTo(SmokeScriptCsvHeaderTest.header(".github/coverage.sh"))
        );
    }

    private static String header(final String script) throws IOException {
        final Matcher matcher = Pattern.compile("printf '([^\\n']+)\\\\n' >").matcher(
            new String(Files.readAllBytes(Paths.get(script)), StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            String.format("%s must write a CSV header with printf", script),
            matcher.find(), Matchers.is(true)
        );
        return matcher.group(1);
    }
}
