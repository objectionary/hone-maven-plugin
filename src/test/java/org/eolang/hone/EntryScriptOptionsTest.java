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
 * Test case for the Maven options {@code entry.sh} builds, which carried
 * {@code -Deo.version} and {@code -Djeo.version} although no jeo goal declares
 * a parameter with either property, and the goal's version comes from its own
 * coordinate (see #1112), and {@code -Deo.cache}, which no jeo goal reads
 * either, together with the {@code EO_CACHE} check that guarded it (see #1190).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptOptionsTest {

    @Test
    void passesNoEoVersionProperty() throws IOException {
        MatcherAssert.assertThat(
            "no jeo goal reads eo.version, so passing it only prints a line and changes nothing",
            EntryScriptOptionsTest.script(),
            Matchers.not(Matchers.containsString("-Deo.version="))
        );
    }

    @Test
    void passesNoJeoVersionProperty() throws IOException {
        MatcherAssert.assertThat(
            "the jeo version comes from the goal coordinate, so the property is dead weight",
            EntryScriptOptionsTest.script(),
            Matchers.not(Matchers.containsString("-Djeo.version="))
        );
    }

    @Test
    void passesNoEoCacheProperty() throws IOException {
        MatcherAssert.assertThat(
            "no jeo goal reads eo.cache, so the property is dead weight",
            EntryScriptOptionsTest.script(),
            Matchers.not(Matchers.containsString("-Deo.cache="))
        );
    }

    @Test
    void requiresNoEoCache() throws IOException {
        MatcherAssert.assertThat(
            "nothing reads the EO cache, so its absence must not stop the script",
            EntryScriptOptionsTest.script(),
            Matchers.not(Matchers.containsString("-z \"${EO_CACHE}\""))
        );
    }

    private static String script() throws IOException {
        return new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
            ),
            StandardCharsets.UTF_8
        );
    }
}
