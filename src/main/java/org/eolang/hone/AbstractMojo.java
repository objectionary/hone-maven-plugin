/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.File;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.IoCheckedText;
import org.cactoos.text.TextOf;
import org.slf4j.impl.StaticLoggerBinder;

/**
 * Abstract base class for all Hone Maven plugin goals.
 *
 * <p>This class provides common functionality shared by all plugin goals,
 * including Docker image configuration, sudo support, execution timing,
 * and skip functionality. Unfortunately, this is the best we can do
 * in Maven Plugin API design (this inheritance-based approach is ugly,
 * but Maven forces us to use it).</p>
 *
 * @since 0.1.0
 * @checkstyle VisibilityModifierCheck (500 lines)
 */
abstract class AbstractMojo extends org.apache.maven.plugin.AbstractMojo {

    /**
     * The "target/" directory of Maven project.
     *
     * @since 0.1.0
     */
    @Parameter(
        property = "hone.target",
        defaultValue = "${project.build.directory}"
    )
    protected File target;

    /**
     * Timings tracker for performance measurements.
     *
     * @since 0.1.0
     */
    protected Timings timings;

    /**
     * Docker image to use.
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.image", defaultValue = "yegor256/hone:0.22.1")
    protected String image;

    /**
     * Whether to use "sudo" when executing Docker commands.
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.sudo", defaultValue = "false")
    protected boolean sudo;

    /**
     * Run without Docker even if phino is available.
     *
     * <p>If this is set to <tt>true</tt>, Docker is used
     * even if phino is available. It is recommended to keep this parameter
     * to <tt>false</tt>, thus making the build faster if it's possible.</p>
     *
     * @since 0.17.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.always-with-docker", defaultValue = "false")
    protected boolean alwaysWithDocker;

    /**
     * Phino version to use.
     *
     * @since 0.21.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.phino-version")
    private String phinoVersion;

    /**
     * Skip the execution, if set to TRUE.
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Skip the execution, if Docker is not available.
     *
     * @since 0.22.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.skipWithoutDocker", defaultValue = "false")
    private boolean skipWithoutDocker;

    @Override
    public final void execute() throws MojoExecutionException {
        StaticLoggerBinder.getSingleton().setMavenLog(this.getLog());
        if (this.skip) {
            Logger.info(this, "Execution skipped due to hone.skip=true");
        } else if (this.skipWithoutDocker && !new Docker(this.sudo).available()) {
            Logger.info(this, "Execution skipped due to hone.skipWithoutDocker=true");
        } else {
            this.timings = new Timings(this.target.toPath().resolve("hone-timings.csv"));
            try {
                this.exec();
            } catch (final IOException ex) {
                throw new MojoExecutionException(ex);
            }
        }
    }

    /**
     * Returns the phino version to use.
     * @return The phino version
     * @throws IOException If reading the default version fails
     */
    protected String phino() throws IOException {
        String version = this.phinoVersion;
        if (version == null || version.isEmpty()) {
            version = new IoCheckedText(
                new TextOf(
                    new ResourceOf("org/eolang/hone/default-phino-version.txt")
                )
            ).asString().trim();
        }
        return version;
    }

    /**
     * Execute the specific goal implementation.
     * @throws IOException If execution fails
     */
    abstract void exec() throws IOException;
}
