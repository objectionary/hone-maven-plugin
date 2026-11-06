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
 * @since 0.75.0
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
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/unphi/c/C.xmir"), "<c/>"
        );
        final String result = EntryScriptChangedClassesTest.result(temp, false);
        MatcherAssert.assertThat(
            "a class no rule touched must not be assembled, since the round trip drops the attributes XMIR cannot carry",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.not(Matchers.hasItem("a/A.xmir"))
        );
        MatcherAssert.assertThat(
            "a class a rule did change, and an XMIR with no counterpart in the disassembly, must still be assembled",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.containsInAnyOrder("b/B.xmir", "c/C.xmir")
        );
        MatcherAssert.assertThat(
            "the count must tell entry.sh that two classes changed",
            result,
            Matchers.containsString("changed=2\n")
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
        MatcherAssert.assertThat(
            "the assembly must actually run when something changed",
            EntryScriptChangedClassesTest.calls(temp),
            Matchers.containsString("jeo-maven-plugin")
        );
    }

    @Test
    void assemblesNothingWhenNoXmirChanged(@Mktmp final Path temp) throws IOException {
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/jeo-disassemble/a/A.xmir"), "<a/>"
        );
        EntryScriptChangedClassesTest.write(temp.resolve("hone/unphi/a/A.xmir"), "<a/>");
        MatcherAssert.assertThat(
            "the count must be zero, so entry.sh skips assembly altogether",
            EntryScriptChangedClassesTest.result(temp, false),
            Matchers.containsString("changed=0\n")
        );
        MatcherAssert.assertThat(
            "nothing changed, so jeo:assemble must not be called at all",
            Files.exists(temp.resolve("calls.txt")),
            Matchers.is(false)
        );
        MatcherAssert.assertThat(
            "an identical XMIR must not reach assembly",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.empty()
        );
    }

    @Test
    void keepsEverythingWhenPhinoIsSkipped(@Mktmp final Path temp) throws IOException {
        EntryScriptChangedClassesTest.write(
            temp.resolve("hone/jeo-disassemble/a/A.xmir"), "<a/>"
        );
        MatcherAssert.assertThat(
            "without the rules the disassembled XMIR equals itself, so nothing may be assembled",
            EntryScriptChangedClassesTest.result(temp, true),
            Matchers.containsString("changed=0\n")
        );
        MatcherAssert.assertThat(
            "nothing may be copied out of the disassembled tree when phino is skipped",
            EntryScriptChangedClassesTest.xmir(temp.resolve("hone/unphi-changed")),
            Matchers.empty()
        );
        MatcherAssert.assertThat(
            "skipping phino must skip the assembly too",
            Files.exists(temp.resolve("calls.txt")),
            Matchers.is(false)
        );
    }

    @Test
    void keepsTheDisassembledXmirWhenNoRuleChangesIt(@Mktmp final Path home)
        throws IOException {
        final Path phino = home.resolve("phino");
        Files.write(
            phino,
            String.join(
                "\n",
                "#!/usr/bin/env bash",
                "cat \"${@: -1}\"",
                "case \" $* \" in",
                "  *\" --output=xmir \"*) printf '<!-- reprinted -->' ;;",
                "esac",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        if (!phino.toFile().setExecutable(true)) {
            throw new IllegalStateException("Can't make the fake phino executable");
        }
        Files.write(home.resolve("simple.phr"), "rule".getBytes(StandardCharsets.UTF_8));
        final Path input = home.resolve("in.xmir");
        Files.write(input, "<xmir pretty=\"jeo\"/>\n".getBytes(StandardCharsets.UTF_8));
        new Jaxec(
            "bash",
            "-c",
            String.format(
                String.join(
                    "%n",
                    "set -e",
                    "export PATH=\"%1$s:${PATH}\" TARGET=\"%1$s\" HONE_RULES=\"%1$s/simple.phr\"",
                    "export PHINO_VERSION=0.0.1 HONE_GREP_IN= HONE_SMALL_STEPS=false",
                    "export HONE_MAX_CYCLES=1 HONE_MAX_DEPTH=1 HONE_STATISTICS=false HONE_VERBOSE=false HONE_DEBUG=false",
                    "bash %2$s rewrite 1/1 \"%1$s/a.phi\" \"%1$s/b.phi\" \"%1$s/in.xmir\" \"%1$s/out.xmir\""
                ),
                home,
                Paths.get(
                    "src/main/resources/org/eolang/hone/scaffolding/rewrite.sh"
                ).toAbsolutePath()
            )
        ).exec();
        MatcherAssert.assertThat(
            "a class no rule changed must keep the XMIR jeo printed, since phino's printer drifts and the class then looks changed",
            Files.readString(home.resolve("out.xmir"), StandardCharsets.UTF_8),
            Matchers.equalTo(Files.readString(input, StandardCharsets.UTF_8))
        );
    }

    @Test
    void filtersOnlyAfterTheXmirIsProduced() throws IOException {
        final String script = new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
            ),
            StandardCharsets.UTF_8
        );
        final int filter = script.indexOf(
            "changed_dir=\"${TARGET}/hone/unphi-changed\""
        );
        MatcherAssert.assertThat(
            "the filter cannot run before jeo disassembled, since it would see no XMIR at all",
            filter,
            Matchers.greaterThan(
                script.indexOf(
                    "org.eolang:jeo-maven-plugin:${JEO_VERSION}:disassemble"
                )
            )
        );
        MatcherAssert.assertThat(
            "the filter cannot run before rewrite.sh produced the XMIR it inspects",
            filter,
            Matchers.greaterThan(script.indexOf("bash \"${SELF}/rewrite.sh\""))
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
                    "export PATH=\"/usr/bin:/bin:/usr/sbin:/sbin:${PATH}\"",
                    "export TARGET=\"%1$s\" SKIP_PHINO=%2$s",
                    "declare -a assemble_opts=()",
                    "mvn() { printf '%%s\\n' \"$*\" >> \"%1$s/calls.txt\"; }",
                    "now() { printf '0\\n'; }",
                    "elapsed() { printf '0\\n'; }",
                    "record_timing() { :; }",
                    "%3$s",
                    "{ printf 'changed=%%s\\n' \"${changed}\"; printf '%%s\\n' \"${assemble_opts[@]}\"; } > result.txt"
                ),
                temp,
                skip,
                EntryScriptChangedClassesTest.block()
            )
        ).exec();
        return Files.readString(temp.resolve("result.txt"), StandardCharsets.UTF_8);
    }

    /*
     * The block of entry.sh that filters the XMIR, plus the guard that runs the
     * assembly only when something changed. They are joined even when the
     * script keeps other steps between them, so that both are executed here.
     */
    private static String block() throws IOException {
        final List<String> lines = Files.readAllLines(
            Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh"),
            StandardCharsets.UTF_8
        );
        final int start = lines.indexOf(
            "changed_dir=\"${TARGET}/hone/unphi-changed\""
        );
        final int count = lines.indexOf(
            "changed=$(find \"${changed_dir}\" -name '*.xmir' -type f -print | wc -l | xargs)"
        );
        final int guard = lines.indexOf("if [ \"${changed}\" -gt 0 ]; then");
        if (start < 0 || count < start || guard < 0) {
            throw new IllegalStateException(
                "No changed-XMIR filter in entry.sh"
            );
        }
        final int end = guard + lines.subList(guard, lines.size()).indexOf("fi");
        if (end < guard) {
            throw new IllegalStateException(
                "No guard around the jeo:assemble invocation in entry.sh"
            );
        }
        return String.join(
            "\n",
            String.join("\n", lines.subList(start, count + 1)),
            String.join("\n", lines.subList(guard, end + 1))
        );
    }

    private static String calls(final Path temp) throws IOException {
        return Files.readString(
            temp.resolve("calls.txt"), StandardCharsets.UTF_8
        );
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
