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
import java.nio.file.Path;
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
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
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
    void printsHelp(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file("src/main/java/Hello.java").write(
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
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutPhino
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void doesNothingWhenNoClasses(@Mktmp final Path home) throws Exception {
        new Farea(home).together(OptimizeMojoTest::runWithoutClasses);
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutDocker
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void transformsSimpleAppWithoutPhino(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoTest.runWithoutPhino(f, image));
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutDocker
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesExecutableJavaApp(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoTest.runExecutableApp(f, image));
    }

    @Test
    @Tag("deep")
    @Timeout(180L)
    @DisabledWithoutDocker
    @ExtendWith(MayBeSlow.class)
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesTwice(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoTest.runTwice(f, image));
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(180L)
    @DisabledWithoutPhino
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesSimpleAppWithoutDocker(@Mktmp final Path home) throws Exception {
        new Farea(home).together(OptimizeMojoTest::runSimpleAppWithoutDocker);
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(1200L)
    @DisabledWithoutDocker
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void optimizesSimpleApp(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        new Farea(home).together(f -> OptimizeMojoTest.runSimpleApp(f, image));
    }

    /**
     * Body of {@link #doesNothingWhenNoClasses}.
     * @param fea Fake Maven project
     * @throws IOException If the build fails to run
     */
    private static void runWithoutClasses(final Farea fea) throws IOException {
        fea.clean();
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("debug", "true")
            .set("alwaysWithDocker", "false");
        fea.files()
            .file("src/main/resources/dummy.txt").write(
                "This populates target/classes/ without .class files"
                    .getBytes(StandardCharsets.UTF_8)
            );
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            "the build must be successful, even if there are no classes",
            fea.log(),
            RequisiteMatcher.SUCCESS
        );
    }

    /**
     * Body of {@link #transformsSimpleAppWithoutPhino}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runWithoutPhino(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/foo/Foo.java").write(
                """
                package foo;
                class Foo {
                    int foo() {
                        return 33;
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/test/java/foo/FooTest.java").write(
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
            .set("rules", "33-to-42")
            .set("skipPhino", "true")
            .set("image", image);
        fea.exec("test");
        MatcherAssert.assertThat(
            "the build must be successful",
            fea.log(),
            RequisiteMatcher.SUCCESS
        );
    }

    /**
     * Body of {@link #optimizesExecutableJavaApp}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runExecutableApp(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/foo/Main.java").write(
                """
                    package foo;
                    public class Main {
                        public static void main(String[] args) {;
                            System.out.println("Hello, world!");
                        }
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
            .set("rules", "none")
            .set("image", image);
        fea.build()
            .plugins()
            .append("org.codehaus.mojo", "exec-maven-plugin", "3.5.0")
            .execution("default")
            .phase("process-classes")
            .goals("java")
            .configuration()
            .set("mainClass", "foo.Main");
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            "the message must be printed",
            fea.log().content(),
            Matchers.containsString("Hello, world!")
        );
    }

    /**
     * Body of {@link #optimizesTwice}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runTwice(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/Hello.java").write(
                String.join(
                    "",
                    "class Hello {",
                    "double foo() { return Math.sin(42); } }"
                ).getBytes(StandardCharsets.UTF_8)
            );
        fea.build()
            .plugins()
            .appendItself()
            .configuration()
            .set("image", image)
            .set("verbose", "true")
            .set("timeout", "15");
        fea.build()
            .plugins()
            .appendItself()
            .execution("first")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("grepIn", ".*");
        fea.build()
            .plugins()
            .appendItself()
            .execution("second")
            .phase("process-classes")
            .goals("optimize")
            .configuration()
            .set("grepIn", ".*");
        fea.exec("test");
        MatcherAssert.assertThat(
            "optimized .phi must be present",
            fea.files().file("target/hone/phi-optimized/Hello.phi").exists(),
            Matchers.is(true)
        );
    }

    /**
     * Body of {@link #optimizesSimpleAppWithoutDocker}.
     * @param fea Fake Maven project
     * @throws IOException If the build fails to run
     */
    private static void runSimpleAppWithoutDocker(final Farea fea) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/foo/Bytes.java").write(
                """
                package foo;
                class Bytes {
                    byte[] foo() {
                        return new byte[] {(byte) 0x01, (byte) 0x02};
                    }
                }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/test/java/foo/KidTest.java").write(
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
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-engine", "5.10.2");
        fea.dependencies()
            .append("org.junit.jupiter", "junit-jupiter-params", "5.10.2");
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("optimize")
            .configuration()
            .set("debug", "true")
            .set("alwaysWithDocker", "false");
        fea.exec("test");
        MatcherAssert.assertThat(
            "optimized .xmir must be present",
            fea.files().file("target/hone/unphi/foo/Bytes.xmir").exists(),
            Matchers.is(true)
        );
    }

    /**
     * Body of {@link #optimizesSimpleApp}.
     * @param fea Fake Maven project
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runSimpleApp(final Farea fea, final String image) throws IOException {
        fea.clean();
        fea.files()
            .file("src/main/java/foo/AbstractParent.java").write(
                """
                    package foo;
                    abstract class AbstractParent {
                        abstract byte[] foo();
                    }
                """.getBytes(StandardCharsets.UTF_8)
            );
        fea.files()
            .file("src/main/java/foo/Kid.java").write(
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
        fea.files()
            .file("src/test/java/foo/KidTest.java").write(
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
            .set("debug", "true")
            .set("alwaysWithDocker", "true")
            .set("image", image)
            .set("grepIn", ".*");
        fea.exec("test");
        MatcherAssert.assertThat(
            "optimized .xmir must be present",
            fea.files().file("target/hone/unphi/foo/Kid.xmir").exists(),
            Matchers.is(true)
        );
        MatcherAssert.assertThat(
            "the file with timings is created",
            fea.files().file("target/timings.csv").exists(),
            Matchers.is(true)
        );
    }
}
