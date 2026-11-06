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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for the exclude globs {@code entry.sh} builds for pre-Java-16
 * classes. The list is comma-joined and Maven splits it at every comma, so a
 * project path with a comma produced two broken globs, the old class was not
 * excluded, and its {@code Stream.mapMulti} failed on Java 8 (see #1347).
 * A wildcard that matches the comma also matches a sibling such as
 * {@code axb.class}, so such classes are staged instead.
 * @since 0.33.0
 */
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptExcludesTest {

    @Test
    void doesNotStageTheClassWithAComma(@Mktmp final Path home) throws IOException {
        final Path root = Files.createDirectories(home.resolve("a,b/target/classes/old"));
        final Path outdated = Files.write(root.resolve("a,b.class"), new byte[] {1});
        final Path stage = Files.createDirectories(home.resolve("stage"));
        EntryScriptExcludesTest.stage(stage, root, outdated);
        MatcherAssert.assertThat(
            "the old class with a comma in its name must stay out of the stage",
            Files.exists(stage.resolve("a,b.class")),
            Matchers.is(false)
        );
    }

    @Test
    void stagesTheSiblingWithoutAComma(@Mktmp final Path home) throws IOException {
        final Path root = Files.createDirectories(home.resolve("a,b/target/classes/old"));
        final Path outdated = Files.write(root.resolve("a,b.class"), new byte[] {1});
        Files.write(root.resolve("axb.class"), new byte[] {1});
        final Path stage = Files.createDirectories(home.resolve("stage"));
        EntryScriptExcludesTest.stage(stage, root, outdated);
        MatcherAssert.assertThat(
            "the sibling without a comma must stay eligible, or the module has nothing to optimize",
            Files.exists(stage.resolve("axb.class")),
            Matchers.is(true)
        );
    }

    @Test
    void pointsTheStageAtTheOriginalSibling(@Mktmp final Path home) throws IOException {
        final Path root = Files.createDirectories(home.resolve("a,b/target/classes/old"));
        final Path outdated = Files.write(root.resolve("a,b.class"), new byte[] {1});
        final Path sibling = Files.write(root.resolve("axb.class"), new byte[] {1});
        final Path stage = Files.createDirectories(home.resolve("stage"));
        EntryScriptExcludesTest.stage(stage, root, outdated);
        MatcherAssert.assertThat(
            "the staged class must point at the original file",
            stage.resolve("axb.class").toRealPath(),
            Matchers.equalTo(sibling.toRealPath())
        );
    }

    @Test
    void doesNotStageTheOldClassInACommaPath(@Mktmp final Path home) throws IOException {
        final Path root = Files.createDirectories(home.resolve("a,b/target/classes/old"));
        final Path outdated = Files.write(root.resolve("Old.class"), new byte[] {1});
        final Path stage = Files.createDirectories(home.resolve("stage"));
        EntryScriptExcludesTest.stage(stage, root, outdated);
        MatcherAssert.assertThat(
            "the old class must stay out of the stage when the comma is in the project path",
            Files.exists(stage.resolve("Old.class")),
            Matchers.is(false)
        );
    }

    @Test
    void stagesTheSuitableClassInACommaPath(@Mktmp final Path home) throws IOException {
        final Path root = Files.createDirectories(home.resolve("a,b/target/classes/old"));
        final Path outdated = Files.write(root.resolve("Old.class"), new byte[] {1});
        Files.write(root.resolve("New.class"), new byte[] {1});
        final Path stage = Files.createDirectories(home.resolve("stage"));
        EntryScriptExcludesTest.stage(stage, root, outdated);
        MatcherAssert.assertThat(
            "the suitable class must still reach the stage",
            Files.exists(stage.resolve("New.class")),
            Matchers.is(true)
        );
    }

    @Test
    void pointsTheStageAtTheOriginalClass(@Mktmp final Path home) throws IOException {
        final Path root = Files.createDirectories(home.resolve("a,b/target/classes/old"));
        final Path outdated = Files.write(root.resolve("Old.class"), new byte[] {1});
        final Path sibling = Files.write(root.resolve("New.class"), new byte[] {1});
        final Path stage = Files.createDirectories(home.resolve("stage"));
        EntryScriptExcludesTest.stage(stage, root, outdated);
        MatcherAssert.assertThat(
            "the staged class must point at the original file",
            stage.resolve("New.class").toRealPath(),
            Matchers.equalTo(sibling.toRealPath())
        );
    }

    @Test
    void escapesOtherGlobCharacters() throws IOException {
        MatcherAssert.assertThat(
            "every glob character must keep its escape (see #1205)",
            EntryScriptExcludesTest.escaped("/tmp/hone/a,b[1]/target/classes/old/Old.class"),
            Matchers.containsString("\\[1\\]")
        );
    }

    @Test
    void matchesTheClassWithABracketAndAComma() throws IOException {
        final String classfile = "/tmp/hone/a,b[1]/target/classes/old/Old.class";
        MatcherAssert.assertThat(
            "the escaped glob must still match the class with a bracketed directory",
            FileSystems.getDefault().getPathMatcher(
                String.format("glob:%s", EntryScriptExcludesTest.escaped(classfile))
            ).matches(Paths.get(classfile)),
            Matchers.is(true)
        );
    }

    private static void stage(final Path stage, final Path root, final Path outdated)
        throws IOException {
        new Jaxec(
            "bash", "-c",
            String.format(
                "RP=realpath%n%s%nstage_classes \"%s\" \"%s\" \"%s\"",
                EntryScriptExcludesTest.function(), stage, root, outdated
            )
        ).exec();
    }

    private static String function() throws IOException {
        final String text = new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
            ),
            StandardCharsets.UTF_8
        );
        final int start = text.indexOf("function stage_classes {");
        final String end = String.format("%n}%n");
        return text.substring(start, text.indexOf(end, start) + end.length());
    }

    private static String escaped(final String classfile) throws IOException {
        final String text = new String(
            Files.readAllBytes(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
            ),
            StandardCharsets.UTF_8
        );
        final int start = text.indexOf("sed '", text.indexOf("globs+=(")) + 5;
        return new Jaxec("sed", text.substring(start, text.indexOf('\'', start)))
            .withStdin(classfile)
            .withCheck(false)
            .execUnsafe()
            .stdout()
            .trim();
    }
}
