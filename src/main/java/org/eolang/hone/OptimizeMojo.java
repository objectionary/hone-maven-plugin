/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.sun.jna.Library;
import com.sun.jna.Native;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.cactoos.iterable.Mapped;

/**
 * Converts Bytecode to Bytecode in order to make it faster.
 *
 * <p>This goal takes every {@code .class} file from the
 * <tt>target/classes/</tt> directory, converts it to {@code .xmir}
 * format (which is XML representation of <a href="https://www.eolang.org">EO</a>),
 * then converts {@code .xmir} to {@code .phi} (which is
 * <a href="https://arxiv.org/abs/2111.13384">𝜑-calculus</a>),
 * then optimizes it via
 * <a href="https://github.com/objectionary/phino">phino</a>,
 * and then back to {@code .xmir} and to {@code .class}. As a result,
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
 * @checkstyle CyclomaticComplexityCheck (500 lines)
 * @checkstyle NPathComplexityCheck (500 lines)
 */
@Mojo(name = "optimize", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresProject = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class OptimizeMojo extends AbstractMojo {

    /**
     * Location of .class files to optimize, inside {@code target} directory.
     *
     * @since 0.8.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.classes", defaultValue = "classes")
    private String classes;

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
     * <p>If you enable them all with "*", your code most definitely
     * will be corrupted, because some rules are for testing purposes
     * only.</p>
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.rules", defaultValue = "none")
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
     * Grep XMIR files to rewrite.
     *
     * <p>Using this regular expression you can filter-in (include) XMIR files that
     * need to be rewritten. It's advised to use this regex in order
     * to save time. This is a good example, to filter-in only the files
     * that contain filter() and map() methods:</p>
     *
     * <pre>"&gt;o&lt;(66-69-6C-74-65-72|6D-61-70)&gt;/o&lt;</pre>
     *
     * <p>Here, <tt>66-69-6C-74-65-72</tt> stands for the <tt>"filter"</tt>
     * and <tt>6D-61-70</tt> for the <tt>"map"</tt>, in hexadecimal format.</p>
     *
     * @since 0.10.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.grep-in", defaultValue = "")
    private String grepIn;

    /**
     * Small steps or big steps?
     *
     * <p>Small steps mode will apply one rule at a time, producing separate
     * {@code .phi} files. This may be useful for debugging of the rules. To the contrary,
     * big steps mode will apply all rules at once, which is faster, but less
     * transparent for debugging.</p>
     *
     * @since 0.3.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.small-steps", defaultValue = "false")
    private boolean smallSteps;

    /**
     * How many rewriting cycles to tolerate maximum?
     *
     * <p>This number doesn't need to be changed. However, may be used for debugging.
     * The larger the number, the longer optimization might take.</p>
     *
     * @since 0.4.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.max-depth", defaultValue = "500")
    private int maxDepth;

    /**
     * All file extensions for the extra rules.
     *
     * @since 0.5.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.extra-extensions", defaultValue = "yml,yaml,phr")
    private String extraExtensions;

    /**
     * Include patterns for .class files.
     *
     * <p>Array of patterns to include specific {@code .class} files for optimization.
     * If not specified, all {@code .class} files will be included.</p>
     *
     * <p>Start them with {@code "/target/classes"}.</p>
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.includes")
    private String[] includes;

    /**
     * Exclude patterns for .class files.
     *
     * <p>Array of patterns to exclude specific {@code .class} files from optimization.
     * If not specified, no {@code .class} files will be excluded.</p>
     *
     * <p>Start them with {@code "/target/classes"}.</p>
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.excludes")
    private String[] excludes;

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
    @SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
    public void exec() throws IOException {
        final long start = System.currentTimeMillis();
        if (this.target.mkdirs()) {
            Logger.debug(this, "Target directory '%s' created", this.target);
        }
        final String tdir = "/target";
        final String cdir = "/eo-cache";
        final Collection<String> command = new LinkedList<>(
            Arrays.asList(
                "run",
                "--rm",
                "--volume", String.format("%s:%s", this.target, tdir),
                "--volume", String.format("%s:%s", this.cache, cdir),
                "--env", String.format("TARGET=%s", tdir),
                "--env", String.format("EO_CACHE=%s", cdir)
            )
        );
        final Path extdir = this.target.toPath().resolve("hone-extra");
        if (extdir.toFile().mkdirs()) {
            Logger.debug(this, "Directory %[file]s created", extdir);
        }
        if (this.extra != null) {
            if (extdir.toFile().mkdirs()) {
                Logger.debug(this, "Directory %[file]s created", extdir);
            }
            for (final String ext : this.extra) {
                final Path src = Paths.get(ext);
                if (src.toFile().isDirectory()) {
                    Logger.info(
                        this,
                        "Scanning %[file]s for extra rules (%s)...",
                        src, this.extraExtensions
                    );
                    try (Stream<Path> files = Files.list(src)) {
                        final List<Path> yamls = files
                            .filter(
                                f -> {
                                    boolean match = false;
                                    for (final String extn : this.extraExtensions.split(",")) {
                                        match = match || f.getFileName().toString().endsWith(
                                            String.format(".%s", extn.trim())
                                        );
                                    }
                                    return match;
                                }
                            )
                            .sorted()
                            .collect(Collectors.toList());
                        for (final Path yaml : yamls) {
                            this.saveExtra(yaml, extdir);
                        }
                    }
                } else {
                    this.saveExtra(src, extdir);
                }
            }
            command.addAll(
                Arrays.asList(
                    "--env", String.format("EXTRA=%s/hone-extra", tdir)
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
        command.addAll(
            Arrays.asList(
                "--env", String.format("CLASSES=%s", this.classes)
            )
        );
        command.addAll(
            Arrays.asList(
                "--env", String.format("SMALL_STEPS=%s", this.smallSteps)
            )
        );
        command.addAll(
            Arrays.asList(
                "--env", String.format("VERBOSE=%s", Logger.isDebugEnabled(this))
            )
        );
        command.addAll(
            Arrays.asList(
                "--env", String.format("GREP_IN=%s", this.grepIn)
            )
        );
        command.addAll(
            Arrays.asList(
                "--env", String.format("MAX_DEPTH=%d", this.maxDepth)
            )
        );
        if (this.includes != null && this.includes.length > 0) {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("INCLUDES=%s", String.join(",", this.includes))
                )
            );
        }
        if (this.excludes != null && this.excludes.length > 0) {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("EXCLUDES=%s", String.join(",", this.excludes))
                )
            );
        }
        command.add("--user");
        command.add(OptimizeMojo.whoami());
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

    private void saveExtra(final Path src, final Path target) throws IOException {
        final int already = Files.list(target).collect(Collectors.toList()).size();
        final String name = String.format(
            "%04d-%s.yml", already,
            src.getFileName().toString().replaceAll("\\.[a-zA-Z0-9]+$", "")
        );
        final Path copy = target.resolve(name);
        if (copy.toFile().exists()) {
            throw new IllegalStateException(
                String.format(
                    "Extra rule %s already exists, something is wrong with our algorithm?",
                    copy
                )
            );
        }
        Files.copy(src, copy);
        Logger.info(
            this,
            "Extra rule '%s' copied to %[file]s from %[file]s",
            name, target, src
        );
    }

    /**
     * Returns the user and group IDs of the current user.
     *
     * @return A string in the format "uid:gid"
     */
    private static String whoami() {
        return String.format(
            "%d:%d",
            OptimizeMojo.CLibrary.INSTANCE.getuid(),
            OptimizeMojo.CLibrary.INSTANCE.geteuid()
        );
    }

    /**
     * C library interface for getting user IDs.
     *
     * <p>This interface uses JNA (Java Native Access)
     * to call native C functions.</p>
     *
     * @since 0.6.0
     */
    public interface CLibrary extends Library {
        /**
         * Instance of the C library.
         *
         * <p>This is used to access native methods.</p>
         */
        OptimizeMojo.CLibrary INSTANCE = Native.load("c", OptimizeMojo.CLibrary.class);

        /**
         * Gets the user ID of the calling process.
         *
         * @return The user ID
         */
        int getuid();

        /**
         * Gets the effective user ID of the calling process.
         *
         * @return The effective user ID
         */
        int geteuid();
    }
}
