/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Test case for {@link OptimizeMojo}.
 *
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
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
    @Timeout(6000L)
    @DisabledWithoutDocker
    @Disabled
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
                        """.getBytes(StandardCharsets.UTF_8)
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
                        """.getBytes(StandardCharsets.UTF_8)
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
                        """.getBytes(StandardCharsets.UTF_8)
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
                    .goals("build", "optimize")
                    .configuration()
                    .set("image", image);
                f.exec("test");
                MatcherAssert.assertThat(
                    "optimized .xmir must be present",
                    f.files().file("target/generated-sources/unphi/foo/Kid.xmir").exists(),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "the file with timings is created",
                    f.files().file("target/timings.csv").exists(),
                    Matchers.is(true)
                );
            }
        );
    }

    @Test
    @Timeout(6000L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @Disabled
    void optimizesTwice(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/Hello.java")
                    .write(
                        String.join(
                            "",
                            "class Hello {",
                            "double foo() { return Math.sin(42); } }"
                        ).getBytes(StandardCharsets.UTF_8)
                    );
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
                    .goals("optimize");
                f.exec("test");
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
                    .write(
                        String.join(
                            "",
                            "class Hello {",
                            "double foo() { return Math.sin(42); } }"
                        ).getBytes(StandardCharsets.UTF_8)
                    );
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
    @Tag("deep")
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    void optimizesJustOneLargeJnaClass(@Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        final String path = "com/sun/jna/Pointer.class";
        final Path bin = Paths.get(System.getProperty("target.directory"))
            .resolve("jna-classes")
            .resolve(path);
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file(String.format("target/classes/%s", path))
                    .write(Files.readAllBytes(bin));
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize")
                    .configuration()
                    .set("image", image);
                f.exec("process-classes");
                final Path pre = f.files().file(
                    "target/generated-sources/jeo-disassemble/com/sun/jna/Pointer.xmir"
                ).path();
                final Path xmir = f.files().file(
                    "target/generated-sources/unphi/com/sun/jna/Pointer.xmir"
                ).path();
                MatcherAssert.assertThat(
                    "optimized large .xmir must be present",
                    xmir.toFile().exists(),
                    Matchers.is(true)
                );
                final Path target = Paths.get(System.getProperty("target.directory"));
                Files.copy(
                    f.files().file("target/timings.csv").path(),
                    target.resolve("timings.csv"),
                    StandardCopyOption.REPLACE_EXISTING
                );
                final String timing = f.files().file("target/hone-timings.csv").content();
                final Matcher mtc = Pattern.compile("optimize,(?<msec>[0-9]+)\n").matcher(timing);
                MatcherAssert.assertThat(
                    String.format("timing must exist in [%s]", timing),
                    mtc.find(), Matchers.is(true)
                );
                final Path phi = f.files().file(
                    "target/generated-sources/phi/com/sun/jna/Pointer.phi"
                ).path();
                final long msec = Long.parseLong(mtc.group("msec"));
                Files.write(
                    target.resolve("jna-summary.txt"),
                    String.join(
                        "\n",
                        String.format("Input: %s", path),
                        Logger.format(
                            "Size of .class: %[size]s (%1$s bytes)",
                            bin.toFile().length()
                        ),
                        Logger.format(
                            "Size of .xmir after disassemble: %[size]s (%1$s bytes, %d lines)",
                            pre.toFile().length(),
                            Files.readString(pre, StandardCharsets.UTF_8).split("\n").length
                        ),
                        Logger.format(
                            "Size of .phi: %[size]s (%1$s bytes, %d lines)",
                            phi.toFile().length(),
                            Files.readString(phi, StandardCharsets.UTF_8).split("\n").length
                        ),
                        Logger.format(
                            "Size of .xmir after unphi: %[size]s (%1$s bytes, %d lines)",
                            xmir.toFile().length(),
                            Files.readString(xmir, StandardCharsets.UTF_8).split("\n").length
                        ),
                        Logger.format(
                            "Optimization time: %[ms]s (%d ms)",
                            msec, msec
                        )
                    ).getBytes(StandardCharsets.UTF_8)
                );
            }
        );
    }

    @Test
    @Timeout(6000L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @Disabled
    void optimizesWithExtraRule(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/rules/simple.yaml")
                    .write(
                        """
                        -   name: simple
                            description: 'change 7777 int to 5555 int'
                            pattern: |
                                Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 00-00-00-00-00-00-1E-61 ⟧ )
                            result: |
                                Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-B5-B3-00-00-00-15-B3 ⟧ )
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/Foo.java")
                    .write(
                        """
                            class Foo {
                                int bar() {
                                    return 7777;
                                }
                            }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/test/java/FooTest.java")
                    .write(
                        """
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;
                        class FooTest {
                            @Test
                            void worksAfterOptimization() {
                                Assertions.assertEquals(5555, new Foo().bar());
                            }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
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
                    .goals("build", "optimize")
                    .configuration()
                    .set("extra", new String[] {"src/rules/simple.yaml"})
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
}
