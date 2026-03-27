/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Build summary statistics.
 *
 * <p>This goal tries to find all the statistics in child modules and
 * build a single summary report.</p>
 *
 * @since 0.1.0
 */
@Mojo(
    name = "summary",
    aggregator = true
)
public final class SummaryMojo extends AbstractMojo {

    @Override
    public void exec() throws IOException {
        Logger.info(this, "Collecting summary build statistics...");
        new Summary(
            this.basedir.toPath(),
            Files.createDirectories(this.target.toPath())
        ).collect();
        Logger.info(this, "Summary build statistics collected successfully!");
    }
}
