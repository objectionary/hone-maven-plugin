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
 * Test case for the backup of the classes, as {@code entry.sh} makes it.
 *
 * @since 0.31.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptBackupTest {

    @Test
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
            "rm -rf \"\\$\\{TARGET\\}/\\S*before-hone\"\\ncp -RL[^\\n]*\\n[^\\n]*\\n"
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No backup block in entry.sh");
        }
        for (final String classes : new String[] {"classes", "test-classes"}) {
            new Jaxec("bash", "-c", matcher.group())
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
}
