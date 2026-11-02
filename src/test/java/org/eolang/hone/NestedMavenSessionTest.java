/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the outer Maven session settings that {@link OptimizeMojo}
 * passes to the nested {@code mvn} runs of {@code entry.sh} (see #1345).
 *
 * @since 0.76.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class NestedMavenSessionTest {

    @Test
    void passesOfflineModeToNestedMaven() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh must pass --offline to both nested jeo runs when the outer build is offline, or jeo tries to reach Central and fails the build",
            NestedMavenSessionTest.opts()
                .withEnv("OFFLINE", "true")
                .exec()
                .stdout(),
            Matchers.containsString("--offline")
        );
    }

    @Test
    void passesSettingsFileToNestedMaven(@Mktmp final Path temp) throws IOException {
        final Path work = temp.resolve("work");
        Files.createDirectories(work);
        Files.write(
            work.resolve("settings.xml"),
            "<settings/>".getBytes(StandardCharsets.UTF_8)
        );
        final Path custom = temp.resolve("custom.xml");
        Files.write(custom, "<settings/>".getBytes(StandardCharsets.UTF_8));
        MatcherAssert.assertThat(
            "the user settings file of the outer build must win over the one in WORKDIR, and only one --settings may reach mvn",
            NestedMavenSessionTest.opts()
                .withEnv("WORKDIR", work.toString())
                .withEnv("SETTINGS", custom.toString())
                .exec()
                .stdout(),
            Matchers.allOf(
                Matchers.containsString(String.format("--settings=%s", custom)),
                Matchers.not(
                    Matchers.containsString(
                        String.format("--settings=%s", work.resolve("settings.xml"))
                    )
                )
            )
        );
    }

    @Test
    void passesLocalRepositoryToNestedMaven() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh must point both nested jeo runs at the local repository of the outer build, or they resolve dependencies somewhere else",
            NestedMavenSessionTest.opts()
                .withEnv("LOCAL_REPO", "/tmp/hone-local-repo")
                .exec()
                .stdout(),
            Matchers.containsString("-Dmaven.repo.local=/tmp/hone-local-repo")
        );
    }

    @Test
    void addsNothingButTheDefaultRepository() throws IOException {
        final String repo = Paths.get(
            System.getProperty("user.home"), ".m2", "repository"
        ).toString();
        MatcherAssert.assertThat(
            "a normal build has nothing to add except its default repository, so no --offline and no --settings may appear",
            NestedMavenSessionTest.opts()
                .withEnv("OFFLINE", "false")
                .withEnv("LOCAL_REPO", repo)
                .exec()
                .stdout(),
            Matchers.allOf(
                Matchers.not(Matchers.containsString("--offline")),
                Matchers.not(Matchers.containsString("--settings")),
                Matchers.containsString(String.format("-Dmaven.repo.local=%s", repo))
            )
        );
    }

    @Test
    void keepsWorkdirSettingsOnDockerPath(@Mktmp final Path temp) throws IOException {
        final Path work = temp.resolve("hone");
        Files.createDirectories(work);
        Files.write(
            work.resolve("settings.xml"),
            "<settings/>".getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "the Docker path sets WORKDIR and no SETTINGS, so its settings.xml must still reach mvn",
            NestedMavenSessionTest.opts()
                .withEnv("WORKDIR", work.toString())
                .exec()
                .stdout(),
            Matchers.containsString(
                String.format("--settings=%s", work.resolve("settings.xml"))
            )
        );
    }

    @Test
    void passesSessionValuesToEntryScript() throws IOException {
        final String source = new String(
            Files.readAllBytes(
                Paths.get("src/main/java/org/eolang/hone/OptimizeMojo.java")
            ),
            StandardCharsets.UTF_8
        );
        MatcherAssert.assertThat(
            "withoutDocker() must hand the session values to entry.sh, or the nested mvn runs with the defaults",
            source.substring(source.indexOf("private void withoutDocker()")),
            Matchers.allOf(
                Matchers.containsString(
                    ".withEnv(\"OFFLINE\", Boolean.toString(this.session.isOffline()))"
                ),
                Matchers.containsString(
                    ".withEnv(\"LOCAL_REPO\", this.session.getLocalRepository().getBasedir()"
                ),
                Matchers.containsString(".withEnv(\"SETTINGS\","),
                Matchers.containsString(
                    "this.session.getRequest().getUserSettingsFile()"
                )
            )
        );
    }

    /*
     * Runs the very block of entry.sh that builds the options shared by both
     * nested jeo invocations, and returns whatever it put into "opts".
     */
    private static Jaxec opts() throws IOException {
        final Matcher matcher = Pattern.compile(
            "declare -a opts=\\([\\s\\S]*?(?=echo \"Using JEO version)"
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No opts array found in entry.sh");
        }
        return new Jaxec(
            "bash",
            "-c",
            String.format(
                "declare -a opts=()%n%s%nprintf '%%s\\n' \"${opts[@]}\"",
                matcher.group()
            )
        );
    }
}
