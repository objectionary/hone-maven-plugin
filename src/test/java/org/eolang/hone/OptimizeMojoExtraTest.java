/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.farea.Farea;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the extra rules of {@link OptimizeMojo}.
 *
 * @since 0.6.1
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "PMD.AvoidAccessibilityAlteration"
})
final class OptimizeMojoExtraTest {

    @Test
    void keepsOneCopyOfExtraRuleAfterTwoRuns(@Mktmp final Path dir) throws Exception {
        new Farea(dir).together(
            f -> {
                f.files().file("src/main/java/Foo.java").write(
                    "class Foo { }".getBytes(StandardCharsets.UTF_8)
                );
                f.files().file("src/rules/one.yml").write(
                    "name: one".getBytes(StandardCharsets.UTF_8)
                );
                f.build()
                    .plugins()
                    .appendItself()
                    .execution("default")
                    .phase("process-classes")
                    .goals("optimize")
                    .configuration()
                    .set("alwaysWithDocker", true)
                    .set("image", "hone-image-that-does-not-exist:0.0.0")
                    .set("extra", new String[] {"src/rules/one.yml"});
                f.execQuiet("process-classes");
                f.execQuiet("process-classes");
            }
        );
        try (Stream<Path> files = Files.list(dir.resolve("target/hone-extra"))) {
            MatcherAssert.assertThat(
                "the extra rule must be copied once, no matter how many runs were before",
                files.map(p -> p.getFileName().toString()).collect(Collectors.toList()),
                Matchers.contains("0000-one.yml")
            );
        }
    }

    @Test
    void resolvesRelativeExtraAgainstBasedirNotCwd(@Mktmp final Path dir) throws Exception {
        final Path module = Files.createDirectories(dir.resolve("module"));
        Files.write(
            Files.createDirectories(module.resolve("src/rules")).resolve("sevens.yml"),
            "name: sevens".getBytes(StandardCharsets.UTF_8)
        );
        final OptimizeMojo mojo = new OptimizeMojo();
        OptimizeMojoExtraTest.set(mojo, AbstractMojo.class, "basedir", module.toFile());
        OptimizeMojoExtraTest.set(
            mojo, OptimizeMojo.class, "extra",
            Collections.singletonList("src/rules/sevens.yml")
        );
        final Path extdir = Files.createDirectories(module.resolve("target/hone-extra"));
        final Method copy = OptimizeMojo.class.getDeclaredMethod("copyExtras", Path.class);
        copy.setAccessible(true);
        copy.invoke(mojo, extdir);
        try (Stream<Path> files = Files.list(extdir)) {
            MatcherAssert.assertThat(
                "a relative extra path must be resolved against basedir, not the JVM's cwd",
                files.map(p -> p.getFileName().toString()).collect(Collectors.toList()),
                Matchers.contains("0000-sevens.yml")
            );
        }
    }

    @Test
    void takesRulesFromASubdirectoryOfAnExtraDirectory(@Mktmp final Path dir) throws Exception {
        final Path rules = Files.createDirectories(dir.resolve("src/rules/deeper"));
        Files.write(
            rules.getParent().resolve("one.yml"), "name: one".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            rules.resolve("three.phr"), "name: three".getBytes(StandardCharsets.UTF_8)
        );
        final OptimizeMojo mojo = new OptimizeMojo();
        OptimizeMojoExtraTest.set(mojo, AbstractMojo.class, "basedir", dir.toFile());
        OptimizeMojoExtraTest.set(
            mojo, OptimizeMojo.class, "extra", Collections.singletonList("src/rules")
        );
        OptimizeMojoExtraTest.set(
            mojo, OptimizeMojo.class, "extraExtensions", "yml,yaml,phr"
        );
        final Path extdir = Files.createDirectories(dir.resolve("target/hone-extra"));
        final Method copy = OptimizeMojo.class.getDeclaredMethod("copyExtras", Path.class);
        copy.setAccessible(true);
        copy.invoke(mojo, extdir);
        try (Stream<Path> files = Files.list(extdir)) {
            MatcherAssert.assertThat(
                "a rule in a subdirectory must be taken too, in path order, since the built-in set is nested and sorted the same way",
                files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList()),
                Matchers.contains("0000-three.yml", "0001-one.yml")
            );
        }
    }

    private static void set(
        final Object target, final Class<?> owner, final String name, final Object value
    ) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
