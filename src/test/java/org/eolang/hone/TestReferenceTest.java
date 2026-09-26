/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the {@code Class#method} references in the documents and the
 * workflows, which kept naming a class a test method had been moved out of,
 * so a workflow selector matched nothing and the job died (see #1117).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class TestReferenceTest {

    /**
     * A reference to a test method, as the documents and the workflows write it.
     */
    private static final Pattern REFERENCE = Pattern.compile(
        "(\\w+Test)#(\\w+)"
    );

    @Test
    void namesTheClassEveryReferencedMethodLivesIn() throws IOException {
        final Collection<String> broken = new LinkedList<>();
        for (final Path source : TestReferenceTest.sources()) {
            final Matcher found = TestReferenceTest.REFERENCE.matcher(
                new String(Files.readAllBytes(source), StandardCharsets.UTF_8)
            );
            while (found.find()) {
                final Path test = Paths.get(
                    "src/test/java/org/eolang/hone", String.format("%s.java", found.group(1))
                );
                if (!Files.exists(test)
                    || !new String(
                        Files.readAllBytes(test), StandardCharsets.UTF_8
                    ).contains(found.group(2))) {
                    broken.add(String.format("%s in %s", found.group(), source));
                }
            }
        }
        MatcherAssert.assertThat(
            "every referenced method must live in the class the reference names, or a selector built from it matches nothing",
            broken,
            Matchers.empty()
        );
    }

    private static Collection<Path> sources() throws IOException {
        final Collection<Path> sources = new LinkedList<>();
        sources.add(Paths.get("CLAUDE.md"));
        try (Stream<Path> found = Files.list(Paths.get(".github/workflows"))) {
            found.forEach(sources::add);
        }
        return sources;
    }
}
