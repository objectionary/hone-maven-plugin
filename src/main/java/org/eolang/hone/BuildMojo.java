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

import java.io.IOException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.cactoos.Scalar;
import org.cactoos.io.OutputTo;
import org.cactoos.io.ResourceOf;
import org.cactoos.io.TeeInput;
import org.cactoos.scalar.IoChecked;
import org.cactoos.scalar.LengthOf;
import org.cactoos.scalar.Retry;

/**
 * Build Docker image.
 *
 * <p>This goal must be used only if you want to build a local custom
 * Docker image for your project. This may be useful when you don't have
 * network access to Docker Hub. In most cases, you have it and that's
 * why don't need this goal.
 * Instead, just use the <tt>pull</tt> goal, which will simply pull
 * a required Docker image from the Hub.</p>
 *
 * <p>This goal is mostly for testing and CI/CD.</p>
 *
 * @since 0.1.0
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public final class BuildMojo extends AbstractMojo {

    @Override
    public void exec() throws IOException {
        try (Mktemp temp = new Mktemp()) {
            final String[] files = {
                "Dockerfile", "entry.sh", "in-docker-pom.xml",
                "install-ghc.sh", "install-maven.sh", "install-stack.sh",
                "install-normalizer.sh",
            };
            for (final String file : files) {
                new IoChecked<>(
                    new LengthOf(
                        new TeeInput(
                            new ResourceOf(String.format("org/eolang/hone/scaffolding/%s", file)),
                            new OutputTo(temp.path().resolve(file))
                        )
                    )
                ).value();
            }
            new Rules().copyTo(temp.path().resolve("rules"));
            temp.path().resolve("entry.sh").toFile().setExecutable(true);
            new IoChecked<>(
                new Retry<>(
                    (Scalar<Object>) () -> new Docker(this.sudo).exec(
                        "build",
                        "--pull",
                        "--tag", this.image,
                        temp.path().toString()
                    )
                )
            ).value();
        }
    }
}
