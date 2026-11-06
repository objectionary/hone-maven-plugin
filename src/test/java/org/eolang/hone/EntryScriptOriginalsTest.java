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
 * Test case for the backup of the classes that {@code entry.sh} makes.
 *
 * @since 0.32.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptOriginalsTest {

    @Test
    void keepsOriginalsWhenRunAgainWithoutClean(@Mktmp final Path temp) throws IOException {
        MatcherAssert.assertThat(
            "a second run without clean must not take the output of hone for the original",
            EntryScriptOriginalsTest.saved(temp, "optimized"),
            Matchers.equalTo("original")
        );
    }

    @Test
    void takesClassCompiledAgain(@Mktmp final Path temp) throws IOException {
        MatcherAssert.assertThat(
            "a class compiled again after the first run must replace its old copy",
            EntryScriptOriginalsTest.saved(temp, "recompiled"),
            Matchers.equalTo("recompiled")
        );
    }

    private static String saved(final Path temp, final String second) throws IOException {
        final Path klass = temp.resolve("classes/foo/App.class");
        Files.createDirectories(klass.getParent());
        Files.write(klass, "original".getBytes(StandardCharsets.UTF_8));
        new Jaxec(
            "bash", "-c",
            String.join(
                ";",
                EntryScriptOriginalsTest.function("save_originals"),
                EntryScriptOriginalsTest.function("remember_assembled"),
                "save_originals",
                "printf optimized > \"${TARGET}/${CLASSES}/foo/App.class\"",
                "remember_assembled",
                String.format("printf %s > \"${TARGET}/${CLASSES}/foo/App.class\"", second),
                "save_originals"
            )
        ).withEnv("TARGET", temp.toString()).withEnv("CLASSES", "classes").exec();
        return new String(
            Files.readAllBytes(temp.resolve("classes-before-hone/foo/App.class")),
            StandardCharsets.UTF_8
        );
    }

    private static String function(final String name) throws IOException {
        final Matcher matcher = Pattern.compile(
            String.format("(?ms)^%s\\(\\) \\{.*?^\\}$", name)
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException(
                String.format("No function '%s' found in entry.sh", name)
            );
        }
        return matcher.group();
    }
}
