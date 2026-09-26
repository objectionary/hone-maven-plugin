/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the Gradle snippet in {@code README.md}, which pinned the
 * never released {@code 0.0.0} because the version bump rewrote only
 * {@code <version>} elements (see #1073).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class GradleSnippetTest {

    @Test
    void pinsTheSameVersionAsTheMavenSnippet() throws IOException {
        final String readme = GradleSnippetTest.text("README.md");
        final Matcher maven = Pattern.compile("<version>([^<]+)</version>").matcher(readme);
        MatcherAssert.assertThat("the Maven snippet must pin a version", maven.find());
        final Matcher gradle = Pattern.compile("hone-maven-plugin:([^:]+):").matcher(readme);
        MatcherAssert.assertThat("the Gradle snippet must pin a version", gradle.find());
        MatcherAssert.assertThat(
            "the Gradle snippet must pin the version the Maven snippet pins, or it names a release that does not exist",
            gradle.group(1),
            Matchers.equalTo(maven.group(1))
        );
    }

    @Test
    void bumpsTheGradleSnippetToo() throws IOException {
        MatcherAssert.assertThat(
            "the up job must rewrite the Gradle snippet, or it stays at whatever it was released with",
            GradleSnippetTest.text(".github/workflows/up.yml"),
            Matchers.containsString("hone-maven-plugin:${latest}:")
        );
    }

    private static String text(final String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
