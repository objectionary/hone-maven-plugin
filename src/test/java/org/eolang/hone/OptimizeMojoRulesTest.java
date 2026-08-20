/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.io.IOException;
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
 * Test case for {@link OptimizeMojo}, against extra optimization rules,
 * includes/excludes, and small-steps modes.
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoRulesTest {

    @Test
    @Tag("deep")
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesJustOneLargeJnaClass(@Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        final String path = "com/sun/jna/Pointer.class";
        final Path bin = Paths.get(System.getProperty("target.directory"))
            .resolve("jna-classes")
            .resolve(path);
        new Farea(dir).together(
            f -> OptimizeMojoRulesTest.runOneLargeJnaClass(f, path, bin, image)
        );
    }

    @Test
    @Tag("deep")
    @Timeout(180L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesWithIncludesAndExcludes(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoRulesTest.runIncludesAndExcludes(f, image));
    }

    @Test
    @Tag("deep")
    @Timeout(180L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesWithExtraRules(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoRulesTest.runExtraRules(f, image));
    }

    @Test
    @Tag("deep")
    @Timeout(180L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesWithSmallSteps(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoRulesTest.runSmallSteps(f, image));
    }

    @Test
    @Tag("deep")
    @Timeout(180L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesWithSmallConsecutiveSteps(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> OptimizeMojoRulesTest.runSmallConsecutiveSteps(f, image)
        );
    }

    /**
     * Body of {@link #optimizesJustOneLargeJnaClass}.
     * @param fea Fake Maven project
     * @param path Path of the .class resource, relative to the project
     * @param bin Path of the .class resource on disk
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runOneLargeJnaClass(final Farea fea, final String path,
        final Path bin, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file(String.format("target/classes/%s", path))
            .write(Files.readAllBytes(bin));
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("alwaysWithDocker", "true")
            .set("image", image)
            .set("grepIn", ".*");
        fea.exec("process-classes");
        final Path pre = fea.files().file(
            "target/hone/jeo-disassemble/com/sun/jna/Pointer.xmir"
        ).path();
        final Path xmir = fea.files().file(
            "target/hone/unphi/com/sun/jna/Pointer.xmir"
        ).path();
        MatcherAssert.assertThat(
            "optimized large .xmir must be present",
            xmir.toFile().exists(),
            Matchers.is(true)
        );
        final Path target = Paths.get(System.getProperty("target.directory"));
        Files.copy(
            fea.files().file("target/timings.csv").path(),
            target.resolve("timings.csv"),
            StandardCopyOption.REPLACE_EXISTING
        );
        final String timing = fea.files().file("target/hone-timings.csv").content();
        final Matcher mtc = Pattern.compile(
            String.format("optimize,(?<msec>[0-9]+)%n")
        ).matcher(timing);
        MatcherAssert.assertThat(
            String.format("timing must exist in [%s]", timing),
            mtc.find(), Matchers.is(true)
        );
        final Path phi = fea.files().file(
            "target/hone/phi/com/sun/jna/Pointer.phi"
        ).path();
        final long msec = Long.parseLong(mtc.group("msec"));
        Files.write(
            target.resolve("jna-summary.txt"),
            String.join(
                System.lineSeparator(),
                String.format("Input: %s", path),
                Logger.format(
                    "Size of .class: %[size]s (%1$s bytes)",
                    bin.toFile().length()
                ),
                Logger.format(
                    "Size of .xmir after disassemble: %[size]s (%1$s bytes, %d lines)",
                    pre.toFile().length(),
                    Files.readString(pre, StandardCharsets.UTF_8)
                        .split(System.lineSeparator()).length
                ),
                Logger.format(
                    "Size of .phi: %[size]s (%1$s bytes, %d lines)",
                    phi.toFile().length(),
                    Files.readString(phi, StandardCharsets.UTF_8)
                        .split(System.lineSeparator()).length
                ),
                Logger.format(
                    "Size of .xmir after unphi: %[size]s (%1$s bytes, %d lines)",
                    xmir.toFile().length(),
                    Files.readString(xmir, StandardCharsets.UTF_8)
                        .split(System.lineSeparator()).length
                ),
                Logger.format(
                    "Optimization time: %[ms]s (%d ms)",
                    msec, msec
                )
            ).getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Body of {@link #optimizesWithIncludesAndExcludes}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runIncludesAndExcludes(final Farea fea, final String image)
        throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/foo/IncludedClass.java").write(
                """
                package foo;
                class IncludedClass {
                    int calculate() { return 42; }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/foo/ExcludedClass.java").write(
                """
                package foo;
                class ExcludedClass {
                    int calculate() { return 100; }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/bar/AnotherClass.java").write(
                """
                package bar;
                class AnotherClass {
                    int calculate() { return 200; }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("image", image)
            .set("includes", "/target/classes/foo/Included*")
            .set("excludes", "/target/classes/foo/Excluded*")
            .set("grepIn", ".*");
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            "optimized IncludedClass.phi must be present",
            fea.files().file(
                "target/hone/phi-optimized/foo/IncludedClass.phi"
            ).exists(),
            Matchers.is(true)
        );
        MatcherAssert.assertThat(
            "ExcludedClass.phi must not be optimized",
            fea.files().file(
                "target/hone/phi-optimized/foo/ExcludedClass.phi"
            ).exists(),
            Matchers.is(false)
        );
        MatcherAssert.assertThat(
            "AnotherClass.phi must not be optimized (not included)",
            fea.files().file(
                "target/hone/phi-optimized/bar/AnotherClass.phi"
            ).exists(),
            Matchers.is(false)
        );
    }

    /**
     * Body of {@link #optimizesWithExtraRules}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runExtraRules(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/rules/first.yaml").write(
                """
                name: fifty-to-sixty
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-49-00-00-00-00-00-00 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-4E-00-00-00-00-00-00 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/second.yaml").write(
                """
                name: thirty-three-to-one
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-40-80-00-00-00-00-00 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 3F-F0-00-00-00-00-00-00 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/a-few/001.yaml").write(
                """
                name: hello-to-bye
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 68-65-6C-6C-6F ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 62-79-65 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/a-few/002.yaml").write(
                """
                name: mama-to-papa
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 6D-61-6D-61 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 70-61-70-61 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/Foo.java").write(
                """
                    class Foo {
                        int bar() {
                            return Math.abs(50) * 33
                                + "hello".hashCode() + "mama".hashCode();
                        }
                    }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/test/java/FooTest.java").write(
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
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-engine", "5.10.2");
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-params", "5.10.2");
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("rules", "none")
            .set("threads", "1")
            .set("smallSteps", "true")
            .set("maxDepth", "10").set(
                "extra",
                new String[] {
                    "src/rules/first.yaml",
                    "src/rules/second.yaml",
                    "src/rules/a-few",
                }
            )
            .set("image", image)
            .set("grepIn", ".*");
        fea.exec("test");
        MatcherAssert.assertThat(
            "the build must be successful",
            fea.log(),
            RequisiteMatcher.SUCCESS
        );
    }

    /**
     * Body of {@link #optimizesWithSmallSteps}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runSmallSteps(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/rules/first.yaml").write(
                """
                name: fifty-to-sixty
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-49-00-00-00-00-00-00 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-4E-00-00-00-00-00-00 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/second.yaml").write(
                """
                name: thirty-three-to-one
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-40-80-00-00-00-00-00 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 3F-F0-00-00-00-00-00-00 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/a-few/001.yaml").write(
                """
                name: hello-to-bye
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 68-65-6C-6C-6F ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 62-79-65 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/a-few/002.yaml").write(
                """
                name: mama-to-papa
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 6D-61-6D-61 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 70-61-70-61 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/Smalls.java").write(
                """
                    class Smalls {
                        int bar() {
                            return Math.abs(50) * 33
                                + "hello".hashCode() + "mama".hashCode();
                        }
                    }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/test/java/SmallsTest.java").write(
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
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-engine", "5.10.2");
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-params", "5.10.2");
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("rules", "none")
            .set("smallSteps", "true")
            .set("maxDepth", "40").set(
                "extra",
                new String[] {
                    "src/rules/first.yaml",
                    "src/rules/second.yaml",
                    "src/rules/a-few",
                }
            )
            .set("image", image)
            .set("grepIn", ".*");
        fea.exec("test");
        MatcherAssert.assertThat(
            "the build must be successful",
            fea.log(),
            RequisiteMatcher.SUCCESS
        );
    }

    /**
     * Body of {@link #optimizesWithSmallConsecutiveSteps}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runSmallConsecutiveSteps(final Farea fea, final String image)
        throws IOException {
        fea.clean();
        fea.files()
            .file("src/rules/first.yaml").write(
                """
                name: 321-to-567
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-74-10-00-00-00-00-00 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-81-B8-00-00-00-00-00 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/rules/second.yaml").write(
                """
                name: 567-to-987
                pattern: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-81-B8-00-00-00-00-00 ⟧ )'
                result: 'Φ.bytes ( data ↦ ⟦ Δ ⤍ 40-8E-D8-00-00-00-00-00 ⟧ )'
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/Books.java").write(
                """
                    class Books {
                        int countThem() {
                            return 321;
                        }
                    }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/test/java/SmallsTest.java").write(
                """
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                class BooksTest {
                    @Test
                    void worksAfterOptimizationWithSmallConsecutiveSteps() {
                        Assertions.assertEquals(
                            987,
                            new Books().countThem()
                        );
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-engine", "5.10.2");
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-params", "5.10.2");
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("rules", "none")
            .set("smallSteps", "true").set(
                "extra",
                new String[] {
                    "src/rules/first.yaml",
                    "src/rules/second.yaml",
                }
            )
            .set("image", image)
            .set("grepIn", ".*");
        fea.exec("test");
        MatcherAssert.assertThat(
            "the build must be successful",
            fea.log(),
            RequisiteMatcher.SUCCESS
        );
    }
}
