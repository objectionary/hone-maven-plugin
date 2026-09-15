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
 * Test case for the count {@link OptimizeMojo} prints through {@code entry.sh}.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoSavedCountTest {

    @Test
    void countsOnlyFilesNotDirectories(@Mktmp final Path temp) throws IOException {
        final Path saved = temp.resolve("classes-before-hone");
        final Path pkg = saved.resolve("org/eolang/deep");
        Files.createDirectories(pkg);
        for (final String name : new String[] {"One.class", "Two.class", "Three.class"}) {
            Files.write(pkg.resolve(name), "x".getBytes(StandardCharsets.UTF_8));
        }
        final Matcher matcher = Pattern.compile(
            "echo \"The binaries before hone are saved.*"
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No saving message found in entry.sh");
        }
        MatcherAssert.assertThat(
            "three class files in three nested directories must be counted as three",
            new Jaxec("bash", "-c", matcher.group())
                .withEnv("TARGET", temp.toString())
                .exec()
                .stdout(),
            Matchers.containsString("(3 files)")
        );
    }
}
