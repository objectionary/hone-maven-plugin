/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the rule fingerprint of {@code rewrite.sh}, which every
 * self-invocation per file recomputed rule by rule, one {@code cksum} each,
 * although the rules and options are the same for the whole run (see #1208).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptStampOnceTest {

    @Test
    void fingerprintsEachRuleOncePerRun(@Mktmp final Path home) throws IOException {
        final Path source = Files.createDirectories(home.resolve("source"));
        Files.write(
            source.resolve("Foo.xmir"), "<xmir/>".getBytes(StandardCharsets.UTF_8)
        );
        final Path dir = Files.createDirectories(home.resolve("rules/streams"));
        final StringBuilder names = new StringBuilder();
        for (final String name : new String[] {"101-one.phr", "102-two.phr", "103-three.phr"}) {
            Files.write(dir.resolve(name), name.getBytes(StandardCharsets.UTF_8));
            names.append(dir.resolve(name)).append(' ');
        }
        final Path phino = home.resolve("phino");
        Files.write(
            phino,
            String.format("#!/usr/bin/env bash%nprintf '0.0.1\\n'%n")
                .getBytes(StandardCharsets.UTF_8)
        );
        RewriteScriptStampOnceTest.executable(phino);
        final Path log = home.resolve("cksum.log");
        final Path shim = home.resolve("cksum");
        Files.write(
            shim,
            String.format(
                "#!/usr/bin/env bash%nprintf 'called\\n' >> \"${HONE_CKSUM_LOG}\"%nexec %s \"$@\"%n",
                RewriteScriptStampOnceTest.real()
            ).getBytes(StandardCharsets.UTF_8)
        );
        RewriteScriptStampOnceTest.executable(shim);
        new Jaxec(
            "bash",
            Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                .toAbsolutePath().toString()
        ).withEnv("PATH", String.format("%s:%s", home, System.getenv("PATH")))
            .withEnv("TARGET", home.resolve("target").toString())
            .withEnv("HONE_XMIR_IN", source.toString())
            .withEnv("HONE_FROM", home.resolve("phi").toString())
            .withEnv("HONE_TO", home.resolve("optimized").toString())
            .withEnv("HONE_XMIR_OUT", home.resolve("out").toString())
            .withEnv("HONE_GREP_IN", "NO_MATCH")
            .withEnv("HONE_RULES", names.toString().trim())
            .withEnv("HONE_SMALL_STEPS", "false")
            .withEnv("HONE_MAX_CYCLES", "1")
            .withEnv("HONE_MAX_DEPTH", "1")
            .withEnv("HONE_THREADS", "1")
            .withEnv("HONE_TIMEOUT", "5")
            .withEnv("HONE_KILL_GRACE", "1")
            .withEnv("HONE_STATISTICS", "false")
            .withEnv("HONE_VERBOSE", "false")
            .withEnv("HONE_DEBUG", "false")
            .withEnv("HONE_VERSION", "0.1.0")
            .withEnv("PHINO_VERSION", "0.0.1")
            .withEnv("HONE_CKSUM_LOG", log.toString())
            .exec();
        MatcherAssert.assertThat(
            "cksum must run once per rule in the parent and once for the input seal, not once per rule for every self-invocation per file",
            Files.readAllLines(log, StandardCharsets.UTF_8),
            Matchers.hasSize(4)
        );
    }

    private static void executable(final Path file) {
        if (!file.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake binary executable");
        }
    }

    private static String real() {
        final StringTokenizer dirs = new StringTokenizer(
            System.getenv("PATH"), File.pathSeparator
        );
        String found = null;
        while (dirs.hasMoreTokens()) {
            final Path candidate = Paths.get(dirs.nextToken(), "cksum");
            if (Files.isExecutable(candidate)) {
                found = candidate.toString();
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Can't find the real cksum in PATH");
        }
        return found;
    }
}
