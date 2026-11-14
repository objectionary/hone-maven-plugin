/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link RmiMojo}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class RmiMojoTest {

    @Test
    void recordsTimingEvenWhenImageIsNotRemoved(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution()
                    .phase("process-classes")
                    .goals("rmi")
                    .configuration()
                    .set("image", "hone-absent-image:0")
                    .set("alwaysWithDocker", "true");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "the rmi goal must record its time in hone-timings.csv",
                    f.files().file("target/hone-timings.csv").content(),
                    Matchers.containsString("\"rmi\"")
                );
            }
        );
    }

    @Test
    void removesImageWhenParallelIsMissing(@Mktmp final Path dir) throws Exception {
        final Path log = dir.resolve("docker.log");
        final RmiMojo mojo = new RmiMojo(
            AbstractMojoTest.fakeTool(dir, "phino", new RmiMojo().phino(), 0).toString(),
            AbstractMojoTest.fakeTool(dir, "mvn", "Apache Maven 3.9", 0).toString(),
            dir.resolve("missing-parallel").toString(),
            AbstractMojoTest.recordingDocker(dir, log).toString()
        );
        mojo.target = dir.resolve("target").toFile();
        mojo.session = AbstractMojoTest.mavenSession();
        mojo.image = "local-only/hone:test";
        mojo.execute();
        MatcherAssert.assertThat(
            "rmi must invoke docker when parallel is missing and threads=0",
            new String(Files.readAllBytes(log), StandardCharsets.UTF_8),
            Matchers.containsString("rmi --no-prune local-only/hone:test")
        );
    }

    @Test
    void skipsRmiWhenParallelIsMissingAndThreadsIsOne(@Mktmp final Path dir) throws Exception {
        final Path log = dir.resolve("docker.log");
        final RmiMojo mojo = new RmiMojo(
            AbstractMojoTest.fakeTool(dir, "phino", new RmiMojo().phino(), 0).toString(),
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
            "rmi must skip docker when parallel is missing and hone.threads=1",
            Files.exists(log),
            Matchers.is(false)
        );
    }

    @Test
    @Tag("deep")
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void pullsAndRemovesDockerImage(@Mktmp final Path dir) throws Exception {
        final String image = "hello-world";
        RmiMojoTest.forget(image);
        MatcherAssert.assertThat(
            "the image must be absent before the build",
            RmiMojoTest.present(image),
            Matchers.is(false)
        );
        new Farea(dir).together(
            f -> {
                f.clean();
                RmiMojoTest.pull(f, image);
                RmiMojoTest.rmi(f, image);
                f.exec("process-classes");
                RmiMojoTest.assertSuccess(f);
                MatcherAssert.assertThat(
                    "the image must be pulled by the goal",
                    RmiMojoTest.present(image),
                    Matchers.is(true)
                );
                f.exec("prepare-package");
                RmiMojoTest.assertSuccess(f);
            }
        );
        MatcherAssert.assertThat(
            "the image must be removed by the goal",
            RmiMojoTest.present(image),
            Matchers.is(false)
        );
    }

    private static void assertSuccess(final Farea farea) throws IOException {
        MatcherAssert.assertThat(
            "the build must be successful",
            farea.log(),
            RequisiteMatcher.SUCCESS
        );
    }

    private static void pull(final Farea farea, final String image)
        throws IOException {
        farea.build()
            .plugins()
            .appendItself()
            .execution("pull")
            .phase("process-classes")
            .goals("pull")
            .configuration()
            .set("alwaysWithDocker", "true")
            .set("image", image);
    }

    private static void rmi(final Farea farea, final String image)
        throws IOException {
        farea.build()
            .plugins()
            .appendItself()
            .execution("rmi")
            .phase("prepare-package")
            .goals("rmi")
            .configuration()
            .set("alwaysWithDocker", "true")
            .set("image", image);
    }

    private static boolean present(final String image) {
        boolean yes = true;
        try {
            new Docker().exec("image", "inspect", image);
        } catch (final IOException ex) {
            yes = false;
        }
        return yes;
    }

    private static void forget(final String image) throws IOException {
        if (RmiMojoTest.present(image)) {
            new Docker().exec("rmi", "--force", image);
        }
    }
}
