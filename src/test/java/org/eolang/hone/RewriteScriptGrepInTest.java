/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the {@code grep_in_check} function of {@code rewrite.sh},
 * which took a pattern starting with a hyphen for an option of grep
 * (see #1397).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptGrepInTest {

    @Test
    void matchesPatternThatStartsWithHyphen(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "a pattern starting with a hyphen must match the XMIR, not be taken for an option",
            RewriteScriptGrepInTest.check(home, "-70<", "<o>6D-61-70</o>"),
            Matchers.equalTo("0")
        );
    }

    @Test
    void missesPatternThatStartsWithHyphen(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "a pattern starting with a hyphen that is absent in the XMIR must not match",
            RewriteScriptGrepInTest.check(home, "-71<", "<o>6D-61-70</o>"),
            Matchers.equalTo("1")
        );
    }

    private static String check(final Path home, final String pattern, final String xmir)
        throws IOException {
        final Path xmi = home.resolve("Foo.xmir");
        Files.write(xmi, xmir.getBytes(StandardCharsets.UTF_8));
        final String text = new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
            ),
            StandardCharsets.UTF_8
        );
        final int start = text.indexOf("function grep_in_check {");
        return new Jaxec(
            "bash", "-c",
            String.format(
                "%s%nrc=0%ngrep_in_check \"${PATTERN}\" \"${XMIR}\" || rc=$?%nprintf '%%s' \"${rc}\"",
                text.substring(start, text.indexOf(String.format("%n}%n"), start) + 3)
            )
        ).withEnv("PATTERN", pattern)
            .withEnv("XMIR", xmi.toString())
            .withCheck(false)
            .execUnsafe()
            .stdout()
            .trim();
    }
}
