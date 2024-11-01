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
import com.yegor256.farea.RequisiteMatcher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link OptimizeMojo}.
 *
 * @since 0.1.0
 */
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class OptimizeMojoTest {

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
                    .goals("optimize")
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
    @ExtendWith(MayBeSlow.class)
    void optimizesSimpleApp(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/foo/AbstractParent.java")
                    .write(
                        """
                            package foo;
                            abstract class AbstractParent {
                                abstract byte[] foo();
                            }
                        """.getBytes()
                    );
                f.files()
                    .file("src/main/java/foo/Kid.java")
                    .write(
                        """
                        package foo;
                        class Kid extends AbstractParent {
                            @Override
                            byte[] foo() {
                                return new byte[] {(byte) 0x01, (byte) 0x02};
                            }
                        }
                        """.getBytes()
                    );
                f.files()
                    .file("src/test/java/foo/KidTest.java")
                    .write(
                        """
                        package foo;
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;
                        class KidTest {
                            @Test
                            void worksAfterOptimization() {
                                Assertions.assertEquals(2, new Kid().foo().length);
                            }
                        }
                        """.getBytes()
                    );
                f.dependencies()
                    .append("org.junit.jupiter", "junit-jupiter-engine", "5.10.2");
                f.dependencies()
                    .append("org.junit.jupiter", "junit-jupiter-params", "5.10.2");
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize", "rmi")
                    .configuration()
                    .set("image", image);
                f.exec("test");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
                MatcherAssert.assertThat(
                    "optimized .xmir must be present",
                    f.files().file("target/generated-sources/unphi/foo/Kid.xmir").exists(),
                    Matchers.is(true)
                );
            }
        );
    }

    @Test
    @ExtendWith(MayBeSlow.class)
    void optimizesTwice(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/Hello.java")
                    .write("class Hello { double foo() { return Math.sin(42); } }".getBytes());
                f.build()
                    .plugins()
                    .appendItself()
                    .configuration()
                    .set("image", image);
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("first")
                    .phase("process-classes")
                    .goals("build", "optimize");
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("second")
                    .phase("process-classes")
                    .goals("optimize", "rmi");
                f.exec("test");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
                MatcherAssert.assertThat(
                    "optimized .phi must be present",
                    f.files().file("target/generated-sources/phi-optimized/Hello.phi").exists(),
                    Matchers.is(true)
                );
            }
        );
    }

    @Test
    void printsHelp(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/Hello.java")
                    .write("class Hello { double foo() { return Math.sin(42); } }".getBytes());
                f.build()
                    .plugins()
                    .appendItself();
                f.exec("hone:help");
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
    }

    @Test
    @Disabled
    @ExtendWith(MayBeSlow.class)
    void optimizesJnaClasses(@Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                final String[] classes = {
                    "Pointer.class", "Library.class", "Native.class",
                    "Version.class", "Callback$UncaughtExceptionHandler.class",
                    "Native$7.class", "Native$1.class", "Native$5.class",
                    "FromNativeContext.class", "MethodResultContext.class",
                    "FunctionResultContext.class", "Platform.class",
                    "darwin-aarch64/libjnidispatch.jnilib",
                    "darwin-x86-64/libjnidispatch.jnilib",
                };
                for (final String cls : classes) {
                    f.files()
                        .file(String.format("target/classes/com/sun/jna/%s", cls))
                        .write(
                            Files.readAllBytes(
                                Paths.get(System.getProperty("target.directory"))
                                    .resolve("jna-classes")
                                    .resolve("com/sun/jna")
                                    .resolve(cls)
                            )
                        );
                }
                f.files()
                    .file("src/test/java/GoTest.java")
                    .write(
                        """
                        import com.sun.jna.Library;
                        import com.sun.jna.Native;
                        import com.sun.jna.Pointer;
                        import org.junit.jupiter.api.Test;
                        class GoTest {
                            interface Foo extends Library {
                                void bar();
                            }
                            @Test
                            void worksAfterOptimization() {
                                Native.load("c", Foo.class);
                            }
                        }
                        """.getBytes()
                    );
                f.dependencies()
                    .append("org.junit.jupiter", "junit-jupiter-engine", "5.10.2");
                f.dependencies()
                    .append("org.junit.jupiter", "junit-jupiter-params", "5.10.2");
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize", "rmi")
                    .configuration()
                    .set("image", image);
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
    @ExtendWith(MayBeSlow.class)
    void optimizesJustOneLargeJnaClass(@Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file("target/classes/com/sun/jna/Pointer.class")
                    .write(
                        Files.readAllBytes(
                            Paths.get(System.getProperty("target.directory"))
                                .resolve("jna-classes")
                                .resolve("com/sun/jna/Pointer.class")
                        )
                    );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize", "rmi")
                    .configuration()
                    .set("image", image);
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "optimized large .xmir must be present",
                    f.files().file(
                        "target/generated-sources/unphi/com/sun/jna/Pointer.xmir"
                    ).exists(),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "the build must be successful",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
    }
}
