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
