/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link Docker}.
 *
 * @since 0.1.0
 */
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
final class DockerTest {

    @Test
    @DisabledWithoutDocker
    void printsVersion() throws Exception {
        MatcherAssert.assertThat(
            "docker version must be printed",
            new Docker().exec("--version"),
            Matchers.is(Matchers.notNullValue())
        );
    }

    @Test
    void checksDockerPresence() {
        MatcherAssert.assertThat(
            "checks if docker is present",
            new Docker().available(),
            Matchers.either(Matchers.is(true)).or(Matchers.is(false))
        );
    }

    @Test
    @Timeout(120L)
    void givesUpOnWedgedDaemonQuickly(@Mktmp final Path temp) throws Exception {
        final Path docker = temp.resolve("docker");
        Files.write(
            docker,
            String.format("#!/usr/bin/env bash%nexec sleep 3600%n")
                .getBytes(StandardCharsets.UTF_8)
        );
        if (!docker.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake docker executable");
        }
        MatcherAssert.assertThat(
            "a daemon that never answers must be reported as not available",
            new Docker(false, docker.toString()).available(),
            Matchers.is(false)
        );
    }

    @Test
    @Timeout(30L)
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void killsOrphanedContainerWhenInterrupted(@Mktmp final Path temp) throws Exception {
        final Path log = temp.resolve("calls.log");
        final Path docker = DockerTest.fakeDocker(temp, log);
        final Thread thread = new Thread(
            () -> {
                try {
                    new Docker(false, docker.toString()).exec(
                        "run", "--rm", "alpine:latest", "sleep", "60"
                    );
                } catch (final IOException ex) {
                    Logger.debug(DockerTest.class, "exec threw: %s", ex.getMessage());
                }
            }
        );
        thread.start();
        while (!Files.exists(log)) {
            Thread.sleep(50L);
        }
        thread.interrupt();
        thread.join(TimeUnit.SECONDS.toMillis(20L));
        final String calls = new TextOf(log).asString();
        final Matcher named = Pattern.compile("--name (\\S+)").matcher(calls);
        MatcherAssert.assertThat(
            "the run command must be given a name",
            named.find(), Matchers.is(true)
        );
        MatcherAssert.assertThat(
            "the orphaned container must be killed by its name after an interrupt (see #1067)",
            calls,
            Matchers.containsString(String.format("kill %s", named.group(1)))
        );
    }

    @Test
    void makesNiceImageName(@RandomImage final String image) {
        MatcherAssert.assertThat(
            "random image name is in a proper format",
            image,
            Matchers.matchesPattern("[a-z-]+:[a-z][a-zA-Z0-9]+")
        );
    }

    private static Path fakeDocker(final Path temp, final Path log) throws IOException {
        final Path docker = temp.resolve("docker");
        Files.write(
            docker,
            String.format(
                """
                #!/usr/bin/env bash
                echo "$@" >> %s
                if [ "$1" = "run" ]; then
                exec sleep 3600
                fi
                exit 0
                """,
                log
            ).getBytes(StandardCharsets.UTF_8)
        );
        if (!docker.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake docker executable");
        }
        return docker;
    }
}
