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
 * Test case for the {@code atomic_write} function of {@code rewrite.sh}.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoAtomicWriteTest {

    @Test
    void keepsExitCodeOfFailedCommand(@Mktmp final Path temp) throws IOException {
        final Matcher matcher = Pattern.compile(
            "function atomic_write \\{.*?\\n}\\n", Pattern.DOTALL
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No atomic_write function found in rewrite.sh");
        }
        MatcherAssert.assertThat(
            "the exit code of the failed command must be returned as is",
            new Jaxec(
                "bash", "-c",
                String.format(
                    "%scode=0%natomic_write %s bash -c 'exit 7' || code=$?%necho \"${code}\"",
                    matcher.group(), temp.resolve("out.txt")
                )
            ).exec().stdout().trim(),
            Matchers.equalTo("7")
        );
    }
}
