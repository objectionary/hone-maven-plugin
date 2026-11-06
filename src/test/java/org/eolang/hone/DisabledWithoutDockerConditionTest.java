/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link DisabledWithoutDockerCondition}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class DisabledWithoutDockerConditionTest {

    @Test
    @Timeout(120L)
    void disablesQuicklyWhenDaemonNeverAnswers(@Mktmp final Path temp) throws Exception {
        MatcherAssert.assertThat(
            "a daemon that never answers must disable the test without waiting",
            new DisabledWithoutDockerCondition(
                new Docker(
                    false,
                    DisabledWithoutDockerConditionTest.fake(temp, "exec sleep 3600")
                )
            ).evaluateExecutionCondition(null).isDisabled(),
            Matchers.is(true)
        );
    }

    @Test
    void enablesWhenDaemonAnswers(@Mktmp final Path temp) throws Exception {
        MatcherAssert.assertThat(
            "a daemon that answers must enable the test",
            new DisabledWithoutDockerCondition(
                new Docker(
                    false,
                    DisabledWithoutDockerConditionTest.fake(temp, "echo 28.0.1")
                )
            ).evaluateExecutionCondition(null).isDisabled(),
            Matchers.is(false)
        );
    }

    private static String fake(final Path home, final String body) throws IOException {
        final Path bin = home.resolve("docker");
        Files.write(
            bin,
            String.format("#!/bin/sh%n%s%n", body).getBytes(StandardCharsets.UTF_8)
        );
        if (!bin.toFile().setExecutable(true)) {
            throw new IOException(String.format("cannot make %s executable", bin));
        }
        return bin.toString();
    }
}
