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
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(6000L)
    @DisabledWithoutDocker
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
                    .set("debug", "true")
                    .set("alwaysWithDocker", "true")
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
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(6000L)
    @DisabledWithoutDocker
    void transformsSimpleAppWithoutPhino(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/foo/Foo.java")
                    .write(
                        """
                        package foo;
                        class Foo {
                            int foo() {
                                return 33;
                            }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/test/java/foo/FooTest.java")
                    .write(
                        """
                        package foo;
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;
                        class FooTest {
                            @Test
                            void worksWithoutPhino() {
                                Assertions.assertEquals(33, new Foo().foo());
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
                    .set("rules", "33-to-42")
                    .set("skipPhino", "true")
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
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(6000L)
    @DisabledWithoutDocker
    void optimizesExecutableJavaApp(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/foo/Main.java")
                    .write(
                        """
                            package foo;
                            public class Main {
                                public static void main(String[] args) {;
                                    System.out.println("Hello, world!");
                                }
                            }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize")
                    .configuration()
                    .set("rules", "none")
                    .set("image", image);
                f.build()
                    .plugins()
                    .append("org.codehaus.mojo", "exec-maven-plugin", "3.5.0")
                    .execution("default")
                    .phase("process-classes")
                    .goals("java")
                    .configuration()
                    .set("mainClass", "foo.Main");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "the message must be printed",
                    f.log().content(),
                    Matchers.containsString("Hello, world!")
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @Timeout(6000L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
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
                    .set("image", image)
                    .set("verbose", "true")
                    .set("timeout", "15");
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
    @Tag("deep")
    @Timeout(6000L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    void optimizesWithIncludesAndExcludes(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/foo/IncludedClass.java")
                    .write(
                        """
                        package foo;
                        class IncludedClass {
                            int calculate() { return 42; }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/foo/ExcludedClass.java")
                    .write(
                        """
                        package foo;
                        class ExcludedClass {
                            int calculate() { return 100; }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/bar/AnotherClass.java")
                    .write(
                        """
                        package bar;
                        class AnotherClass {
                            int calculate() { return 200; }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize")
                    .configuration()
                    .set("image", image)
                    .set("includes", "/target/classes/foo/Included*")
                    .set("excludes", "/target/classes/foo/Excluded*");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "optimized IncludedClass.phi must be present",
                    f.files().file(
                        "target/generated-sources/phi-optimized/foo/IncludedClass.phi"
                    ).exists(),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "ExcludedClass.phi must not be optimized",
                    f.files().file(
                        "target/generated-sources/phi-optimized/foo/ExcludedClass.phi"
                    ).exists(),
                    Matchers.is(false)
                );
                MatcherAssert.assertThat(
                    "AnotherClass.phi must not be optimized (not included)",
                    f.files().file(
                        "target/generated-sources/phi-optimized/bar/AnotherClass.phi"
                    ).exists(),
                    Matchers.is(false)
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @Timeout(6000L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    void optimizesWithExtraRules(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/rules/first.yaml")
                    .write(
                        """
                        name: fifty-to-sixty
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-49-00-00-00-00-00-00 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-4E-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/second.yaml")
                    .write(
                        """
                        name: thirty-three-to-one
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-40-80-00-00-00-00-00 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 3F-F0-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/001.yaml")
                    .write(
                        """
                        name: hello-to-bye
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 68-65-6C-6C-6F ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 62-79-65 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/002.yaml")
                    .write(
                        """
                        name: mama-to-papa
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 6D-61-6D-61 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 70-61-70-61 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/Foo.java")
                    .write(
                        """
                            class Foo {
                                int bar() {
                                    return Math.abs(50) * 33
                                        + "hello".hashCode() + "mama".hashCode();
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
                            void worksAfterOptimizationWithExtraRule() {
                                Assertions.assertEquals(
                                    3531468,
                                    new Foo().bar()
                                );
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
                    .set("rules", "none")
                    .set("smallSteps", "true")
                    .set("maxDepth", "10")
                    .set(
                        "extra",
                        new String[] {
                            "src/rules/first.yaml",
                            "src/rules/second.yaml",
                            "src/rules/a-few",
                        }
                    )
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
    @Tag("deep")
    @Timeout(6000L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    void optimizesWithSmallSteps(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/rules/first.yaml")
                    .write(
                        """
                        name: fifty-to-sixty
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-49-00-00-00-00-00-00 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-4E-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/second.yaml")
                    .write(
                        """
                        name: thirty-three-to-one
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-40-80-00-00-00-00-00 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 3F-F0-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/001.yaml")
                    .write(
                        """
                        name: hello-to-bye
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 68-65-6C-6C-6F ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 62-79-65 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/002.yaml")
                    .write(
                        """
                        name: mama-to-papa
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 6D-61-6D-61 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 70-61-70-61 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/Smalls.java")
                    .write(
                        """
                            class Smalls {
                                int bar() {
                                    return Math.abs(50) * 33
                                        + "hello".hashCode() + "mama".hashCode();
                                }
                            }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/test/java/SmallsTest.java")
                    .write(
                        """
                        import org.junit.jupiter.api.Assertions;
                        import org.junit.jupiter.api.Test;
                        class SmallsTest {
                            @Test
                            void worksAfterOptimizationWithSmallSteps() {
                                Assertions.assertEquals(
                                    3531468,
                                    new Smalls().bar()
                                );
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
                    .set("rules", "none")
                    .set("smallSteps", "true")
                    .set("maxDepth", "40")
                    .set(
                        "extra",
                        new String[] {
                            "src/rules/first.yaml",
                            "src/rules/second.yaml",
                            "src/rules/a-few",
                        }
                    )
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
