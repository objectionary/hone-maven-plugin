/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the environment {@link OptimizeMojo} hands to the nested
 * {@code mvn} on the non-Docker path, where a JDWP agent inherited from the
 * outer build would fight for the debug port (see #1350).
 *
 * @since 0.75.0
 */
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "JTCOP.RulePresentTense"
})
final class OptimizeMojoNestedMavenTest {

    @Test
    void noMavenOpts() throws IOException {
        MatcherAssert.assertThat(
            "the nested mvn must not inherit MAVEN_OPTS, or a JDWP agent binds the port twice",
            OptimizeMojoNestedMavenTest.withoutDocker(),
            Matchers.containsString(".withEnv(\"MAVEN_OPTS\", \"\")")
        );
    }

    @Test
    void noMavenDebugOpts() throws IOException {
        MatcherAssert.assertThat(
            "the nested mvn must not inherit MAVEN_DEBUG_OPTS, or mvnDebug binds the port twice",
            OptimizeMojoNestedMavenTest.withoutDocker(),
            Matchers.containsString(".withEnv(\"MAVEN_DEBUG_OPTS\", \"\")")
        );
    }

    private static String withoutDocker() throws IOException {
        final String source = new String(
            Files.readAllBytes(
                Paths.get("src/main/java/org/eolang/hone/OptimizeMojo.java")
            ),
            StandardCharsets.UTF_8
        );
        return source.substring(source.indexOf("private void withoutDocker()"));
    }
}
