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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the backup of the classes, as {@code entry.sh} makes it.
 *
 * @since 0.31.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptBackupTest {

    @Test
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void keepsBackupOfEachClassesDirectory(@Mktmp final Path temp) throws IOException {
        Files.createDirectories(temp.resolve("classes/foo"));
        Files.write(
            temp.resolve("classes/foo/Main.class"),
            "main".getBytes(StandardCharsets.UTF_8)
        );
        Files.createDirectories(temp.resolve("test-classes/foo"));
        Files.write(
            temp.resolve("test-classes/foo/MainTest.class"),
            "test".getBytes(StandardCharsets.UTF_8)
        );
        final Matcher matcher = Pattern.compile(
            "save_originals\\(\\) \\{.*?\\n\\}", Pattern.DOTALL
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No save_originals function in entry.sh");
        }
        for (final String classes : new String[] {"classes", "test-classes"}) {
            new Jaxec("bash", "-c", String.format("%s%nsave_originals", matcher.group()))
                .withEnv("TARGET", temp.toString())
                .withEnv("CLASSES", classes)
                .exec();
        }
        MatcherAssert.assertThat(
            "the backup of the main classes must survive the execution with 'test-classes' (see #1344)",
            temp.resolve("classes-before-hone/foo/Main.class").toFile().exists(),
            Matchers.is(true)
        );
        MatcherAssert.assertThat(
            "the backup of the test classes must be its own directory (see #1344)",
            temp.resolve("test-classes-before-hone/foo/MainTest.class").toFile().exists(),
            Matchers.is(true)
        );
    }

    @Test
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void rejectsClassesOutsideTarget(@Mktmp final Path temp) throws IOException {
        final Path shared = Files.createDirectories(temp.resolve("shared"));
        Files.write(
            shared.resolve("Keep.class"),
            "keep".getBytes(StandardCharsets.UTF_8)
        );
        final Result result = new Jaxec("bash", EntryScriptBackupTest.script())
            .withEnv("TARGET", temp.toString())
            .withEnv("EO_CACHE", temp.toString())
            .withEnv("CLASSES", "../shared")
            .withCheck(false)
            .exec();
        MatcherAssert.assertThat(
            "a classes directory outside the target must be refused (see #1344)",
            result.code(),
            Matchers.is(1)
        );
        MatcherAssert.assertThat(
            "the refusal must name the variable to fix",
            result.stdout(),
            Matchers.containsString("CLASSES")
        );
        MatcherAssert.assertThat(
            "nothing outside the target may be removed",
            shared.resolve("Keep.class").toFile().exists(),
            Matchers.is(true)
        );
    }

    private static String script() {
        return Paths.get(
            "src/main/resources/org/eolang/hone/scaffolding/entry.sh"
        ).toAbsolutePath().toString();
    }
}
