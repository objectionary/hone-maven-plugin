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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the edit count {@link OptimizeMojo} writes through {@code rewrite.sh}.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoChangedTest {

    @Test
    void countsEveryReplacedLineOnce(@Mktmp final Path temp) throws IOException {
        final Path before = temp.resolve("before.phi");
        final Path after = temp.resolve("after.phi");
        Files.write(before, "a%nb%nc%n".formatted().getBytes(StandardCharsets.UTF_8));
        Files.write(after, "x%ny%nz%n".formatted().getBytes(StandardCharsets.UTF_8));
        final Matcher matcher = Pattern.compile("    changed=.*?\\n").matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No edit count found in rewrite.sh");
        }
        MatcherAssert.assertThat(
            "three replaced lines must be counted as three, not as six",
            new Jaxec(
                "bash", "-c",
                String.format(
                    "phi=%s%npho=%s%n%sprintf '%%s' \"${changed}\"",
                    before, after, matcher.group()
                )
            ).exec().stdout().trim(),
            Matchers.equalTo("3")
        );
    }
}
