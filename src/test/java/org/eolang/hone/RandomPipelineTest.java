/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link RandomPipeline}.
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
final class RandomPipelineTest {

    /**
     * How many random classes the compilation check generates.
     */
    private static final int TOTAL = 24;

    @Test
    void repeatsItselfOnTheSameSeed() {
        MatcherAssert.assertThat(
            "the same seed must produce the very same class, or a failure found once is lost",
            new RandomPipeline(42L).java("same", "X"),
            Matchers.equalTo(new RandomPipeline(42L).java("same", "X"))
        );
    }

    @Test
    void walksElsewhereOnAnotherSeed() {
        MatcherAssert.assertThat(
            "two seeds must not walk the grammar the same way",
            new RandomPipeline(1L).java("other", "X"),
            Matchers.not(Matchers.equalTo(new RandomPipeline(2L).java("other", "X")))
        );
    }

    @Test
    void countsElementsOnlyWhenTheTerminalWalksThemAll() {
        final List<String> broken = new ArrayList<>(0);
        for (int seed = 0; seed < RandomPipelineTest.TOTAL; ++seed) {
            final String java = new RandomPipeline(seed).java("counted", "X");
            if (java.contains(".peek(") && java.contains(".count()")) {
                broken.add(java);
            }
        }
        MatcherAssert.assertThat(
            "a peek must not sit in front of count(), which may skip the traversal",
            broken,
            Matchers.empty()
        );
    }

    @Test
    void generatesSourceThatCompiles(@Mktmp final Path dir) throws IOException {
        final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        final List<String> files = new ArrayList<>(0);
        for (int seed = 0; seed < RandomPipelineTest.TOTAL; ++seed) {
            final String name = String.format("P%04d", seed);
            final Path java = dir.resolve(String.format("%s.java", name));
            Files.write(
                java,
                new RandomPipeline(seed).java("compiled", name).getBytes(StandardCharsets.UTF_8)
            );
            files.add(java.toString());
        }
        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        final List<String> args = new ArrayList<>(0);
        args.add("-d");
        args.add(dir.toString());
        args.addAll(files);
        javac.run(null, null, errors, args.toArray(new String[0]));
        MatcherAssert.assertThat(
            "every generated class must compile, or the grammar is not typed",
            new String(errors.toByteArray(), StandardCharsets.UTF_8),
            Matchers.emptyString()
        );
    }
}
