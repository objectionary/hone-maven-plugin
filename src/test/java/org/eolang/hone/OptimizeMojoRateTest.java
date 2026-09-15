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
 * Test case for the rate {@link OptimizeMojo} prints through {@code rewrite.sh}.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoRateTest {

    @Test
    void survivesAClockWithOneSecondResolution() throws IOException {
        final Matcher matcher = Pattern.compile("  per=.*?\\n", Pattern.DOTALL).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No rate calculation found in rewrite.sh");
        }
        MatcherAssert.assertThat(
            "a file that finishes inside one second cannot kill the worker",
            new Jaxec(
                "bash", "-c",
                String.format(
                    "set -e -o pipefail%nnow(){ date '+%%s'; }%nstart=$(now)%ns_lines=1234%n%sprintf '%%s' \"${per}\"",
                    matcher.group()
                )
            ).exec().stdout().trim(),
            Matchers.not(Matchers.emptyString())
        );
    }
}
