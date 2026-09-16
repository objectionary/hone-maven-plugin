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
import java.util.Arrays;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the locale {@code rewrite.sh} sets.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoLocaleTest {

    @Test
    void takesTheLocaleTheHostHas() throws IOException {
        MatcherAssert.assertThat(
            "the locale the host doesn't have must not be exported",
            new Jaxec(
                "bash", "-c",
                String.format(
                    "set -e%nlocale() { echo 'C.UTF-8'; echo 'POSIX'; }%nLANG=ru_RU.UTF-8%n%s%nprintf '%%s' \"${LANG}\"",
                    this.paragraph()
                )
            ).exec().stdout().trim(),
            Matchers.endsWith("C.UTF-8")
        );
    }

    /**
     * The paragraph of the script that sets the locale.
     *
     * @return Lines of the script
     * @throws IOException If fails to read the script
     */
    private String paragraph() throws IOException {
        return Arrays.stream(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            ).split(String.format("%n%n"))
        )
            .filter(part -> part.contains("Setting locale to"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No locale block found in rewrite.sh"));
    }
}
