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
import org.cactoos.io.ResourceOf;
import org.cactoos.text.IoCheckedText;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Test case for {@link OptimizeMojo}, against its default {@code grep-in}
 * pre-filter pattern and the {@code rewrite.sh} scaffolding script.
 * @since 0.1.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(MktmpResolver.class)
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoDefaultGrepInTest {

    @Test
    void matchesStandaloneMapByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'map' byte sequence",
            OptimizeMojoDefaultGrepInTest.grepInMatches(dir, "<o as=\"data\">6D-61-70</o>"),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneFilterByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'filter' byte sequence",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">66-69-6C-74-65-72</o>"
            ),
            Matchers.is(true)
        );
    }

    @Test
    void ignoresMapBytesEmbeddedInLongerStringInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must not match 'map' bytes embedded in 'mapped/X' (see #449)",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">6D-61-70-70-65-64-2F-58</o>"
            ),
            Matchers.is(false)
        );
    }

    @Test
    void ignoresFilterBytesEmbeddedInLongerStringInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must not match 'filter' bytes embedded in 'filtered'",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">66-69-6C-74-65-72-65-64</o>"
            ),
            Matchers.is(false)
        );
    }

    @Test
    void matchesMapToIntByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'mapToInt' byte sequence (see #705)",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">6D-61-70-54-6F-49-6E-74</o>"
            ),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneSkipByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'skip' byte sequence (see #705)",
            OptimizeMojoDefaultGrepInTest.grepInMatches(dir, "<o as=\"data\">73-6B-69-70</o>"),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneDropWhileByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'dropWhile' byte sequence, since the method is fused by the pipeline but absent from the default pre-filter (see #449)",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">64-72-6F-70-57-68-69-6C-65</o>"
            ),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneFlatMapByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'flatMap' byte sequence (see #705)",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">66-6C-61-74-4D-61-70</o>"
            ),
            Matchers.is(true)
        );
    }

    @Test
    void matchesStandaloneDistinctByteSequenceInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must match a standalone 'distinct' byte sequence (see #705)",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">64-69-73-74-69-6E-63-74</o>"
            ),
            Matchers.is(true)
        );
    }

    @Test
    void ignoresMapToIntBytesEmbeddedInLongerStringInDefaultGrepIn(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "default grep-in must not match 'mapToInt' bytes embedded in a longer sequence",
            OptimizeMojoDefaultGrepInTest.grepInMatches(
                dir, "<o as=\"data\">6D-61-70-54-6F-49-6E-74-65-72</o>"
            ),
            Matchers.is(false)
        );
    }

    @Test
    void hasZeroDefaultThreadsInPluginDescriptor() throws Exception {
        MatcherAssert.assertThat(
            "default 'threads' parameter must be 0 so that all CPUs are used by default (see #500)",
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("META-INF/maven/plugin.xml")
                )
            ).asString(),
            Matchers.containsString(
                "<threads implementation=\"int\" default-value=\"0\">"
            )
        );
    }

    @Test
    void doesNotPlaceParallelTmpdirOnHostMountedVolume() throws Exception {
        MatcherAssert.assertThat(
            "rewrite.sh must not point parallel --tmpdir at the host-mounted ${TARGET} volume, since virtiofs (Docker Desktop on macOS) makes fstat fail with ENOENT on deleted-but-open files, which breaks parallel's grouped output and triggers 'Cant dup STDOUT: No such file or directory' (see #506)",
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("org/eolang/hone/scaffolding/rewrite.sh")
                )
            ).asString(),
            Matchers.not(Matchers.containsString("--tmpdir=${PARALLEL_HOME}"))
        );
    }

    @Test
    void doesNotRetryFailingJobsInfinitely() throws Exception {
        MatcherAssert.assertThat(
            "rewrite.sh must not pass --retries=0 to parallel, since in GNU parallel that means infinite retries, so a deterministically failing phino job loops forever instead of failing fast via --halt=now,fail=1 (see #720)",
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("org/eolang/hone/scaffolding/rewrite.sh")
                )
            ).asString(),
            Matchers.not(Matchers.containsString("--retries=0"))
        );
    }

    @Test
    void matchesWithAValidGrepInPattern(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "a valid pattern that matches must be reported as a match",
            OptimizeMojoDefaultGrepInTest.grepInCode(dir, "(66-69-6C-74-65-72|6D-61-70)"),
            Matchers.is(0)
        );
    }

    @Test
    void missesWithAGrepInPatternWithoutMatches(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "a valid pattern without matches must be reported as a miss",
            OptimizeMojoDefaultGrepInTest.grepInCode(dir, "(99-99)"),
            Matchers.is(1)
        );
    }

    @Test
    void failsOnAnInvalidGrepInPattern(@Mktmp final Path dir)
        throws IOException {
        MatcherAssert.assertThat(
            "an invalid pattern must be reported as an error, not as a miss (see #825)",
            OptimizeMojoDefaultGrepInTest.grepInCode(dir, "("),
            Matchers.is(2)
        );
    }

    /**
     * Runs the default {@code grep-in} pattern through the very tool that
     * consumes it in {@code rewrite.sh} ({@code grep -E}), so the pattern's
     * dialect is validated against its real consumer rather than against
     * {@link java.util.regex.Pattern} (see #671).
     * @param dir Temporary directory to hold the sample file
     * @param content The line of XMIR to grep through
     * @return TRUE if {@code grep -E} finds a match
     * @throws IOException If the sample file cannot be written
     */
    private static boolean grepInMatches(final Path dir, final String content)
        throws IOException {
        final Path file = dir.resolve("sample.xmir");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return new Jaxec(
            "grep", "-qE", OptimizeMojo.DEFAULT_GREP_IN, file.toString()
        ).withCheck(false).execUnsafe().code() == 0;
    }

    /**
     * Run the {@code grep_in_check} sub-command of the real
     * {@code rewrite.sh} against a sample XMIR and return its exit code.
     * @param dir Temporary directory for the script and the sample file
     * @param pattern The pattern to verify
     * @return The exit code of {@code grep_in_check}: 0 = match,
     *  1 = no match, 2 = invalid pattern
     * @throws IOException If the script or the sample file cannot be written
     */
    private static int grepInCode(final Path dir, final String pattern)
        throws IOException {
        final Path script = dir.resolve("rewrite.sh");
        Files.write(
            script,
            new IoCheckedText(
                new TextOf(
                    new ResourceOf("org/eolang/hone/scaffolding/rewrite.sh")
                )
            ).asString().getBytes(StandardCharsets.UTF_8)
        );
        final Path file = dir.resolve("sample.xmir");
        Files.write(
            file,
            String.format("66-69-6C-74-65-72%n").getBytes(StandardCharsets.UTF_8)
        );
        return new Jaxec(
            "bash", script.toString(), "grep_in_check", pattern, file.toString()
        ).withCheck(false).execUnsafe().code();
    }
}
