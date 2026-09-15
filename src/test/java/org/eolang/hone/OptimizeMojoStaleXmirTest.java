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
 * Test case for the directories {@link OptimizeMojo} clears through {@code entry.sh}.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoStaleXmirTest {

    @Test
    void clearsTheXmirOfThePreviousRun(@Mktmp final Path temp) throws IOException {
        final Path stale = temp.resolve("hone/jeo-disassemble/Gone.xmir");
        Files.createDirectories(stale.getParent());
        Files.write(stale, "x".getBytes(StandardCharsets.UTF_8));
        final Matcher matcher = Pattern.compile("rm -rf \"\\$\\{TARGET\\}/hone/[^\\n]*\\n").matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No clearing of the pipeline directories in entry.sh");
        }
        new Jaxec("bash", "-c", matcher.group()).withEnv("TARGET", temp.toString()).exec();
        MatcherAssert.assertThat(
            "XMIR of a class that is no longer disassembled cannot survive into the next run",
            stale.toFile().exists(),
            Matchers.is(false)
        );
    }
}
