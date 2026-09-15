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
 * Test case for the way {@link BuildMojo} copies phino in its Dockerfile.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class BuildMojoPhinoCopyTest {

    @Test
    void doesNotPipeFindIntoACommandThatExitsEarly() throws IOException {
        MatcherAssert.assertThat(
            "a find piped into head dies of SIGPIPE and pipefail makes the build fail",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/Dockerfile")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.not(Matchers.containsString("-name phino -type f 2>/dev/null | head"))
        );
    }

    @Test
    void showsThatSuchAPipeReallyFails() throws IOException {
        MatcherAssert.assertThat(
            "the pipe this Dockerfile must avoid really does exit with 141",
            new Jaxec(
                "bash", "-c",
                "set -o pipefail -e; find / -name '*' 2>/dev/null | head -1 >/dev/null; echo ok"
            ).withCheck(false).execUnsafe().code(),
            Matchers.equalTo(141)
        );
    }
}
