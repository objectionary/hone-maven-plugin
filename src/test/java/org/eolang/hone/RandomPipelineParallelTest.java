/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link RandomPipeline}, against the promise its walk makes
 * when it lets a pipeline go parallel: the pipeline returns what it would
 * return sequentially, or the random test compares two runs of an oracle that
 * depends on how the stream happened to split (see #1416).
 *
 * @since 0.33.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings({
    "JTCOP.RuleEveryTestHasProductionClass",
    "PMD.AvoidAccessibilityAlteration"
})
final class RandomPipelineParallelTest {

    @Test
    void returnsTheSameValueParallelOrNot(@Mktmp final Path dir) throws Exception {
        final List<String> names = new ArrayList<>(0);
        final List<String> parallel = new ArrayList<>(0);
        final List<String> sequential = new ArrayList<>(0);
        for (int seed = 0; seed < 3000; ++seed) {
            final String name = String.format("P%04d", seed);
            final String java = new RandomPipeline(seed).java("twins", name);
            if (java.contains(".parallel()") || java.contains(".parallelStream()")) {
                names.add(name);
                parallel.add(RandomPipelineParallelTest.saved(dir.resolve("par"), name, java));
                sequential.add(
                    RandomPipelineParallelTest.saved(
                        dir.resolve("seq"), name,
                        java.replaceAll("\\R\\s*\\.parallel\\(\\)", "")
                            .replace(".parallelStream()", ".stream()")
                    )
                );
            }
        }
        final Path par = RandomPipelineParallelTest.compiled(dir.resolve("par-out"), parallel);
        final Path seq = RandomPipelineParallelTest.compiled(dir.resolve("seq-out"), sequential);
        final List<String> differ = new ArrayList<>(0);
        for (final String name : names) {
            final String raced = RandomPipelineParallelTest.value(par, name);
            final String walked = RandomPipelineParallelTest.value(seq, name);
            if (!raced.equals(walked)) {
                differ.add(
                    String.format("%s: '%s' parallel, '%s' sequential", name, raced, walked)
                );
            }
        }
        MatcherAssert.assertThat(
            "a pipeline that goes parallel must return what it returns sequentially",
            differ,
            Matchers.empty()
        );
    }

    private static String saved(final Path dir, final String name, final String java)
        throws IOException {
        final Path file = dir.resolve(String.format("twins/%s.java", name));
        Files.createDirectories(file.getParent());
        Files.write(file, java.getBytes(StandardCharsets.UTF_8));
        return file.toString();
    }

    private static Path compiled(final Path out, final List<String> files) throws IOException {
        Files.createDirectories(out);
        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        final List<String> args = new ArrayList<>(0);
        args.add("-nowarn");
        args.add("-d");
        args.add(out.toString());
        args.addAll(files);
        final int code = ToolProvider.getSystemJavaCompiler().run(
            null, null, errors, args.toArray(new String[0])
        );
        if (code != 0) {
            throw new IllegalStateException(
                new String(errors.toByteArray(), StandardCharsets.UTF_8)
            );
        }
        return out;
    }

    /*
     * What the pipeline of the class returns, which is the value its main()
     * prints, computed in this JVM by a loader of its own, since both twins
     * of one seed carry the same class name.
     */
    private static String value(final Path classes, final String name) throws Exception {
        try (URLClassLoader loader = RandomPipelineParallelTest.loader(classes)) {
            final Class<?> klass = loader.loadClass(String.format("twins.%s", name));
            final Method pipe = klass.getDeclaredMethod(
                "pipe", long.class, int.class, double.class, String.class
            );
            pipe.setAccessible(true);
            Object holder = null;
            if (!Modifier.isStatic(pipe.getModifiers())) {
                final Constructor<?> ctor = klass.getDeclaredConstructor(long.class);
                ctor.setAccessible(true);
                holder = ctor.newInstance(4L);
            }
            return (String) pipe.invoke(holder, 2L, 3, 1.5, "z");
        }
    }

    private static URLClassLoader loader(final Path classes) throws IOException {
        return new URLClassLoader(
            new URL[] {classes.toUri().toURL()},
            Thread.currentThread().getContextClassLoader()
        );
    }
}
