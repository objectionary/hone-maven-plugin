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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the {@code check_xml} macro of the rules {@code Makefile},
 * whose {@code xmllint} branch ended in a {@code printf}, so a rejected XMIR
 * printed a complaint and let the target finish green (see #1118).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class CheckXmlMacroTest {

    @Test
    void failsOnXmlTheLinterRejects(@Mktmp final Path home) throws IOException {
        CheckXmlMacroTest.assumeLinter();
        final Path broken = home.resolve("broken.xmir");
        Files.write(broken, "<object><o name=\"Foo\"</object>".getBytes(StandardCharsets.UTF_8));
        MatcherAssert.assertThat(
            "a rejected XMIR must fail the target, or make finishes green over XML that xmllint cannot parse",
            new Jaxec("bash", "-c", CheckXmlMacroTest.macro(broken))
                .withCheck(false)
                .execUnsafe()
                .code(),
            Matchers.not(Matchers.equalTo(0))
        );
    }

    @Test
    void passesOnXmlTheLinterAccepts(@Mktmp final Path home) throws IOException {
        CheckXmlMacroTest.assumeLinter();
        final Path good = home.resolve("good.xmir");
        Files.write(good, "<object><o name=\"Foo\"/></object>".getBytes(StandardCharsets.UTF_8));
        MatcherAssert.assertThat(
            "a well formed XMIR must pass, or every target fails",
            new Jaxec("bash", "-c", CheckXmlMacroTest.macro(good))
                .withCheck(false)
                .execUnsafe()
                .code(),
            Matchers.equalTo(0)
        );
    }

    private static void assumeLinter() throws IOException {
        Assumptions.assumeTrue(
            new Jaxec("bash", "-c", "command -v xmllint")
                .withCheck(false)
                .execUnsafe()
                .code() == 0,
            "xmllint is not installed, so the macro cannot be exercised"
        );
    }

    private static String macro(final Path file) throws IOException {
        final String text = new String(
            Files.readAllBytes(
                Paths.get("src/test/resources/org/eolang/hone/rules/streams/Makefile")
            ),
            StandardCharsets.UTF_8
        );
        final int start = text.indexOf("define check_xml");
        return text.substring(text.indexOf('\n', start) + 1, text.indexOf("endef", start))
            .replace("$(1)", file.toString())
            .replace("$(2)", "after-phino")
            .replace("$$", "$");
    }
}
