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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the small-steps suffix width in {@code rewrite.sh}, which
 * must grow with the number of rules, or a rule set past 99 sorts
 * {@code .100} between {@code .10} and {@code .11} (see #1078).
 *
 * @since 0.24.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptSmallStepsWidthTest {

    @Test
    void sizesTheSuffixWidthToTheRuleCount() throws IOException {
        MatcherAssert.assertThat(
            "rewrite.sh must size the small-steps suffix width to the rule count",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("printf \"%0${width}d\"")
        );
    }

    @Test
    void sortsPastNinetyNineRulesInOrder() throws IOException {
        MatcherAssert.assertThat(
            "with 152 rules, the suffixes must sort .007, .099, .100, .101, .152 in that order",
            new Jaxec(
                "bash", "-c",
                "for p in 7 99 100 101 152;do printf \"%03d\\n\" $p;done|sort|tr \"\\n\" \" \""
            ).withCheck(false).execUnsafe().stdout().trim(),
            Matchers.equalTo("007 099 100 101 152")
        );
    }
}
