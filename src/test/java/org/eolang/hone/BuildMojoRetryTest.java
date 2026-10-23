/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.cactoos.Scalar;
import org.cactoos.scalar.Retry;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test case for the retry policy of {@link BuildMojo}, which tried a failing
 * {@code docker build} three times with no delay, so a Dockerfile that can
 * never build was built three times in a row (see #1003).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class BuildMojoRetryTest {

    @Test
    void waitsBetweenAttempts() {
        MatcherAssert.assertThat(
            "a retry with no delay only repeats the same failure at once, so the wait must be real",
            BuildMojo.DELAY.compareTo(Duration.ofSeconds(1L)),
            Matchers.greaterThanOrEqualTo(0)
        );
    }

    @Test
    void triesAsManyTimesAsItSays() {
        final AtomicInteger count = new AtomicInteger();
        Assertions.assertThrows(
            Exception.class,
            () -> new Retry<>(
                (Scalar<Object>) () -> {
                    count.incrementAndGet();
                    throw new IOException("docker is unhappy");
                },
                BuildMojo.ATTEMPTS,
                Duration.ZERO
            ).value(),
            "a failure that never clears must reach the caller"
        );
        MatcherAssert.assertThat(
            "the build must be attempted exactly as many times as the policy says",
            count.get(),
            Matchers.equalTo(BuildMojo.ATTEMPTS)
        );
    }
}
