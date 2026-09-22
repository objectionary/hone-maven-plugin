/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.IoCheckedText;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the bytecode verification that {@link OptimizeMojo} asks jeo
 * to perform after assembly.
 *
 * @since 0.31.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoVerificationTest {

    @Test
    void verifiesAssembledBytecodeWhenSkipIsOff() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh cannot opt out of jeo verification when skipVerification is off, since an unloadable class must fail the build that made it, not the JVM that runs it (see #1102)",
            OptimizeMojoVerificationTest.assembleOpts("false"),
            Matchers.not(Matchers.containsString("jeo.assemble.skip.verification"))
        );
    }

    @Test
    void skipsVerificationWhenAsked() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh cannot ignore skipVerification=true, since a project that accepts the risk must be able to buy back the build time",
            OptimizeMojoVerificationTest.assembleOpts("true"),
            Matchers.containsString("-Djeo.assemble.skip.verification=true")
        );
    }

    @Test
    void keepsVerificationOnInPluginDescriptor() throws IOException {
        MatcherAssert.assertThat(
            "the skipVerification parameter cannot default to anything but false, since verification is the protection the plugin gets for free (see #1102)",
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("META-INF/maven/plugin.xml")
                )
            ).asString(),
            Matchers.containsString(
                "<skipVerification implementation=\"boolean\" default-value=\"false\">"
            )
        );
    }

    /*
     * Runs the very block of entry.sh that decides on the jeo verification
     * flag, with the given value of SKIP_VERIFICATION, and returns whatever
     * that block appended to the assemble options.
     */
    private static String assembleOpts(final String skip) throws IOException {
        final Matcher matcher = Pattern.compile(
            "if \\[ \"\\$\\{SKIP_VERIFICATION\\}\" == 'true' \\]; then\\n[\\s\\S]*?\\nfi\\n"
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException(
                "No SKIP_VERIFICATION condition in entry.sh"
            );
        }
        return new Jaxec(
            "bash",
            "-c",
            String.format(
                "declare -a assemble_opts=()%n%s%nprintf '%%s\\n' \"${assemble_opts[@]}\"",
                matcher.group()
            )
        ).withEnv("SKIP_VERIFICATION", skip).exec().stdout();
    }
}
