/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void buildsImageWhenParallelIsMissing(@Mktmp final Path dir) throws Exception {
        final Path log = dir.resolve("docker.log");
        final BuildMojo mojo = new BuildMojo(
            AbstractMojoTest.fakeTool(dir, "phino", new BuildMojo().phino(), 0).toString(),
            AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
            dir.resolve("missing-parallel").toString(),
            AbstractMojoTest.recordingDocker(dir, log).toString()
        );
        mojo.target = dir.resolve("target").toFile();
        mojo.session = AbstractMojoTest.mavenSession();
        mojo.image = "local-only/hone:test";
        mojo.execute();
        MatcherAssert.assertThat(
            "build must invoke docker when parallel is missing and threads=0",
            new String(Files.readAllBytes(log), StandardCharsets.UTF_8),
            Matchers.containsString("build ")
        );
    }

    @Test
    void skipsBuildWhenParallelIsMissingAndThreadsIsOne(@Mktmp final Path dir) throws Exception {
        final Path log = dir.resolve("docker.log");
        final BuildMojo mojo = new BuildMojo(
            AbstractMojoTest.fakeTool(dir, "phino", new BuildMojo().phino(), 0).toString(),
            AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
            dir.resolve("missing-parallel").toString(),
            AbstractMojoTest.recordingDocker(dir, log).toString()
        );
        mojo.target = dir.resolve("target").toFile();
        mojo.session = AbstractMojoTest.mavenSession();
        mojo.image = "local-only/hone:test";
        mojo.threads = 1;
        mojo.execute();
        MatcherAssert.assertThat(
            "build must skip docker when parallel is missing and hone.threads=1",
            Files.exists(log),
            Matchers.is(false)
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
                    .set("alwaysWithDocker", "true")
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
        new Farea(dir).together(
            f -> {
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("generate-resources")
                    .goals("build")
                    .configuration()
                    .set("alwaysWithDocker", "true")
                    .set("image", image);
                f.exec("generate-resources");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
        MatcherAssert.assertThat(
            "docker tree command must execute successfully",
            new Jaxec(
                "docker", "run", "--rm",
                "--env", "TARGET=/tmp",
                "--entrypoint", "/bin/bash",
                image,
                "-c", "tree /hone"
            ).withCheck(false).execUnsafe().stdout(),
            Matchers.allOf(
                Matchers.containsString("entry.sh"),
                Matchers.containsString("rewrite.sh"),
                Matchers.containsString("none.yml"),
                Matchers.containsString("701-static-lambda-to-invokedynamic.phr")
            )
        );
    }
}
