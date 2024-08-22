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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test case for {@link OptimizeMojo}.
 *
 * @since 0.1.0
 */
@EnabledOnOs(OS.LINUX)
final class OptimizeMojoTest {

    @Test
    void skipsOptimizationOnFlag(@TempDir final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("optimize")
                    .configuration()
                    .set("skip", true);
                f.exec("test");
                MatcherAssert.assertThat(
                    "the optimization step must be skipped",
                    f.log(),
                    Matchers.containsString("SUCCESS")
                );
            }
        );
    }

    @Test
    @Disabled
    void optimizesSimpleApp(@TempDir final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.files()
                    .file("src/main/java/Hello.java")
                    .write("class Hello { double foo() { return Math.sin(42); } }");
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("optimize")
                    .configuration()
                    .set("image", "hone:local");
                f.exec("test");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    Matchers.containsString("SUCCESS")
                );
            }
        );
    }

    @Test
    void printsHelp(@TempDir final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.files()
                    .file("src/main/java/Hello.java")
                    .write("class Hello { double foo() { return Math.sin(42); } }");
                f.build()
                    .plugins()
                    .appendItself();
                f.exec("hone:help");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    Matchers.containsString("Display help information on hone-maven-plugin")
                );
            }
        );
    }
}
