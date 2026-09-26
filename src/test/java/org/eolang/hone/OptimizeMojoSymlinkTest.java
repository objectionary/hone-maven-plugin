/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the classes lookup of {@link OptimizeMojo}, which walked
 * {@code target/classes} without following links, so a symlinked directory
 * read as empty and the whole goal skipped itself (see #1114).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "PMD.AvoidAccessibilityAlteration"
})
final class OptimizeMojoSymlinkTest {

    @Test
    void findsClassesBehindASymlink(@Mktmp final Path home) throws Exception {
        final Path real = Files.createDirectories(home.resolve("real/org"));
        Files.write(real.resolve("Foo.class"), "bytes".getBytes(StandardCharsets.UTF_8));
        final Path target = Files.createDirectories(home.resolve("target"));
        Files.createSymbolicLink(target.resolve("classes"), home.resolve("real"));
        MatcherAssert.assertThat(
            "a symlinked classes directory holds classes, and everything downstream follows the link",
            OptimizeMojoSymlinkTest.withoutClasses(target),
            Matchers.is(false)
        );
    }

    @Test
    void findsNoClassesInAnEmptyDirectory(@Mktmp final Path home) throws Exception {
        final Path target = Files.createDirectories(home.resolve("target"));
        Files.createDirectories(target.resolve("classes"));
        MatcherAssert.assertThat(
            "an empty directory has no classes in it",
            OptimizeMojoSymlinkTest.withoutClasses(target),
            Matchers.is(true)
        );
    }

    private static boolean withoutClasses(final Path target) throws Exception {
        final OptimizeMojo mojo = new OptimizeMojo();
        final Field dir = AbstractMojo.class.getDeclaredField("target");
        dir.setAccessible(true);
        dir.set(mojo, target.toFile());
        final Field classes = OptimizeMojo.class.getDeclaredField("classes");
        classes.setAccessible(true);
        classes.set(mojo, "classes");
        final Method method = OptimizeMojo.class.getDeclaredMethod("withoutClasses");
        method.setAccessible(true);
        return (boolean) method.invoke(mojo);
    }
}
