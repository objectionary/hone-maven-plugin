/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the exclude globs {@code entry.sh} builds for pre-Java-16
 * classes. The list is comma-joined and Maven splits it at every comma, so a
 * project path with a comma produced two broken globs, the old class was not
 * excluded, and its {@code Stream.mapMulti} failed on Java 8 (see #1347).
 *
 * @since 0.33.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptExcludesTest {

    @Test
    void matchesTheClassUnderAComma() throws IOException {
        final String classfile = "/tmp/hone/a,b/target/classes/old/Old.class";
        final String escaped = EntryScriptExcludesTest.escaped(classfile);
        MatcherAssert.assertThat(
            "a comma in the project path must not survive into the comma-joined exclude list",
            escaped,
            Matchers.not(Matchers.containsString(","))
        );
        MatcherAssert.assertThat(
            "the comma-free glob must still match the very class it was built from",
            FileSystems.getDefault().getPathMatcher(String.format("glob:%s", escaped))
                .matches(Paths.get(classfile)),
            Matchers.is(true)
        );
    }

    @Test
    void keepsEscapingOtherGlobCharacters() throws IOException {
        final String classfile = "/tmp/hone/a,b[1]/target/classes/old/Old.class";
        final String escaped = EntryScriptExcludesTest.escaped(classfile);
        MatcherAssert.assertThat(
            "the comma must go, but every other glob character must keep its escape (see #1205)",
            escaped,
            Matchers.allOf(
                Matchers.not(Matchers.containsString(",")),
                Matchers.containsString("\\[1\\]")
            )
        );
        MatcherAssert.assertThat(
            "the escaped glob must still match the class with a bracketed directory",
            FileSystems.getDefault().getPathMatcher(String.format("glob:%s", escaped))
                .matches(Paths.get(classfile)),
            Matchers.is(true)
        );
    }

    private static String escaped(final String classfile) throws IOException {
        final String text = new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
            ),
            StandardCharsets.UTF_8
        );
        final int start = text.indexOf("sed '", text.indexOf("globs+=(")) + 5;
        return new Jaxec("sed", text.substring(start, text.indexOf('\'', start)))
            .withStdin(classfile)
            .withCheck(false)
            .execUnsafe()
            .stdout()
            .trim();
    }
}
