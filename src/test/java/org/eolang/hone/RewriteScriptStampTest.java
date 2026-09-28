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
 * Test case for the freshness stamp of {@code rewrite.sh}, which was built
 * from the absolute rule paths and their modification times, both of which the
 * host path renews on every run, so the skip could never fire (see #1119).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptStampTest {

    @Test
    void staysTheSameWhenTheRulesMoveToAnotherDirectory(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "the same rule in another temporary directory must give the same stamp, or a host run never skips",
            RewriteScriptStampTest.stamp(home.resolve("first"), "name: one"),
            Matchers.equalTo(RewriteScriptStampTest.stamp(home.resolve("second"), "name: one"))
        );
    }

    @Test
    void changesWhenTheRuleChanges(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "an edited rule must give another stamp, or its edit is skipped",
            RewriteScriptStampTest.stamp(home.resolve("before"), "name: one"),
            Matchers.not(
                Matchers.equalTo(RewriteScriptStampTest.stamp(home.resolve("after"), "name: two"))
            )
        );
    }

    private static String stamp(final Path dir, final String body) throws IOException {
        final Path rule = Files.createDirectories(dir.resolve("rules/streams"))
            .resolve("101-demo.phr");
        Files.write(rule, body.getBytes(StandardCharsets.UTF_8));
        final String text = new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
            ),
            StandardCharsets.UTF_8
        );
        final int start = text.indexOf("stamp=\"${HONE_GREP_IN}");
        return new Jaxec(
            "bash", "-c",
            String.format(
                "set -e%nrules=(%s)%n%s%nprintf '%%s' \"${stamp}\"",
                rule,
                text.substring(start, text.indexOf(String.format("%ndone"), start) + 5)
            )
        ).withCheck(false).execUnsafe().stdout();
    }
}
