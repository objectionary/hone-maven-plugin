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
     * Skip the execution, if set to TRUE.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The "target/" directory of Maven project.
     *
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
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
        final ProcessBuilder builder = new ProcessBuilder(
            "docker", "run",
            "--rm",
            "--volume", String.format("%s:/target", this.target),
            "--env", "TARGET=/target",
            "yegor256/hone"
        );
        Logger.info(this, "+ %s", String.join(" ", builder.command()));
        try (VerboseProcess proc = new VerboseProcess(builder)) {
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
}
