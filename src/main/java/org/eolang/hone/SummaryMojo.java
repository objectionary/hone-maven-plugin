/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

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

    /**
     * The current Maven session, whose projects are the only ones summarized.
     *
     * @since 0.1.0
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Ctor.
     */
    public SummaryMojo() {
        // nothing
    }

    @Override
    public void exec() throws IOException {
        Logger.info(this, "Collecting summary build statistics...");
        final Path stats = new Summary(
            this.session.getProjects().stream()
                .map(project -> Paths.get(project.getBuild().getDirectory()))
                .collect(Collectors.toList()),
            Files.createDirectories(this.target.toPath())
        ).collect();
        if (stats.toFile().exists()) {
            final CSV csv = new CSV(stats);
            Logger.info(
                this,
                "Optimized %d/%d files",
                csv.count("Changed", CSV::positive),
                csv.size()
            );
        }
        Logger.info(this, "Summary build statistics collected successfully!");
    }
}
