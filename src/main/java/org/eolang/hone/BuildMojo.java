/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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

    /**
     * Phino version to use.
     *
     * @since 0.2.0
     * @checkstyle MemberNameCheck (6 lines)
     */
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Parameter(property = "hone.phino-version", defaultValue = "0.0.0.37")
    private String phinoVersion;

    @Override
    public void exec() throws IOException {
        try (Mktemp temp = new Mktemp()) {
            final String[] files = {
                "Dockerfile", "entry.sh", "in-docker-pom.xml",
                "normalize.sh", "extensions.xml", "settings.xml",
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
            new Rules("*").copyTo(temp.path().resolve("rules"));
            for (final String file : new String[] {"entry.sh", "normalize.sh"}) {
                temp.path().resolve(file).toFile().setExecutable(true);
            }
            this.timings.through(
                "build",
                () -> new IoChecked<>(
                    new Retry<>(
                        (Scalar<Object>) () -> new Docker(this.sudo).exec(
                            "build",
                            "--pull",
                            "--progress=plain",
                            "--build-arg", String.format("PHINO_VERSION=%s", this.phinoVersion),
                            "--tag", this.image,
                            temp.path().toString()
                        )
                    )
                ).value()
            );
        }
    }
}
