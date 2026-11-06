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
import java.util.List;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the filter that lets {@code entry.sh} assemble only the XMIR
 * that differs from what jeo disassembled (see #1343).
 *
 * @since 0.73.5
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptChangedClassesTest {

    @Test
    void assemblesOnlyChangedXmir(@Mktmp final Path temp) throws IOException {
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/jeo-disassemble/a/A.xmir"), "<a/>"
        );
        EntryScriptChangedClassesTest.write(temp.resolve("hone/unphi/a/A.xmir"), "<a/>");
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/jeo-disassemble/b/B.xmir"), "<b/>"
        );
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/unphi/b/B.xmir"), "<b changed=\"true\"/>"
        );
        final String result = EntryScriptChangedClassesTest.result(temp, false);
        MatcherAssert.assertThat(
            "a class no rule touched must not be assembled, since the round trip drops the attributes XMIR cannot carry",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.not(Matchers.hasItem("a/A.xmir"))
        );
        MatcherAssert.assertThat(
            "a class a rule did change must still be assembled",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.contains("b/B.xmir")
        );
        MatcherAssert.assertThat(
            "the count must tell entry.sh that one class changed",
            result,
            Matchers.containsString("changed=1\n")
        );
        MatcherAssert.assertThat(
            "jeo must read the filtered directory, or the unchanged classes come back rewritten",
            result,
            Matchers.containsString(
                String.format(
                    "-Djeo.assemble.sourcesDir=%s/hone/unphi-changed",
                    temp
                )
            )
        );
    }

    @Test
    void assemblesNothingWhenNoXmirChanged(@Mktmp final Path temp) throws IOException {
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/jeo-disassemble/a/A.xmir"), "<a/>"
        );
        EntryScriptChangedClassesTest.write(temp.resolve("hone/unphi/a/A.xmir"), "<a/>");
        MatcherAssert.assertThat(
            "an identical XMIR must not reach assembly",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.empty()
        );
        MatcherAssert.assertThat(
            "the count must be zero, so entry.sh skips assembly altogether",
            EntryScriptChangedClassesTest.result(temp, false),
            Matchers.containsString("changed=0\n")
        );
    }

    @Test
    void keepsEverythingWhenPhinoIsSkipped(@Mktmp final Path temp) throws IOException {
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/jeo-disassemble/a/A.xmir"), "<a/>"
        );
        MatcherAssert.assertThat(
            "without the rules the disassembled XMIR equals itself, so no class may be assembled",
            EntryScriptChangedClassesTest.result(temp, true),
            Matchers.containsString("changed=0\n")
        );
        MatcherAssert.assertThat(
            "nothing may be copied out of the disassembled tree when phino is skipped",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.empty()
        );
    }

    private static String result(final Path temp, final boolean skip) throws IOException {
        new Jaxec(
            "bash",
            "-c",
            String.format(
                String.join(
                    "%n",
                    "set -e",
                    "cd \"%1$s\"",
                    "export TARGET=\"%1$s\" SKIP_PHINO=%2$s",
                    "declare -a assemble_opts=()",
                    "%3$s",
                    "{ printf 'changed=%%s\\n' \"${changed}\"; printf '%%s\\n' \"${assemble_opts[@]}\"; } > result.txt"
                ),
                temp,
                skip,
                EntryScriptChangedClassesTest.block()
            )
        ).exec();
        return Files.readString(
            temp.resolve("result.txt"), StandardCharsets.UTF_8
        );
    }

    /*
     * The very block of entry.sh that filters the XMIR before assembly.
     */
    private static String block() throws IOException {
        final List<String> lines = Files.readAllLines(
            Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh"),
            StandardCharsets.UTF_8
        );
        final int start = lines.indexOf(
            "changed_dir=\"${TARGET}/hone/unphi-changed\""
        );
        final int end = lines.indexOf(
            "changed=$(find \"${changed_dir}\" -name '*.xmir' -type f -print | wc -l | xargs)"
        );
        if (start < 0 || end < start) {
            throw new IllegalStateException(
                "No changed-XMIR filter in entry.sh"
            );
        }
        return String.join("\n", lines.subList(start, end + 1));
    }

    private static void write(final Path file, final String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> xmir(final Path dir) throws IOException {
        final List<String> found;
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                found = walk.filter(path -> path.toString().endsWith(".xmir"))
                    .map(path -> dir.relativize(path).toString())
                    .toList();
            }
        } else {
            found = List.of();
        }
        return found;
    }
}
