/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.Jhome;
import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.Result;
import com.yegor256.farea.Farea;
import com.yegor256.farea.RequisiteMatcher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.ToolProvider;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Test case for {@link OptimizeMojo}, fuzzing it with randomly generated
 * stream pipelines from {@link RandomPipeline}.
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(RandomImageResolver.class)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RandomPipelineOptimizationTest {

    @Test
    void repeatsTheSamePipelineOnTheSameSeed() {
        MatcherAssert.assertThat(
            "the same seed must produce the very same class, or a failure found once is lost",
            new RandomPipeline(42L).java("same", "X"),
            Matchers.equalTo(new RandomPipeline(42L).java("same", "X"))
        );
    }

    @Test
    void walksElsewhereOnAnotherSeed() {
        MatcherAssert.assertThat(
            "two seeds must not walk the grammar the same way",
            new RandomPipeline(1L).java("other", "X"),
            Matchers.not(Matchers.equalTo(new RandomPipeline(2L).java("other", "X")))
        );
    }

    @Test
    void avoidsAPeekInFrontOfCount() {
        final List<String> peeked = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("counted", "X");
            if (java.contains(".peek(") && java.contains(".count()")) {
                peeked.add(java);
            }
        }
        MatcherAssert.assertThat(
            "a peek must not sit in front of count(), which may skip the traversal",
            peeked,
            Matchers.empty()
        );
    }

    @Test
    void avoidsAThrowInFrontOfCount() {
        final List<String> thrown = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("counted", "X");
            if (java.contains("throw boom(") && java.contains(".count()")) {
                thrown.add(java);
            }
        }
        MatcherAssert.assertThat(
            "a throwing operator must not sit in front of count(), which may never reach it",
            thrown,
            Matchers.empty()
        );
    }

    @Test
    void catchesEveryPipelineThatThrows() {
        final List<String> loose = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("caught", "X");
            if (java.contains("throw boom(") && !java.contains("} catch (")) {
                loose.add(java);
            }
        }
        MatcherAssert.assertThat(
            "a pipeline that throws must sit in a body that catches, or it kills the JVM",
            loose,
            Matchers.empty()
        );
    }

    @Test
    void reachesPipelinesThatThrow() {
        final List<String> thrown = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("thrown", "X");
            if (java.contains("throw boom(")) {
                thrown.add(java);
            }
        }
        MatcherAssert.assertThat(
            "some pipelines must throw, or the tests that guard a throw guard nothing",
            thrown,
            Matchers.not(Matchers.empty())
        );
    }

    @Test
    void avoidsParallelWhenTheTraversalIsCounted() {
        final List<String> raced = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("raced", "X");
            if (java.contains(".parallel()")
                && (java.contains("SUM[0] +=") || java.contains("throw boom("))) {
                raced.add(java);
            }
        }
        MatcherAssert.assertThat(
            "a parallel pipeline must not count its own traversal, since workers lose updates",
            raced,
            Matchers.empty()
        );
    }

    @Test
    void reachesParallelPipelines() {
        final List<String> raced = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("raced", "X");
            if (java.contains(".parallel()")) {
                raced.add(java);
            }
        }
        MatcherAssert.assertThat(
            "some pipelines must go parallel, or the rules that revert fusion are never tried",
            raced,
            Matchers.not(Matchers.empty())
        );
    }

    @Test
    void reachesInstanceMethods() {
        final List<String> instances = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("held", "X");
            if (java.contains("private String pipe(")) {
                instances.add(java);
            }
        }
        MatcherAssert.assertThat(
            "some pipelines must sit in an instance method, or only static frames are tried",
            instances,
            Matchers.not(Matchers.empty())
        );
    }

    @Test
    void reachesLambdasThatReadThis() {
        final List<String> selfish = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("selfish", "X");
            if (java.contains("this.bump(") || java.contains("this::decorate")) {
                selfish.add(java);
            }
        }
        MatcherAssert.assertThat(
            "some lambdas must read this, or the non-static lambda rules are never tried",
            selfish,
            Matchers.not(Matchers.empty())
        );
    }

    @Test
    void reachesLambdasThatCapture() {
        final List<String> capturing = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String java = new RandomPipeline(seed).java("capturing", "X");
            if (java.contains("n + by") || java.contains("tag::concat")) {
                capturing.add(java);
            }
        }
        MatcherAssert.assertThat(
            "some lambdas must capture an argument, or the capturing metafactory is never lifted",
            capturing,
            Matchers.not(Matchers.empty())
        );
    }

    @Test
    void reachesEveryProductionOfTheGrammar() {
        final StringBuilder walked = new StringBuilder();
        for (int seed = 0; seed < 2000; ++seed) {
            walked.append(new RandomPipeline(seed).java("reached", "X"));
        }
        final List<String> missed = new ArrayList<>(0);
        final Grammar grammar = new Grammar();
        for (final String production : grammar.fragments()) {
            if (!walked.toString().contains(Grammar.fragment(production))) {
                missed.add(production);
            }
        }
        MatcherAssert.assertThat(
            "every production must be reachable, or the grammar promises more than it walks",
            missed,
            Matchers.empty()
        );
    }

    @Test
    void generatesPipelinesThatCompile(@Mktmp final Path dir) throws IOException {
        final List<String> files = new ArrayList<>(0);
        for (int seed = 0; seed < 500; ++seed) {
            final String name = String.format("P%04d", seed);
            final Path java = dir.resolve(String.format("%s.java", name));
            Files.write(
                java,
                new RandomPipeline(seed).java("compiled", name).getBytes(StandardCharsets.UTF_8)
            );
            files.add(java.toString());
        }
        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        final List<String> args = new ArrayList<>(0);
        args.add("-d");
        args.add(dir.toString());
        args.addAll(files);
        ToolProvider.getSystemJavaCompiler().run(
            null, null, errors, args.toArray(new String[0])
        );
        MatcherAssert.assertThat(
            "every generated class must compile, or the grammar is not typed",
            new String(errors.toByteArray(), StandardCharsets.UTF_8),
            Matchers.emptyString()
        );
    }

    @Test
    @Tag("deep")
    @ExtendWith(MayBeSlow.class)
    @Timeout(1200L)
    @DisabledWithoutDocker
    @SuppressWarnings({"PMD.UnitTestShouldIncludeAssert", "JTCOP.RuleAssertionMessage"})
    void preservesWhatRandomPipelinesPrint(@Mktmp final Path home,
        @RandomImage final String image) throws Exception {
        final int pipelines = Integer.getInteger("hone.random.pipelines", 120);
        new Farea(home).together(
            f -> RandomPipelineOptimizationTest.runRandomPipelines(f, home, pipelines, image)
        );
    }

    /**
     * Body of {@link #preservesWhatRandomPipelinesPrint}.
     * @param fea Fake Maven project
     * @param home The root of the fake Maven project
     * @param pipelines How many random pipelines to generate
     * @param image Docker image tag
     * @throws IOException If the build fails to run
     */
    private static void runRandomPipelines(final Farea fea, final Path home,
        final int pipelines, final String image) throws IOException {
        fea.clean();
        for (int seed = 0; seed < pipelines; ++seed) {
            final String name = String.format("P%04d", seed);
            fea.files()
                .file(String.format("src/main/java/random/%s.java", name)).write(
                    new RandomPipeline(seed)
                        .java("random", name)
                        .getBytes(StandardCharsets.UTF_8)
                );
        }
        fea.build()
            .plugins()
            .appendItself()
            .execution("default")
            .phase("process-classes")
            .goals("build", "optimize")
            .configuration()
            .set("rules", "streams/*")
            .set("grepIn", ".*")
            .set("image", image);
        fea.exec("process-classes");
        MatcherAssert.assertThat(
            "the build of the random pipelines must be successful",
            fea.log(),
            RequisiteMatcher.SUCCESS
        );
        int rewritten = 0;
        for (int seed = 0; seed < pipelines; ++seed) {
            final String klass = String.format("random/P%04d.class", seed);
            if (!Arrays.equals(
                Files.readAllBytes(home.resolve("target/classes").resolve(klass)),
                Files.readAllBytes(
                    home.resolve("target/classes-before-hone").resolve(klass)
                )
            )) {
                rewritten += 1;
            }
        }
        MatcherAssert.assertThat(
            String.format(
                "at least one of the %d pipelines must be rewritten, or the experiment proves nothing",
                pipelines
            ),
            rewritten,
            Matchers.greaterThan(0)
        );
        final List<String> broken = new ArrayList<>(0);
        for (int seed = 0; seed < pipelines; ++seed) {
            final String name = String.format("P%04d", seed);
            final Result before = RandomPipelineOptimizationTest.runs(
                home, "target/classes-before-hone", name
            );
            final Result after = RandomPipelineOptimizationTest.runs(home, "target/classes", name);
            final String complaint = RandomPipelineOptimizationTest.differs(
                before, after, new RandomPipeline(seed).java("random", name), name
            );
            if (!complaint.isEmpty()) {
                broken.add(complaint);
            }
        }
        MatcherAssert.assertThat(
            String.format(
                "optimization must not change what a pipeline prints, %d of %d did",
                broken.size(), pipelines
            ),
            broken,
            Matchers.empty()
        );
    }

    /**
     * What the two runs of one pipeline disagree about.
     * @param before The run of the class as the Java compiler left it
     * @param after The run of the same class after optimization
     * @param java The source of the class, printed when there is a complaint
     * @param name The name of the class
     * @return The complaint, or an empty string when the two runs agree
     */
    private static String differs(final Result before, final Result after,
        final String java, final String name) {
        final String complaint;
        if (before.code() == 0 && after.code() == 0
            && before.stdout().equals(after.stdout())) {
            complaint = "";
        } else if (before.code() == 0 && after.code() == 0) {
            complaint = String.format(
                "%s printed '%s' before optimization and '%s' after it:%n%s",
                name, before.stdout().trim(), after.stdout().trim(), java
            );
        } else if (before.code() == 0) {
            complaint = String.format(
                "%s cannot run after optimization:%n%s%n%s", name, java, after.stderr()
            );
        } else {
            complaint = String.format(
                "%s cannot run before optimization, so the grammar is broken:%n%s%n%s",
                name, java, before.stderr()
            );
        }
        return complaint;
    }

    /**
     * Run one class of the fake project, in the JVM that runs this test.
     * @param home The root of the fake Maven project
     * @param dir The directory with compiled classes, relative to the root
     * @param name The name of the class in the {@code random} package
     * @return What the JVM printed and the code it exited with
     * @throws IOException If the JVM cannot be started
     */
    private static Result runs(final Path home, final String dir, final String name)
        throws IOException {
        return new Jaxec(
            new Jhome().java().toString(),
            "-cp",
            home.resolve(dir).toString(),
            String.format("random.%s", name)
        ).withCheck(false).execUnsafe();
    }
}
