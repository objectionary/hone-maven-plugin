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
 * Test case for {@link PullMojo}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class PullMojoTest {

    @Test
    @Tag("deep")
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void pullsDockerImage(@Mktmp final Path dir) throws Exception {
        final String image = "hello-world";
        PullMojoTest.forget(image);
        MatcherAssert.assertThat(
            "the image must be absent before the build",
            PullMojoTest.present(image),
            Matchers.is(false)
        );
        new Farea(dir).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution()
                    .phase("process-classes")
                    .goals("pull")
                    .configuration()
                    .set("alwaysWithDocker", "true")
                    .set("image", image);
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
        MatcherAssert.assertThat(
            "the image must be pulled by the goal",
            PullMojoTest.present(image),
            Matchers.is(true)
        );
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
        if (PullMojoTest.present(image)) {
            new Docker().exec("rmi", "--force", image);
        }
    }
}
