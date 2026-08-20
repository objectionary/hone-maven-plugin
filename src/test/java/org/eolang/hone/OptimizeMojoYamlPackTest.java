/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.Result;
import com.yegor256.farea.Farea;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cactoos.iterable.Mapped;
import org.eolang.jucs.ClasspathSource;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

/**
 * Test case for {@link OptimizeMojo}, against YAML packs of
 * {@code before}/{@code after} bytecode expectations.
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoYamlPackTest {

    /**
     * Temporary directory for the fake Maven project.
     */
    @Mktmp
    private Path directory;

    @ParameterizedTest
    @Tag("deep")
    @Timeout(180L)
    @ExtendWith(MayBeSlow.class)
    @DisabledWithoutDocker
    @ClasspathSource(value = "org/eolang/hone/optimize", glob = "**.yml")
    @SuppressWarnings({
        "unchecked", "PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"
    })
    void optimizesAsSpecifiedInYamlPack(final String yaml, final String name,
        @RandomImage final String image) throws Exception {
        final Map<String, Object> pack = new Yaml().load(yaml);
        final String code = (String) pack.get("java");
        final Matcher pkg = Pattern.compile("package\\s+([\\w.]+)\\s*;").matcher(code);
        if (!pkg.find()) {
            throw new IllegalStateException(
                String.format("YAML pack '%s' lacks 'package' declaration in 'java' field", name)
            );
        }
        final Matcher cls = Pattern.compile("class\\s+(\\w+)").matcher(code);
        if (!cls.find()) {
            throw new IllegalStateException(
                String.format("YAML pack '%s' lacks 'class' declaration in 'java' field", name)
            );
        }
        final String slashed = pkg.group(1).replace('.', '/');
        final String klass = cls.group(1);
        final String path = String.format("src/main/java/%s/%s.java", slashed, klass);
        new Farea(this.directory).together(
            f -> OptimizeMojoYamlPackTest.runYamlPack(
                f, path, code, pack, image, slashed, klass, pkg.group(1)
            )
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
        final Path input = dir.resolve("input.phi");
        Files.write(
            input,
            String.format("%s%n", raw).getBytes(StandardCharsets.UTF_8)
        );
        final List<String> cmd = new ArrayList<>(rules.size() + 4);
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
        try (
            Stream<Path> entries = Files.list(
                Paths.get(System.getProperty("target.directory"))
                    .getParent().resolve("src").resolve("test").resolve("phino")
            )
        ) {
            packs = entries
                .filter(p -> p.toString().endsWith(".yml"))
                .sorted()
                .collect(Collectors.toList());
        }
        return packs.stream();
    }

    /**
     * Body of {@link #optimizesAsSpecifiedInYamlPack}.
     * @param fea Fake Maven project
     * @param path Path of the Java source file to write, relative to the project
     * @param code Java source of the class under test
     * @param pack The full YAML pack, for its optional and expectation fields
     * @param image Docker image tag
     * @param slashed The pack's package name, with dots replaced by slashes
     * @param klass The pack's class name
     * @param pkgname The pack's package name
     * @throws IOException If the build fails to run
     */
    @SuppressWarnings("unchecked")
    private static void runYamlPack(final Farea fea, final String path, final String code,
        final Map<String, Object> pack, final String image, final String slashed,
        final String klass, final String pkgname) throws IOException {
        final String grepin;
        if (pack.containsKey("grep-in")) {
            grepin = (String) pack.get("grep-in");
        } else {
            grepin = OptimizeMojo.DEFAULT_GREP_IN;
        }
        fea.clean();
        fea.files()
            .file(path)
            .write(code.getBytes(StandardCharsets.UTF_8));
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("rules", "streams/*")
            .set("grepIn", grepin)
            .set("image", image);
        fea.build()
            .plugins()
            .append("org.codehaus.mojo", "exec-maven-plugin", "3.5.0")
            .execution("default")
            .phase("process-classes")
            .goals("java")
            .configuration()
            .set("mainClass", String.format("%s.%s", pkgname, klass));
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            String.format(
                "log lacks one of the expected substrings for pack at %s",
                path
            ),
            fea.log().content(),
            Matchers.allOf(
                new Mapped<>(Matchers::containsString, (List<String>) pack.get("log"))
            )
        );
        OptimizeMojoYamlPackTest.assertOpcodes(
            fea.files().file(
                String.format("target/classes-before-hone/%s/%s.class", slashed, klass)
            ).path(),
            (Map<String, Integer>) pack.get("before"),
            "before"
        );
        OptimizeMojoYamlPackTest.assertOpcodes(
            fea.files().file(
                String.format("target/classes/%s/%s.class", slashed, klass)
            ).path(),
            (Map<String, Integer>) pack.get("after"),
            "after"
        );
    }

    /**
     * Assert that opcode counts in a compiled class match the YAML
     * expectations. A zero value asserts the opcode is absent.
     * @param klass Path to the .class file
     * @param expected Expected map of opcode to count, or null to skip
     * @param stage Either "before" or "after" — used in the failure message
     * @throws IOException If the class file cannot be read
     */
    private static void assertOpcodes(final Path klass,
        final Map<String, Integer> expected, final String stage) throws IOException {
        if (expected == null) {
            return;
        }
        final Map<String, Integer> actual = new ClassOpcodes(klass).counts();
        final List<org.hamcrest.Matcher<? super Map<String, Integer>>> checks =
            new ArrayList<>(expected.size());
        for (final Map.Entry<String, Integer> entry : expected.entrySet()) {
            checks.add(OptimizeMojoYamlPackTest.opcodeMatcher(entry.getKey(), entry.getValue()));
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
    private static org.hamcrest.Matcher<? super Map<String, Integer>> opcodeMatcher(
        final String opcode, final Integer count) {
        final org.hamcrest.Matcher<? super Map<String, Integer>> ret;
        if (count == 0) {
            ret = Matchers.not(Matchers.hasKey(opcode));
        } else {
            ret = Matchers.hasEntry(opcode, count);
        }
        return ret;
    }
}
