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
 * Remove Docker image.
 *
 * <p>This goal deletes Docker image from your machine, in order to
 * save space and simply clean up after the optimization step. You
 * may not use this goal at all, but we recommend to use it.</p>
 *
 * @since 0.1.0
 */
@Mojo(name = "rmi", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresProject = false)
public final class RmiMojo extends AbstractMojo {

    @Override
    public void exec() throws IOException {
        if (!this.alwaysWithDocker && new Phino().available()) {
            Logger.info(
                this,
                "Docker image '%s' was probably NOT built, that's why not removed either",
                this.image
            );
        } else {
            new Docker(this.sudo).exec(
                "rmi",
                "--no-prune",
                this.image
            );
            Logger.info(this, "Docker image '%s' was removed", this.image);
        }
    }
}
