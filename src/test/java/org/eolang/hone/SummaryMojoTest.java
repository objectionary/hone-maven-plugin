/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.cactoos.bytes.BytesOf;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link SummaryMojo}.
 *
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class SummaryMojoTest {

    @Test
    @SuppressWarnings("deprecation")
    void collectsStatisticsOfEveryProject(@Mktmp final Path temp) throws Exception {
        final MavenProject client = SummaryMojoTest.project(
            temp, "client", "csv/hone-statistics-client.csv"
        );
        final MavenProject server = SummaryMojoTest.project(
            temp, "server", "csv/hone-statistics-server.csv"
        );
        final SummaryMojo mojo = new SummaryMojo();
        mojo.target = Files.createDirectories(temp.resolve("target")).toFile();
        mojo.session = new MavenSession(
            null,
            new DefaultMavenExecutionRequest(),
            (MavenExecutionResult) null,
            Arrays.asList(client, server)
        );
        mojo.exec();
        MatcherAssert.assertThat(
            "the summary goal must write the report of all projects",
            new TextOf(temp.resolve("target/hone-summary.csv")).asString(),
            Matchers.allOf(
                Matchers.containsString("org/eolang/hone/client/Client.phi"),
                Matchers.containsString("org/eolang/hone/server/Server.phi"),
                Matchers.containsString("1/2,"),
                Matchers.containsString("2/2,")
            )
        );
    }

    private static MavenProject project(final Path temp, final String name, final String csv)
        throws Exception {
        final Path dir = Files.createDirectories(temp.resolve(name));
        Files.write(
            dir.resolve("hone-statistics.csv"),
            new BytesOf(new ResourceOf(csv)).asBytes()
        );
        final MavenProject project = new MavenProject();
        final Build build = new Build();
        build.setDirectory(dir.toString());
        project.getModel().setBuild(build);
        return project;
    }
}
