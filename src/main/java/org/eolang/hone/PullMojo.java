/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.IOException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Pull Docker image from Docker Hub.
 *
 * <p>This goal pulls Docker image from
 * <a href="https://hub.docker.com">Docker Hub</a> to your machine. You
 * may skip this goal and simply use the {@code optimize} goal, which
 * will automatically pull the image from the Hub. However, it would be
 * cleaner to use {@code pull}, then {@code optimize}, and
 * then {@code rmi} (which deletes the image from your machine).</p>
 *
 * @since 0.1.0
 */
@Mojo(name = "pull", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresProject = false)
public final class PullMojo extends AbstractMojo {

    /**
     * Ctor.
     */
    public PullMojo() {
        // nothing
    }

    @Override
    public void exec() throws IOException {
        if (this.alwaysWithDocker || !new Phino().available(this.phino())) {
            this.timings.through(
                "pull",
                () -> new Docker(this.sudo).exec(
                    "pull",
                    this.image
                )
            );
            Logger.info(this, "Docker image '%s' was pulled", this.image);
        } else {
            Logger.info(this, "Docker image '%s' was NOT pulled", this.image);
        }
    }
}
