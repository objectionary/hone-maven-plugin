/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
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
 * Test case for the rule name {@link OptimizeMojo} prints in small steps mode.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoRuleNameTest {

    @Test
    void stripsTheExtensionOfAPhinoRule() throws IOException {
        final Matcher matcher = Pattern.compile(
            "m=\\$\\(basename.*?\\n(\\s*m=\"\\$\\{m%\\.\\*\\}\"\\n)?",
            Pattern.DOTALL
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No rule name stripping found in rewrite.sh");
        }
        MatcherAssert.assertThat(
            "the extension of a phino rule must be stripped from the name that is printed",
            new Jaxec(
                "bash", "-c",
                String.format(
                    "rule=/x/101-remove-self-reference-labels.phr%n%sprintf '%%s' \"${m}\"",
                    matcher.group()
                )
            ).exec().stdout().trim(),
            Matchers.equalTo("101-remove-self-reference-labels")
        );
    }
}
