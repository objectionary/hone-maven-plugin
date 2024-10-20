/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.hone;

import com.yegor256.farea.Farea;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test case for {@link BuildMojo}.
 *
 * @since 0.1.0
 */
final class BuildMojoTest {

    @Test
    void skipsOptimizationOnFlag(@TempDir final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
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
                    "the Docker image building step must be skipped",
                    f.log(),
                    Matchers.allOf(
                        Matchers.containsString("BUILD SUCCESS"),
                        Matchers.not(Matchers.containsString("BUILD FAILURE"))
                    )
                );
            }
        );
    }

    @Test
    void buildsDockerImage(@TempDir final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("generate-resources")
                    .goals("build", "rmi")
                    .configuration()
                    .set("image", Float.toHexString(new SecureRandom().nextFloat()));
                f.exec("generate-resources");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    Matchers.allOf(
                        Matchers.containsString("BUILD SUCCESS"),
                        Matchers.not(Matchers.containsString("BUILD FAILURE"))
                    )
                );
            }
        );
    }
}
