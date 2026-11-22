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
 * Test case for the warm-up build {@code hone-it.sh} runs before it applies
 * hone. Maven remembers the remote metadata of every repository together with
 * the timestamp of its last lookup and, on a failed lookup, a marker that
 * stops it from retrying for a day, so one transient fetch failure poisons
 * every later Maven process sharing the cache: plugins whose dependencies use
 * version ranges, like the BouncyCastle jars behind git-commit-id-maven-plugin
 * of mybatis-3, then fail with "No versions available for ... within specified
 * range" long after the network problem is gone (see #1340). The warm-up
 * build must therefore clear that bookkeeping before it runs.
 *
 * @since 0.74.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class HoneItWarmUpTest {

    @Test
    void purgesMetadataStateButKeepsArtifacts(@Mktmp final Path home) throws IOException {
        final Path repo = Files.createDirectories(
            home.resolve(".m2").resolve("repository").resolve("org").resolve("example")
        );
        final String[] poisoned = {
            "maven-metadata-central.xml",
            "maven-metadata-central.xml.sha1",
            "resolver-status.properties",
            "dep-1.0.jar.lastUpdated",
        };
        for (final String name : poisoned) {
            Files.write(repo.resolve(name), new byte[1]);
        }
        Files.write(repo.resolve("dep-1.0.jar"), new byte[1]);
        Files.write(repo.resolve("_remote.repositories"), new byte[1]);
        HoneItWarmUpTest.warmUp(home);
        MatcherAssert.assertThat(
            "every stale metadata bookkeeping file must be deleted before the warm-up build, or one failed lookup poisons the whole coverage job",
            Arrays.stream(poisoned)
                .filter(name -> repo.resolve(name).toFile().exists())
                .count(),
            Matchers.is(0L)
        );
        HoneItWarmUpTest.expectKept(repo.resolve("dep-1.0.jar"));
        HoneItWarmUpTest.expectKept(repo.resolve("_remote.repositories"));
    }

    @Test
    void runsMavenOnTheCheckedOutProject(@Mktmp final Path home) throws IOException {
        HoneItWarmUpTest.warmUp(home);
        MatcherAssert.assertThat(
            "the warm-up build must stay a 'mvn test' call on the checked-out project with the configured flags",
            HoneItWarmUpTest.args(home),
            Matchers.hasItems(
                "-ntp", "-B", "-q", "-f", home.resolve("proj").toString(), "test"
            )
        );
    }

    @Test
    void reportsTheBuildResultBack(@Mktmp final Path home) throws IOException {
        HoneItWarmUpTest.warmUp(home);
        MatcherAssert.assertThat(
            "a failing warm-up build must surface its non-zero exit code through rc",
            Integer.parseInt(
                new String(
                    Files.readAllBytes(home.resolve("rc")), StandardCharsets.UTF_8
                ).trim()
            ),
            Matchers.not(Matchers.is(0))
        );
    }

    private static void expectKept(final Path file) {
        MatcherAssert.assertThat(
            String.format(
                "'%s' is not metadata bookkeeping and must stay untouched",
                file
            ),
            file.toFile().exists(),
            Matchers.is(true)
        );
    }

    private static List<String> args(final Path home) throws IOException {
        HoneItWarmUpTest.warmUp(home);
        return Arrays.asList(
            Pattern.compile("\\s+").split(
                new String(
                    Files.readAllBytes(home.resolve("args")), StandardCharsets.UTF_8
                )
            )
        );
    }

    private static Result warmUp(final Path home) throws IOException {
        final Path bin = Files.createDirectories(home.resolve("bin"));
        final Path mvn = bin.resolve("mvn");
        Files.write(
            mvn,
            String.format(
                "#!/usr/bin/env bash%nprintf '%%s' \"$*\" > '%s'%nexit 1%n",
                home.resolve("args")
            ).getBytes(StandardCharsets.UTF_8)
        );
        Files.setPosixFilePermissions(mvn, PosixFilePermissions.fromString("rwx------"));
        final Path timeout = bin.resolve("timeout");
        Files.write(
            timeout,
            String.format(
                "#!/usr/bin/env bash%nshift%nexec \"$@\"%n"
            ).getBytes(StandardCharsets.UTF_8)
        );
        Files.setPosixFilePermissions(timeout, PosixFilePermissions.fromString("rwx------"));
        Files.createDirectories(home.resolve("proj"));
        return new Jaxec(
            "bash", "-c",
            String.join(
                String.format("%n"),
                "set -e -u",
                HoneItWarmUpTest.function("fresh_metadata"),
                HoneItWarmUpTest.function("warm_up_build"),
                "budget=1200",
                "flags=(-ntp -B -q --batch-mode)",
                String.format("dir='%s'", home.resolve("proj")),
                String.format("HOME='%s'", home),
                "warm_up_build",
                String.format("printf '%%s' \"$rc\" > '%s'", home.resolve("rc"))
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
