/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.Jaxec;
import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.Result;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cactoos.io.ResourceOf;
import org.cactoos.iterable.Mapped;
import org.cactoos.text.IoCheckedText;
import org.cactoos.text.TextOf;
import org.eolang.jucs.ClasspathSource;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Test case for {@link OptimizeMojo}.
 * @since 0.1.0
 * @todo #440:90min enable 'grep-in' tests.
 *  The following test is disabled because it fails on Rultor:
 *  <a href="https://github.com/objectionary/hone-maven-plugin/pull/458">PR</a>
 *  However, all the tests pass locally.
 *  We should find a reason why the following test fails on specific
 *  environment and fix it:
 *  - {@link OptimizeMojoTest#optimizesAsSpecifiedInYamlPack}
 *  When this test is fixed, remove @Disabled annotation.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "PMD.AvoidDuplicateLiterals",
    "PMD.TooManyMethods",
    "PMD.GodClass"
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

    @ParameterizedTest
    @Tag("deep")
    @Timeout(180L)
    @ExtendWith(MayBeSlow.class)
    @DisabledWithoutDocker
    @ClasspathSource(value = "org/eolang/hone/optimize", glob = "**.yml")
    @SuppressWarnings("unchecked")
    void optimizesAsSpecifiedInYamlPack(final String yaml, @Mktmp final Path dir,
        @RandomImage final String image) throws Exception {
        final Map<String, Object> pack = new Yaml().load(yaml);
        final String code = (String) pack.get("java");
        final Matcher pkg = Pattern.compile("package\\s+([\\w.]+)\\s*;").matcher(code);
        if (!pkg.find()) {
            throw new IllegalStateException(
                String.format("YAML pack lacks 'package' declaration in 'java' field: %s", yaml)
            );
        }
        final Matcher cls = Pattern.compile("class\\s+(\\w+)").matcher(code);
        if (!cls.find()) {
            throw new IllegalStateException(
                String.format("YAML pack lacks 'class' declaration in 'java' field: %s", yaml)
            );
        }
        final String slashed = pkg.group(1).replace('.', '/');
        final String klass = cls.group(1);
        final String path = String.format("src/main/java/%s/%s.java", slashed, klass);
        new Farea(dir).together(
            f -> {
                f.clean();
                f.files()
                    .file(path)
                    .write(code.getBytes(StandardCharsets.UTF_8));
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("build", "optimize")
                    .configuration()
                    .set("rules", "streams/*")
                    .set("image", image);
                f.build()
                    .plugins()
                    .append("org.codehaus.mojo", "exec-maven-plugin", "3.5.0")
                    .execution("default")
                    .phase("process-classes")
                    .goals("java")
                    .configuration()
                    .set("mainClass", String.format("%s.%s", pkg.group(1), klass));
                f.exec("process-classes");
                MatcherAssert.assertThat(
                    String.format(
                        "log lacks one of the expected substrings for pack at %s",
                        path
                    ),
                    f.log().content(),
                    Matchers.allOf(
                        new Mapped<>(Matchers::containsString, (List<String>) pack.get("log"))
                    )
                );
                OptimizeMojoTest.assertOpcodes(
                    f.files().file(
                        String.format("target/classes-before-hone/%s/%s.class", slashed, klass)
                    ).path(),
                    (Map<String, Integer>) pack.get("before"),
                    "before"
                );
                OptimizeMojoTest.assertOpcodes(
                    f.files().file(
                        String.format("target/classes/%s/%s.class", slashed, klass)
                    ).path(),
                    (Map<String, Integer>) pack.get("after"),
                    "after"
                );
            }
        );
    }

    @ParameterizedTest
    @Tag("deep")
    @Timeout(60L)
    @DisabledWithoutPhino
    @MethodSource("phinoPacks")
    @SuppressWarnings({"unchecked", "PMD.UnitTestContainsTooManyAsserts"})
    void appliesPhinoRulesAsSpecifiedInYamlPack(final Path yml,
        @Mktmp final Path dir) throws Exception {
        final Map<String, Object> pack = new Yaml().load(
            Files.readString(yml, StandardCharsets.UTF_8)
        );
        final List<String> rules = (List<String>) pack.get("rules");
        if (rules == null || rules.isEmpty()) {
            throw new IllegalStateException(
                String.format("YAML pack '%s' must declare at least one 'rules' entry", yml)
            );
        }
        final String raw = (String) pack.get("input");
        if (raw == null) {
            throw new IllegalStateException(
                String.format("YAML pack '%s' must declare an 'input' field", yml)
            );
        }
        final List<String> expected = (List<String>) pack.get("expected");
        if (expected == null || expected.isEmpty()) {
            throw new IllegalStateException(
                String.format("YAML pack '%s' must declare at least one 'expected' pattern", yml)
            );
        }
        final Path root = Paths.get(System.getProperty("target.directory")).getParent();
        final String trimmed = raw.replaceFirst("^\\s+", "");
        final String content;
        if (trimmed.startsWith("{") || trimmed.startsWith("Φ")) {
            content = raw;
        } else {
            content = String.format("{%s}", raw);
        }
        final Path input = dir.resolve("input.phi");
        Files.write(
            input,
            String.format("%s%n", content).getBytes(StandardCharsets.UTF_8)
        );
        final List<String> cmd = new ArrayList<>();
        cmd.add("phino");
        cmd.add("rewrite");
        cmd.add("--sweet");
        for (final String rule : rules) {
            final Path resolved = root.resolve(rule);
            if (!Files.exists(resolved)) {
                throw new IllegalStateException(
                    String.format(
                        "rule file '%s' from pack '%s' does not exist", resolved, yml
                    )
                );
            }
            cmd.add(String.format("--rule=%s", resolved));
        }
        cmd.add(input.toString());
        final Result rewrite = new Jaxec(cmd.toArray(new String[0]))
            .withCheck(false).execUnsafe();
        MatcherAssert.assertThat(
            String.format(
                "phino rewrite cannot fail for pack '%s', stderr: %s",
                yml, rewrite.stderr()
            ),
            rewrite.code(),
            Matchers.is(0)
        );
        final Path output = dir.resolve("output.phi");
        Files.write(output, rewrite.stdout().getBytes(StandardCharsets.UTF_8));
        for (final String pattern : expected) {
            final Result match = new Jaxec(
                "phino", "match", "--pattern", pattern, output.toString()
            ).withCheck(false).execUnsafe();
            MatcherAssert.assertThat(
                String.format(
                    "expected pattern from pack '%s' cannot fail to match%n  pattern: %s%n  actual: %s",
                    yml, pattern, rewrite.stdout()
                ),
                match.code(),
                Matchers.is(0)
            );
        }
    }

    /**
     * Source method for {@link #appliesPhinoRulesAsSpecifiedInYamlPack}.
     * Walks {@code src/test/phino/} and yields every {@code .yml} pack.
     * @return Stream of YAML pack paths in alphabetical order
     * @throws IOException If the directory cannot be listed
     */
    static Stream<Path> phinoPacks() throws IOException {
        final List<Path> packs;
        try (Stream<Path> entries = Files.list(
            Paths.get(System.getProperty("target.directory"))
                .getParent().resolve("src").resolve("test").resolve("phino")
        )) {
            packs = entries
                .filter(p -> p.toString().endsWith(".yml"))
                .sorted()
                .collect(Collectors.toList());
        }
        return packs.stream();
    }

    /**
     * Assert that opcode counts in a compiled class match the YAML
     * expectations. A zero value asserts the opcode is absent.
     * @param klass Path to the .class file
     * @param expected Expected opcode → count map, or null to skip
     * @param stage Either "before" or "after" — used in the failure message
     * @throws IOException If the class file cannot be read
     */
    private static void assertOpcodes(final Path klass,
        final Map<String, Integer> expected, final String stage) throws IOException {
        if (expected == null) {
            return;
        }
        final Map<String, Integer> actual = new ClassOpcodes(klass).counts();
        final List<org.hamcrest.Matcher<? super Map<? extends String, ? extends Integer>>> checks =
            new ArrayList<>(expected.size());
        for (final Map.Entry<String, Integer> entry : expected.entrySet()) {
            checks.add(OptimizeMojoTest.opcodeMatcher(entry.getKey(), entry.getValue()));
        }
        MatcherAssert.assertThat(
            String.format(
                "%s-stage opcode counts in %s do not match YAML '%s' expectations, actual: %s",
                stage, klass, stage, actual
            ),
            actual,
            Matchers.allOf(checks)
        );
    }

    /**
     * Build a single opcode matcher: presence with an exact count when
     * the expected value is positive, absence when it is zero.
     * @param opcode Opcode mnemonic
     * @param count Expected occurrences (zero asserts absence)
     * @return A Hamcrest matcher over the opcode tally
     */
    private static org.hamcrest.Matcher<? super Map<? extends String, ? extends Integer>>
        opcodeMatcher(final String opcode, final Integer count) {
        final org.hamcrest.Matcher<? super Map<? extends String, ? extends Integer>> ret;
        if (count == 0) {
            ret = Matchers.not(Matchers.hasKey(opcode));
        } else {
            ret = Matchers.hasEntry(opcode, count);
        }
        return ret;
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
    @Timeout(600L)
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
    @Timeout(600L)
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
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-49-00-00-00-00-00-00 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-4E-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/second.yaml")
                    .write(
                        """
                        name: thirty-three-to-one
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-40-80-00-00-00-00-00 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 3F-F0-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/001.yaml")
                    .write(
                        """
                        name: hello-to-bye
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 68-65-6C-6C-6F ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 62-79-65 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/002.yaml")
                    .write(
                        """
                        name: mama-to-papa
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 6D-61-6D-61 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 70-61-70-61 ⟧ )'
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
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-49-00-00-00-00-00-00 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-4E-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/second.yaml")
                    .write(
                        """
                        name: thirty-three-to-one
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-40-80-00-00-00-00-00 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 3F-F0-00-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/001.yaml")
                    .write(
                        """
                        name: hello-to-bye
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 68-65-6C-6C-6F ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 62-79-65 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/a-few/002.yaml")
                    .write(
                        """
                        name: mama-to-papa
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 6D-61-6D-61 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 70-61-70-61 ⟧ )'
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
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-74-10-00-00-00-00-00 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-81-B8-00-00-00-00-00 ⟧ )'
                        """.getBytes(StandardCharsets.UTF_8)
                    );
                f.files()
                    .file("src/rules/second.yaml")
                    .write(
                        """
                        name: 567-to-987
                        pattern: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-81-B8-00-00-00-00-00 ⟧ )'
                        result: 'Φ.bytes ( α0 ↦ ⟦ Δ ⤍ 40-8E-D8-00-00-00-00-00 ⟧ )'
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
    void matchesStandaloneMapByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'map' byte sequence",
            OptimizeMojoTest.grepInMatches(dir, "<o as=\"α0\">6D-61-70</o>"),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneFilterByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'filter' byte sequence",
            OptimizeMojoTest.grepInMatches(dir, "<o as=\"α0\">66-69-6C-74-65-72</o>"),
            Matchers.is(true)
        );
    }

    @Test
    void ignoresMapBytesEmbeddedInLongerStringInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must not match 'map' bytes embedded in 'mapped/X' (see #449)",
            OptimizeMojoTest.grepInMatches(dir, "<o as=\"α0\">6D-61-70-70-65-64-2F-58</o>"),
            Matchers.is(false)
        );
    }

    @Test
    void ignoresFilterBytesEmbeddedInLongerStringInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must not match 'filter' bytes embedded in 'filtered'",
            OptimizeMojoTest.grepInMatches(dir, "<o as=\"α0\">66-69-6C-74-65-72-65-64</o>"),
            Matchers.is(false)
        );
    }

    @Test
    void rejectsMapToIntByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must not match the 'mapToInt' byte sequence (see #671)",
            OptimizeMojoTest.grepInMatches(dir, "<o as=\"α0\">6D-61-70-54-6F-49-6E-74</o>"),
            Matchers.is(false)
        );
    }

    /**
     * Runs the default {@code grep-in} pattern through the very tool that
     * consumes it in {@code rewrite.sh} ({@code grep -E}), so the pattern's
     * dialect is validated against its real consumer rather than against
     * {@link java.util.regex.Pattern} (see #671).
     * @param dir Temporary directory to hold the sample file
     * @param content The line of XMIR to grep through
     * @return TRUE if {@code grep -E} finds a match
     * @throws IOException If the sample file cannot be written
     */
    private static boolean grepInMatches(final Path dir, final String content)
        throws IOException {
        final Path file = dir.resolve("sample.xmir");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return new Jaxec(
            "grep", "-qE", OptimizeMojo.DEFAULT_GREP_IN, file.toString()
        ).withCheck(false).execUnsafe().code() == 0;
    }

    @Test
    void hasZeroDefaultThreadsInPluginDescriptor() throws Exception {
        MatcherAssert.assertThat(
            "default 'threads' parameter must be 0 so that all CPUs are used by default (see #500)",
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("META-INF/maven/plugin.xml")
                )
            ).asString(),
            Matchers.containsString(
                "<threads implementation=\"int\" default-value=\"0\">"
            )
        );
    }

    @Test
    void doesNotPlaceParallelTmpdirOnHostMountedVolume() throws Exception {
        MatcherAssert.assertThat(
            "rewrite.sh must not point parallel --tmpdir at the host-mounted ${TARGET} volume, since virtiofs (Docker Desktop on macOS) makes fstat fail with ENOENT on deleted-but-open files, which breaks parallel's grouped output and triggers 'Cant dup STDOUT: No such file or directory' (see #506)",
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("org/eolang/hone/scaffolding/rewrite.sh")
                )
            ).asString(),
            Matchers.not(Matchers.containsString("--tmpdir=${PARALLEL_HOME}"))
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
