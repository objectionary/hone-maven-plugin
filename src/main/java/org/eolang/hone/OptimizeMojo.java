/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.sun.security.auth.module.UnixSystem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.cactoos.iterable.Mapped;

/**
 * Converts Bytecode to Bytecode in order to make it faster.
 *
 * <p>This goal takes every <tt>.class</tt> file from the
 * <tt>target/classes/</tt> directory, converts it to <tt>.xmir</tt>
 * format (which is XML representation of <a href="https://www.eolang.org">EO</a>),
 * then converts <tt>.xmir</tt> to <tt>.phi</tt> (which is
 * <a href="https://arxiv.org/abs/2111.13384">𝜑-calculus</a>),
 * then optimizes it via
 * <a href="https://github.com/objectionary/phino">phino</a>,
 * and then back to <tt>.xmir</tt> and to <tt>.class</tt>. As a result,
 * you obtain optimized Bytecode in the <tt>target/classes/</tt> directory,
 * which supposedly works faster than before.</p>
 *
 * <p>The entire optimization pipeline happens inside Docker container,
 * which is run from the image specified in the <tt>image</tt> parameter.
 * The image may either be pulled or built locally. We recommend pulling
 * it from the Docker Hub with the help of the <tt>pull</tt> goal. Also,
 * we recommend deleting the image after optimization is done, with the help
 * of the <tt>rmi</tt> goal.</p>
 *
 * @since 0.1.0
 */
@Mojo(name = "optimize", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class OptimizeMojo extends AbstractMojo {

    /**
     * List of built-in rules to use for optimizations.
     *
     * <p>For example, "<tt>simple,b*,!abc</tt>" would include
     * the <tt>simple</tt> rule, all rules that start
     * with the <tt>b</tt> character, and exclude the <tt>abc</tt>
     * rule.</p>
     *
     * <p>In order to disable them all, simply set this parameter
     * to <tt>none</tt>.</p>
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.rules", defaultValue = "*")
    private String rules;

    /**
     * List of extra rules to use for optimizations, provided as
     * YAML files.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.extra")
    private List<String> extra;

    /**
     * EO version to use.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.eo-version")
    private String eoVersion;

    /**
     * JEO version to use.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.jeo-version")
    private String jeoVersion;

    /**
     * Skip phino entirely.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.skip-phino", defaultValue = "false")
    private boolean skipPhino;

    /**
     * EO cache directory.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (7 lines)
     * @checkstyle VisibilityModifierCheck (10 lines)
     */
    @Parameter(property = "hone.cache")
    @SuppressWarnings("PMD.ImmutableField")
    private File cache = Paths.get(System.getProperty("user.home")).resolve(".eo").toFile();

    @Override
    public void exec() throws IOException {
        final long start = System.currentTimeMillis();
        final Collection<String> command = new LinkedList<>(
            Arrays.asList(
                "run",
                "--rm",
                "--volume", String.format("%s:/target", this.target),
                "--volume", String.format("%s:/eo-cache", this.cache),
                "--env", "TARGET=/target"
            )
        );
        Path extdir = this.target.toPath().resolve("hone-extra");
        if (extdir.toFile().mkdirs()) {
            Logger.debug(this, "Directory %[file]s created", extdir);
        }
        if (this.extra != null) {
            final int sub = extdir.toFile().list().length + 1;
            extdir = extdir.resolve(Integer.toString(sub));
            if (extdir.toFile().mkdirs()) {
                Logger.debug(this, "Directory %[file]s created", extdir);
            }
            final String fmt = String.format(
                "%%0%dd.yml",
                (int) Math.log10(this.extra.size()) + 1
            );
            for (int pos = 0; pos < this.extra.size(); ++pos) {
                final Path src = Paths.get(this.extra.get(pos));
                final Path dest = extdir.resolve(String.format(fmt, pos));
                Files.copy(src, dest);
                Logger.debug(this, "Extra rule %[file]s copied to %[file]s", src, dest);
            }
            command.addAll(
                Arrays.asList(
                    "--env", String.format("EXTRA=/target/hone-extra/%d", sub)
                )
            );
        }
        if (this.eoVersion == null) {
            Logger.debug(this, "EO version is not set, we use the default one");
        } else {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("EO_VERSION=%s", this.eoVersion)
                )
            );
        }
        if (this.jeoVersion == null) {
            Logger.debug(this, "JEO version is not set, we use the default one");
        } else {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("JEO_VERSION=%s", this.jeoVersion)
                )
            );
        }
        if (this.skipPhino) {
            Logger.debug(this, "Phino phase is skipped due to skipPhino flag");
            command.addAll(
                Arrays.asList(
                    "--env", "SKIP_PHINO=yes"
                )
            );
        }
        command.add("--user");
        command.add(
            String.format(
                "%d:%d",
                new UnixSystem().getUid(),
                new UnixSystem().getGid()
            )
        );
        command.addAll(
            Arrays.asList(
                "--env",
                String.format(
                    "RULES=%s",
                    String.join(
                        " ",
                        new Mapped<>(
                            p -> String.format("rules/%s", p),
                            new Rules(this.rules).yamls()
                        )
                    )
                )
            )
        );
        command.add(this.image);
        this.timings.through(
            "optimize",
            () -> new Docker(this.sudo).exec(command)
        );
        Logger.info(
            this,
            "Bytecode was optimized in '%s' in %[ms]s",
            this.target,
            System.currentTimeMillis() - start
        );
    }
}
