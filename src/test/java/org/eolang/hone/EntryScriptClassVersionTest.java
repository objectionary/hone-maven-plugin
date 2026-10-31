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
 * Test case for the class-version guard in {@code entry.sh}, which excluded
 * pre-Java-16 classes from the streams rules but read the version of every
 * {@code *.class} file without checking the {@code CAFEBABE} magic first, so a
 * resource file merely named {@code *.class} reached jeo and failed the build.
 *
 * @since 0.31.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
@ExtendWith(MktmpResolver.class)
final class EntryScriptClassVersionTest {

    @Test
    void excludesFileWithoutMagic(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "a resource file named *.class that is not a class file must be excluded from the disassembly, not handed to jeo (see #1342)",
            EntryScriptClassVersionTest.outdated(home),
            Matchers.allOf(
                Matchers.containsString("Bogus.class"),
                Matchers.containsString("Empty.class")
            )
        );
    }

    @Test
    void namesTheFileItLeavesOut(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "the guard must say which file it left out and why, instead of leaking an od error or failing the build (see #1342)",
            EntryScriptClassVersionTest.output(home),
            Matchers.allOf(
                Matchers.containsString("Bogus.class"),
                Matchers.containsString("is not a class file")
            )
        );
    }

    @Test
    void keepsRealClasses(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "a real class at version 61 must still reach the streams rules, since only non-class files are the problem (see #1342)",
            EntryScriptClassVersionTest.outdated(home),
            Matchers.not(Matchers.containsString("good.class"))
        );
    }

    @Test
    void excludesOldVersion(@Mktmp final Path home) throws IOException {
        MatcherAssert.assertThat(
            "a pre-Java-16 class must still be excluded exactly as before the magic check (see #930)",
            EntryScriptClassVersionTest.outdated(home),
            Matchers.containsString("old.class")
        );
    }

    /*
     * Runs the guard block of entry.sh over a temp target/classes holding the
     * four fixtures and returns the paths it put into "outdated".
     */
    private static String outdated(final Path home) throws IOException {
        final String marker = "OUTDATED:";
        final String out = EntryScriptClassVersionTest.output(home);
        return out.substring(out.indexOf(marker) + marker.length());
    }

    /*
     * Runs the guard block of entry.sh over a temp target/classes holding the
     * four fixtures and returns everything it printed.
     */
    private static String output(final Path home) throws IOException {
        final Path classes = Files.createDirectories(home.resolve("classes"));
        Files.write(
            classes.resolve("good.class"),
            new byte[] {
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0x3D,
            }
        );
        Files.write(
            classes.resolve("old.class"),
            new byte[] {
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0x34,
            }
        );
        Files.write(
            classes.resolve("Bogus.class"),
            "this is not a class file at all".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(classes.resolve("Empty.class"), new byte[0]);
        final Matcher matcher = Pattern.compile(
            "declare -a outdated=\\(\\)[\\s\\S]*?done < <\\(find -H[^\\n]*\\n"
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
                "No class version guard in entry.sh"
            );
        }
        return new Jaxec(
            "bash",
            "-c",
            String.format(
                "%s%nprintf 'OUTDATED:\\n%%s\\n' \"${outdated[@]}\"",
                matcher.group()
            )
        ).withEnv("TARGET", home.toString()).withEnv("CLASSES", "classes").exec().stdout();
    }
}
