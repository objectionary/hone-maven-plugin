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
 * Test case for the parameters {@code CLAUDE.md} documents, which named a
 * {@code hone.verbose} that does not exist and described a default of
 * {@code hone.grep-in} made of two names instead of fifteen (see #1075).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class ParametersDocTest {

    @Test
    void documentsOnlyTheParametersThatExist() throws IOException {
        MatcherAssert.assertThat(
            "hone.verbose is not a parameter, so offering it as a switch sends the reader to a flag that does nothing",
            ParametersDocTest.doc(),
            Matchers.allOf(
                Matchers.not(Matchers.containsString("`hone.debug=true` and `hone.verbose`")),
                Matchers.containsString("Logger.isDebugEnabled")
            )
        );
    }

    @Test
    void describesTheGrepInDefaultAsItIs() throws IOException {
        MatcherAssert.assertThat(
            "the default pre-filter keeps a class that calls distinct, so the doc must not say only map and filter are kept",
            ParametersDocTest.doc(),
            Matchers.containsString("DEFAULT_GREP_IN")
        );
    }

    private static String doc() throws IOException {
        return new String(Files.readAllBytes(Paths.get("CLAUDE.md")), StandardCharsets.UTF_8);
    }
}
