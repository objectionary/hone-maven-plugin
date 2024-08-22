/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.jcabi.log.VerboseProcess;
import com.yegor256.Jaxec;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Converts Bytecode to Bytecode.
 *
 * @since 0.1.0
 */
@Mojo(name = "optimize", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public final class OptimizeMojo extends AbstractMojo {

    /**
     * Default Docker image.
     */
    private static final String DEFAULT_IMAGE = "yegor256/hone";

    /**
     * Skip the execution, if set to TRUE.
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Docker image to use.
     *
     * <p>If you want to us to build a Docker image for you, use the
     * name that ends with ":local", for example: "hone:local".</p>
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.image", defaultValue = OptimizeMojo.DEFAULT_IMAGE)
    private String image;

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
     * OPEO version to use.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.opeo-version")
    private String opeoVersion;

    /**
     * The "target/" directory of Maven project.
     *
     * @since 0.1.0
     */
    @Parameter(
        property = "hone.target",
        defaultValue = "${project.build.directory}"
    )
    private File target;

    @Override
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            Logger.info(this, "Execution skipped");
            return;
        }
        new Jaxec("docker", "--version")
            .withCheck(true)
            .withRedirect(true)
            .exec();
        if (this.image.endsWith(":local")) {
            new Jaxec(
                "docker", "build",
                "src/docker",
                "--tag", this.image
            ).withCheck(true).withRedirect(true).exec();
        }
        final ProcessBuilder bldr = this.builder();
        try (VerboseProcess proc = new VerboseProcess(bldr)) {
            final VerboseProcess.Result ret = proc.waitFor();
            if (ret.code() != 0) {
                throw new MojoExecutionException(
                    String.format("Failed to optimize, code=%d", ret.code())
                );
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException(ex);
        }
        Logger.info(this, "Done!");
    }

    /**
     * The builder for Docker run.
     * @return Builder
     */
    private ProcessBuilder builder() {
        final List<String> command = new LinkedList<>(
            Arrays.asList(
                "docker", "run",
                "--rm",
                "--volume", String.format("%s:/target", this.target),
                "--env", "TARGET=/target"
            )
        );
        if (this.eoVersion != null) {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("EO_VERSION=%s", this.eoVersion)
                )
            );
        }
        if (this.jeoVersion != null) {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("JEO_VERSION=%s", this.jeoVersion)
                )
            );
        }
        if (this.opeoVersion != null) {
            command.addAll(
                Arrays.asList(
                    "--env", String.format("OPEO_VERSION=%s", this.opeoVersion)
                )
            );
        }
        command.add(this.image);
        return new ProcessBuilder(command);
    }
}
