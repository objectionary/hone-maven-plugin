/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the {@code docker run} command {@link OptimizeMojo} builds,
 * which handed the container {@code --privileged} and the host Docker socket,
 * neither of which anything in the image can use (see #1033).
 *
 * @since 0.30.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "PMD.AvoidAccessibilityAlteration"
})
final class OptimizeMojoDockerCommandTest {

    @Test
    void asksForNoPrivileges(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "the image holds no docker client and needs no devices, so the container must not be privileged",
            OptimizeMojoDockerCommandTest.command(home),
            Matchers.not(Matchers.hasItem("--privileged"))
        );
    }

    @Test
    void mountsNoDockerSocket(@Mktmp final Path home) throws Exception {
        MatcherAssert.assertThat(
            "nothing in the image talks to the daemon, so the socket must stay on the host",
            OptimizeMojoDockerCommandTest.command(home),
            Matchers.not(Matchers.hasItem("/var/run/docker.sock:/var/run/docker.sock"))
        );
    }

    @Test
    void namesTheImageLast(@Mktmp final Path home) throws Exception {
        final Collection<String> command = OptimizeMojoDockerCommandTest.command(home);
        MatcherAssert.assertThat(
            "the image must be the last word, or docker reads it as an option",
            command.toArray()[command.size() - 1],
            Matchers.is("hone:latest")
        );
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> command(final Path home) throws Exception {
        final OptimizeMojo mojo = new OptimizeMojo();
        OptimizeMojoDockerCommandTest.set(
            mojo, AbstractMojo.class, "target",
            Files.createDirectories(home.resolve("target")).toFile()
        );
        OptimizeMojoDockerCommandTest.set(mojo, AbstractMojo.class, "basedir", home.toFile());
        OptimizeMojoDockerCommandTest.set(mojo, OptimizeMojo.class, "classes", "classes");
        OptimizeMojoDockerCommandTest.set(mojo, OptimizeMojo.class, "rules", "none");
        OptimizeMojoDockerCommandTest.set(mojo, OptimizeMojo.class, "grepIn", ".*");
        OptimizeMojoDockerCommandTest.set(
            mojo, OptimizeMojo.class, "cache",
            Files.createDirectories(home.resolve("cache")).toFile()
        );
        OptimizeMojoDockerCommandTest.set(mojo, AbstractMojo.class, "image", "hone:latest");
        final Method method = OptimizeMojo.class.getDeclaredMethod("dockerCommand");
        method.setAccessible(true);
        return (Collection<String>) method.invoke(mojo);
    }

    private static void set(
        final Object target, final Class<?> owner, final String name, final Object value
    ) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
