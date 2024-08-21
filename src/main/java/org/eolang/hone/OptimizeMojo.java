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
package org.eolang.jeo;

import com.jcabi.log.Logger;
import java.io.File;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Converts Bytecode to Bytecode.
 *
 * @since 0.1.0
 */
@Mojo(name = "optimize", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public final class OptimizeMojo extends AbstractMojo {

    /**
     * Maven project.
     *
     * @since 0.2
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Directory with .class binaries.
     *
     * @since 0.2.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(
        property = "jeo.assemble.beforeDir",
        defaultValue = "${project.build.outputDirectory}"
    )
    private File beforeDir;

    /**
     * Target directory, with new .class binaries.
     *
     * @since 0.2.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @Parameter(
        property = "jeo.assemble.afterDir",
        defaultValue = "${project.build.directory}/after-hone"
    )
    private File afterDir;

    @Override
    public void execute() throws MojoExecutionException {
        Logger.info(this, "Done!");
    }
}
