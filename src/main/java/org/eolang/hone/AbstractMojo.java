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
import java.io.File;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Abstract mojo.
 *
 * @since 0.1.0
 */
abstract class AbstractMojo extends org.apache.maven.plugin.AbstractMojo {

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
     * @since 0.1.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(property = "hone.image", defaultValue = "yegor256/hone")
    protected String image;

    /**
     * Use "sudo" for "docker".
     *
     * @since 0.1.0
     */
    @Parameter(property = "hone.sudo", defaultValue = "false")
    protected boolean sudo;

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
    public final void execute() throws MojoExecutionException {
        if (this.skip) {
            Logger.info(this, "Execution skipped");
            return;
        }
        try {
            this.exec();
        } catch (final IOException ex) {
            throw new MojoExecutionException(ex);
        }
        Logger.info(this, "Done!");
    }

    /**
     * Execute it.
     * @throws IOException If fails
     */
    abstract void exec() throws IOException;
}
