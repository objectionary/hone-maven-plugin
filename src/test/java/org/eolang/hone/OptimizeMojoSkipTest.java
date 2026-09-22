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
 * Test case for the skip check of {@code rewrite.sh}, as {@code entry.sh} runs it.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoSkipTest {

    @Test
    void skipsUnchangedClassOnSecondRun(@Mktmp final Path temp) throws IOException {
        MatcherAssert.assertThat(
            "a class that did not change since the previous run must be skipped",
            this.twice(temp, "printf '<xmir/>' > hone/jeo-disassemble/A.xmir"),
            Matchers.containsString("skipping transformation for 1/1")
        );
    }

    @Test
    void dropsOutputOfClassThatIsGone(@Mktmp final Path temp) throws IOException {
        this.twice(temp, "printf '<xmir/>' > hone/jeo-disassemble/B.xmir");
        MatcherAssert.assertThat(
            "the output of a class that is no longer disassembled must not survive",
            temp.resolve("hone/unphi/A.xmir").toFile().exists(),
            Matchers.is(false)
        );
    }

    private String twice(final Path home, final String between) throws IOException {
        final Path phino = home.resolve("phino");
        Files.write(
            phino,
            String.format(
                "#!/usr/bin/env bash%n[ \"${1}\" == '--version' ] && echo 0.0.1 && exit%ncat \"${@: -1}\"%n"
            ).getBytes(StandardCharsets.UTF_8)
        );
        if (!phino.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake phino executable");
        }
        Files.write(home.resolve("simple.phr"), "rule".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(home.resolve("hone/jeo-disassemble"));
        Files.write(
            home.resolve("hone/jeo-disassemble/A.xmir"),
            "<xmir/>".getBytes(StandardCharsets.UTF_8)
        );
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
        return new Jaxec(
            "bash", "-c",
            String.format(
                String.join(
                    String.format("%n"),
                    "set -e",
                    "cd %1$s",
                    "export PATH=%1$s:${PATH} TARGET=%1$s HONE_RULES=%1$s/simple.phr",
                    "export HONE_XMIR_IN=hone/jeo-disassemble HONE_XMIR_OUT=hone/unphi",
                    "export HONE_FROM=hone/phi HONE_TO=hone/phi-optimized HONE_THREADS=1",
                    "export HONE_MAX_CYCLES=1 HONE_MAX_DEPTH=1 HONE_SMALL_STEPS=false",
                    "export HONE_STATISTICS=false HONE_VERBOSE=false HONE_DEBUG=false HONE_TIMEOUT=5",
                    "bash %2$s > /dev/null",
                    "%3$smkdir -p hone/jeo-disassemble",
                    "%4$s",
                    "bash %2$s"
                ),
                home,
                Paths.get(
                    "src/main/resources/org/eolang/hone/scaffolding/rewrite.sh"
                ).toAbsolutePath(),
                matcher.group(),
                between
            )
        ).exec().stdout();
    }
}
