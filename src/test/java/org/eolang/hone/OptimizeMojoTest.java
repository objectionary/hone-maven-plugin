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
 * Test case for {@link OptimizeMojo}.
 *
 * @since 0.1.0
 * @todo #440:90min enable 'grep-in' tests.
 *  The following tests are disabled because they fail on Rultor:
 *  <a href="https://github.com/objectionary/hone-maven-plugin/pull/458">PR</a>
 *  However, all the tests pass.
 *  We should find a reason why the following tests fail on specific
 *  environment and fix them:
 *  - {@link OptimizeMojoTest#skipsOptimizationDueGrepInOption}
 *  - {@link OptimizeMojoTest#doesNotSkipOptimizationDueGrepInOption}
 *  When this tests are fixed, remove @Disabled annotation.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "PMD.AvoidDuplicateLiterals",
    "PMD.TooManyMethods",
    "PMD.UnitTestShouldIncludeAssert"
})
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
    @DisabledWithoutDocker
    void doesNotSkipOptimizationDueGrepInOption(@Mktmp final Path dir)
    throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/mapped/X.java")
                    .write(
                        """
                        package mapped;
                        import java.util.*;
                        import java.util.stream.*;

                        class X {
                            public static void main(String[] a) {
                              List<Integer> r = Arrays.asList(1,2,3,4).stream()
                                  .map(n->n*2)
                                  .filter(n->n%2==0)
                                  .map(n->n+1)
                                  .map(n->n+2)
                                  .collect(Collectors.toList());
                              System.out.println(r);
                            }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("test")
                    .goals("optimize")
                    .configuration()
                    .set("rules", "streams/*");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "phino should optimize (rewrite) exactly one file",
                    f.log().content(),
                    Matchers.allOf(
                        Matchers.containsString("Modified 1/1 X.phi"),
                        Matchers.containsString("Finished rewriting 1 file"),
                        Matchers.containsString("BUILD SUCCESS")
                    )
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @DisabledWithoutDocker
    void skipsOptimizationDueGrepInOption(@Mktmp final Path dir)
    throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/rand/X.java")
                    .write(
                        """
                        package rand;
                        import java.util.*;
                        import java.util.stream.*;

                        class X {
                            public static void main(String[] a) {
                              System.out.println("Don't optimize me, pls");
                            }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("test")
                    .goals("optimize")
                    .configuration()
                    .set("rules", "streams/*");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "phino should skip optimization if the default grep-in does not match any of the instructions",
                    f.log().content(),
                    Matchers.allOf(
                        Matchers.containsString("No grep-in match for 1/1 X.xmir"),
                        Matchers.containsString("Finished rewriting 1 file"),
                        Matchers.containsString("BUILD SUCCESS")
                    )
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutPhino
    void generatesStatisticsWithoutDocker(@Mktmp final Path home) throws IOException {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/statistics/Statistics.java")
                    .write(
                        """
                        package statistics;
                        class Statistics {
                            byte[] foo() {
                                return new byte[] {(byte) 0x01, (byte) 0x02};
                            }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/statistics/SntatisticsSecond.java")
                    .write(
                        """
                        package statistics;
                        class StatisticsSecond {
                            byte[] foo() {
                                return new byte[] {(byte) 0x01, (byte) 0x02};
                            }
                        }
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("optimize")
                    .configuration()
                    .set("debug", "true")
                    .set("alwaysWithDocker", "false")
                    .set("grepIn", ".*");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "a statistics file must be created and the content of the statistics file must be correct",
                    f.files().file("target/hone-statistics.csv").content(),
                    Matchers.allOf(
                        Matchers.containsString("ID,Before,After,Changed,LinesPerSec"),
                        Matchers.containsString(
                            String.format(
                                "%s,\"%s\",\"%s\",%d",
                                "1/2",
                                f.files()
                                    .file("target/hone/phi/statistics/Statistics.phi")
                                    .path(),
                                f.files()
                                    .file("target/hone/phi-optimized/statistics/Statistics.phi")
                                    .path(),
                                0
                            )
                        ),
                        Matchers.containsString(
                            String.format(
                                "%s,\"%s\",\"%s\",%d",
                                "2/2",
                                f.files().file(
                                    "target/hone/phi/statistics/StatisticsSecond.phi"
                                ).path(),
                                f.files().file(
                                    "target/hone/phi-optimized/statistics/StatisticsSecond.phi"
                                ).path(),
                                0
                            )
                        )
                    )
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutDocker
    void generatesStatisticsWithDocker(
        @Mktmp final Path home,
        @RandomImage final String image
    ) throws IOException {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/statistics/StatisticsFromDocker.java")
                    .write(
                        """
                        package statistics;
                        class StatisticsFromDocker {
                            byte[] foo() {
                                return new byte[] {(byte) 0x01, (byte) 0x02};
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
                    .set("debug", "true")
                    .set("alwaysWithDocker", "true")
                    .set("image", image)
                    .set("grepIn", ".*");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "a statistics file mus be created and the content of statistics file must be correct",
                    f.files().file("target/hone-statistics.csv").content(),
                    Matchers.allOf(
                        Matchers.containsString("ID,Before,After,Changed,LinesPerSec"),
                        Matchers.containsString(
                            String.format(
                                "%s,\"%s\",\"%s\",%d",
                                "1/1",
                                "/target/hone/phi/statistics/StatisticsFromDocker.phi",
                                "/target/hone/phi-optimized/statistics/StatisticsFromDocker.phi",
                                0
                            )
                        )
                    )
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutPhino
    void optimizesSimpleAppWithoutDocker(@Mktmp final Path home) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/foo/Bytes.java")
                    .write(
                        """
                        package foo;
                        class Bytes {
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
                        class BytesTest {
                            @Test
                            void worksAfterOptimization() {
                                Assertions.assertEquals(2, new Bytes().foo().length);
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
                    .goals("optimize")
                    .configuration()
                    .set("debug", "true")
                    .set("alwaysWithDocker", "false");
                f.exec("test");
                MatcherAssert.assertThat(
                    "optimized .xmir must be present",
                    f.files().file("target/hone/unphi/foo/Bytes.xmir").exists(),
                    Matchers.is(true)
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
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
                    .set("image", image)
                    .set("grepIn", ".*");
                f.exec("test");
                MatcherAssert.assertThat(
                    "optimized .xmir must be present",
                    f.files().file("target/hone/unphi/foo/Kid.xmir").exists(),
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
    @Timeout(180L)
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
    @Timeout(180L)
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
    @Timeout(180L)
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
                    .goals("build", "optimize")
                    .configuration()
                    .set("grepIn", ".*");
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("second")
                    .phase("process-classes")
                    .goals("optimize")
                    .configuration()
                    .set("grepIn", ".*");
                f.exec("test");
                MatcherAssert.assertThat(
                    "optimized .phi must be present",
                    f.files().file("target/hone/phi-optimized/Hello.phi").exists(),
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
    @SuppressWarnings("PMD.UnnecessaryLocalRule")
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
                    .set("alwaysWithDocker", "true")
                    .set("image", image)
                    .set("grepIn", ".*");
                f.exec("process-classes");
                final Path pre = f.files().file(
                    "target/hone/jeo-disassemble/com/sun/jna/Pointer.xmir"
                ).path();
                final Path xmir = f.files().file(
                    "target/hone/unphi/com/sun/jna/Pointer.xmir"
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
                    "target/hone/phi/com/sun/jna/Pointer.phi"
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
    @Timeout(180L)
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
                    .set("excludes", "/target/classes/foo/Excluded*")
                    .set("grepIn", ".*");
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "optimized IncludedClass.phi must be present",
                    f.files().file(
                        "target/hone/phi-optimized/foo/IncludedClass.phi"
                    ).exists(),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "ExcludedClass.phi must not be optimized",
                    f.files().file(
                        "target/hone/phi-optimized/foo/ExcludedClass.phi"
                    ).exists(),
                    Matchers.is(false)
                );
                MatcherAssert.assertThat(
                    "AnotherClass.phi must not be optimized (not included)",
                    f.files().file(
                        "target/hone/phi-optimized/bar/AnotherClass.phi"
                    ).exists(),
                    Matchers.is(false)
                );
            }
        );
    }

    @Test
    @Tag("deep")
    @Timeout(180L)
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
                    .set("threads", "1")
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
                    .set("image", image)
                    .set("grepIn", ".*");
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
    @Timeout(180L)
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
                    .set("image", image)
                    .set("grepIn", ".*");
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
    @Timeout(180L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    void optimizesWithSmallConsecutiveSteps(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/rules/first.yaml")
                    .write(
                        """
                        name: 321-to-567
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-74-10-00-00-00-00-00 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-81-B8-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/second.yaml")
                    .write(
                        """
                        name: 567-to-987
                        pattern: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-81-B8-00-00-00-00-00 ⟧ )'
                        result: 'Φ.org.eolang.bytes ( α0 ↦ ⟦ Δ ⤍ 40-8E-D8-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/main/java/Books.java")
                    .write(
                        """
                            class Books {
                                int countThem() {
                                    return 321;
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
                    .set(
                        "extra",
                        new String[] {
                            "src/rules/first.yaml",
                            "src/rules/second.yaml",
                        }
                    )
                    .set("image", image)
                    .set("grepIn", ".*");
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
    @Timeout(180L)
    @DisabledWithoutPhino
    void doesNothingWhenNoClasses(@Mktmp final Path home) throws Exception {
        new Farea(home).together(
            f -> {
                f.clean();
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize")
                    .configuration()
                    .set("debug", "true")
                    .set("alwaysWithDocker", "false");
                f.files()
                    .file("src/main/resources/dummy.txt")
                    .write(
                        "This populates target/classes/ without .class files"
                        .getBytes(StandardCharsets.UTF_8)
                    );
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    "the build must be successful, even if there are no classes",
                    f.log(),
                    RequisiteMatcher.SUCCESS
                );
            }
        );
    }

    @Test
    void matchesStandaloneMapByteSequenceInDefaultGrepIn() {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'map' byte sequence",
            Pattern.compile(OptimizeMojo.DEFAULT_GREP_IN).matcher(
                "<o as=\"α0\">6D-61-70</o>"
            ).find(),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneFilterByteSequenceInDefaultGrepIn() {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'filter' byte sequence",
            Pattern.compile(OptimizeMojo.DEFAULT_GREP_IN).matcher(
                "<o as=\"α0\">66-69-6C-74-65-72</o>"
            ).find(),
            Matchers.is(true)
        );
    }

    @Test
    void ignoresMapBytesEmbeddedInLongerStringInDefaultGrepIn() {
        MatcherAssert.assertThat(
            "default grep-in must not match 'map' bytes embedded in 'mapped/X' (see #449)",
            Pattern.compile(OptimizeMojo.DEFAULT_GREP_IN).matcher(
                "<o as=\"α0\">6D-61-70-70-65-64-2F-58</o>"
            ).find(),
            Matchers.is(false)
        );
    }

    @Test
    void ignoresFilterBytesEmbeddedInLongerStringInDefaultGrepIn() {
        MatcherAssert.assertThat(
            "default grep-in must not match 'filter' bytes embedded in 'filtered'",
            Pattern.compile(OptimizeMojo.DEFAULT_GREP_IN).matcher(
                "<o as=\"α0\">66-69-6C-74-65-72-65-64</o>"
            ).find(),
            Matchers.is(false)
        );
    }

    @Test
    void formatsWhoamiAsUidColonGid() {
        MatcherAssert.assertThat(
            "whoami must format the Docker --user value as 'uid:gid', not 'uid:euid' (see #492)",
            OptimizeMojo.whoami(
                new OptimizeMojoTest.FakeCLibrary(1000, 2000, 3000)
            ),
            Matchers.is("1000:3000")
        );
    }

    /**
     * Fixed-value stub of {@link OptimizeMojo.CLibrary} that returns
     * distinct values for uid, euid, and gid so callers can be checked
     * for picking up the right one.
     *
     * @since 0.6.0
     */
    private static final class FakeCLibrary implements OptimizeMojo.CLibrary {
        /**
         * Real user ID to return.
         */
        private final int uid;

        /**
         * Effective user ID to return.
         */
        private final int euid;

        /**
         * Group ID to return.
         */
        private final int gid;

        /**
         * Ctor.
         * @param ruid Real user ID
         * @param reuid Effective user ID
         * @param rgid Group ID
         */
        FakeCLibrary(final int ruid, final int reuid, final int rgid) {
            this.uid = ruid;
            this.euid = reuid;
            this.gid = rgid;
        }

        @Override
        public int getuid() {
            return this.uid;
        }

        @Override
        public int geteuid() {
            return this.euid;
        }

        @Override
        public int getgid() {
            return this.gid;
        }
    }
}
