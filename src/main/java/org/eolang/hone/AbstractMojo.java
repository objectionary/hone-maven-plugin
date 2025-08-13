/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.File;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
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
    @Parameter(property = "hone.image", defaultValue = "yegor256/hone:0.8.2")
    protected String image;

    /**
     * Whether to use "sudo" when executing Docker commands.
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.sudo", defaultValue = "false")
    protected boolean sudo;

    /**
     * Skip the execution, if set to TRUE.
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public final void execute() throws MojoExecutionException {
        StaticLoggerBinder.getSingleton().setMavenLog(this.getLog());
        if (this.skip) {
            Logger.info(this, "Execution skipped due to hone.skip=true");
            return;
        }
        this.timings = new Timings(this.target.toPath().resolve("hone-timings.csv"));
        try {
            this.exec();
        } catch (final IOException ex) {
            throw new MojoExecutionException(ex);
        }
    }

    /**
     * Execute the specific goal implementation.
     * @throws IOException If execution fails
     */
    abstract void exec() throws IOException;
}
