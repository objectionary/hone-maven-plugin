/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the Maven call {@code hone-it.sh} makes inside
 * {@code apply_hone}, which ran with {@code -q} and so dropped the nested
 * jeo and phino output that explains a failed hone (see #1340).
 *
 * @since 0.74.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class HoneItVerboseTest {

    @Test
    void runsHoneWithoutQuietFlag(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "the hone call must not pass -q, or the reason of a failed hone never reaches the job log",
            HoneItVerboseTest.args(home),
            Matchers.not(Matchers.hasItem("-q"))
        );
    }

    @Test
    void keepsTheModuleGoalsAndPhinoVersion(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "the hone call must keep its quiet-independent flags, module, both goals and phino version",
            HoneItVerboseTest.args(home),
            Matchers.hasItems(
                "-ntp", "-B", "--batch-mode", "-f", home.resolve("mod").toString(),
                "org.eolang:hone-maven-plugin:1.0-SNAPSHOT:build",
                "org.eolang:hone-maven-plugin:1.0-SNAPSHOT:optimize",
                "-Dhone.rules=streams/*",
                "-Dhone.phino-version=0.1.0"
            )
        );
    }

    @Test
    void failsWhenTheHoneFails(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "a failing hone must still make apply_hone return non-zero",
            HoneItVerboseTest.hone(home).code(),
            Matchers.not(Matchers.is(0))
        );
    }

    private static List<String> args(final Path home) throws IOException {
        HoneItVerboseTest.hone(home);
        return Arrays.asList(
            Pattern.compile("\\s+").split(
                new String(
                    Files.readAllBytes(home.resolve("args")), StandardCharsets.UTF_8
                )
            )
        );
    }

    private static Result hone(final Path home) throws IOException {
        final Path bin = Files.createDirectories(home.resolve("bin"));
        final Path stub = bin.resolve("mvn");
        Files.write(
            stub,
            String.format(
                "#!/usr/bin/env bash%nprintf '%%s' \"$*\" > '%s'%nexit 1%n",
                home.resolve("args")
            ).getBytes(StandardCharsets.UTF_8)
        );
        Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwx------"));
        final Path module = home.resolve("mod");
        final byte[] bytes = new byte[8];
        bytes[6] = 0;
        bytes[7] = (byte) 0x3D;
        Files.write(
            Files.createDirectories(module.resolve("target/classes")).resolve("Foo.class"),
            bytes
        );
        return new Jaxec(
            "bash", "-c",
            String.join(
                String.format("%n"),
                "set -e -u",
                HoneItVerboseTest.function("supports_streams"),
                HoneItVerboseTest.function("apply_hone"),
                "version=1.0-SNAPSHOT",
                "phino=0.1.0",
                String.format("apply_hone '%s' || exit 3", module)
            )
        ).withEnv(
            "PATH", String.format("%s:%s", bin, System.getenv("PATH"))
        ).withCheck(false).execUnsafe();
    }

    private static String function(final String name) throws IOException {
        final String text = new String(
            Files.readAllBytes(Paths.get(".github/hone-it.sh")), StandardCharsets.UTF_8
        );
        final int start = text.indexOf(String.format("%s() {", name));
        return text.substring(start, text.indexOf(String.format("%n}"), start) + 2);
    }
}
