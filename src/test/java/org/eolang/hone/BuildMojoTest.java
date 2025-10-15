/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.cactoos.io.InputOf;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Test case for {@link BuildMojo}.
 *
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
final class BuildMojoTest {

    @Test
    void skipsOptimizationOnFlag(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build")
                    .configuration()
                    .set("skip", true);
                f.exec("test");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @DisabledWithoutDocker
    void buildsDockerImage(@Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        new Farea(dir).together(
            f -> {
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("generate-resources")
                    .goals("build")
                    .configuration()
                    .set("image", image);
                f.exec("generate-resources");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @DisabledWithoutDocker
    void buildsImageAndVerifiesFileStructure(@Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        Stream.of("a", "b").map(s -> s.length()).map((Integer x) -> x + 1);
        new Farea(dir).together(
            f -> {
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("generate-resources")
                    .goals("build")
                    .configuration()
                    .set("image", image);
                f.exec("generate-resources");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
        final ProcessBuilder bldr = new ProcessBuilder(
            "docker", "run", "--rm",
            "--env", "TARGET=/tmp",
            "--entrypoint", "/bin/bash",
            image,
            "-c", "tree /hone"
        );
        bldr.redirectErrorStream(true);
        bldr.redirectOutput(ProcessBuilder.Redirect.PIPE);
        final Process process = bldr.start();
        process.waitFor();
        MatcherAssert.assertThat(
            "docker tree command must execute successfully",
            new TextOf(new InputOf(process.getInputStream())).asString(),
            Matchers.allOf(
                Matchers.containsString("entry.sh"),
                Matchers.containsString("rewrite.sh"),
                Matchers.containsString("none.yml"),
                Matchers.containsString("701-static-lambda-to-invokedynamic.phr")
            )
        );
    }
}
