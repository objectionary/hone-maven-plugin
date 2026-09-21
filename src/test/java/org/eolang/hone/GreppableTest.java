/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link Greppable}.
 *
 * @since 0.27.2
 */
final class GreppableTest {

    @Test
    void convertsSingleMethodToAnchoredHexPattern() {
        MatcherAssert.assertThat(
            "the 'map' method must become its anchored hex-byte form",
            new Greppable("map").toString(),
            Matchers.equalTo(">(6D-61-70)<")
        );
    }

    @Test
    void joinsSeveralMethodsAsAlternatives() {
        MatcherAssert.assertThat(
            "all method names must be joined into one anchored alternation",
            new Greppable("filter", "map", "mapMulti").toString(),
            Matchers.equalTo(">(66-69-6C-74-65-72|6D-61-70|6D-61-70-4D-75-6C-74-69)<")
        );
    }

    @Test
    void matchesExampleInGrepInJavadoc() throws Exception {
        final Matcher matcher = Pattern.compile(
            "Grep XMIR files.*?<pre>(.*?)</pre>", Pattern.DOTALL
        ).matcher(
            new String(
                Files.readAllBytes(Paths.get("src/main/java/org/eolang/hone/OptimizeMojo.java")),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No grep-in example found in OptimizeMojo.java");
        }
        MatcherAssert.assertThat(
            "the documented grep-in example must be the pattern that matches real XMIR",
            matcher.group(1).replace("&lt;", "<").replace("&gt;", ">"),
            Matchers.equalTo(new Greppable("filter", "map").toString())
        );
    }
}
