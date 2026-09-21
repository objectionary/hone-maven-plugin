/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link CSV}.
 * @since 0.1.0
 * @checkstyle AbbreviationAsWordInNameCheck (3 lines)
 */
@ExtendWith(MktmpResolver.class)
final class CSVTest {

    @Test
    void countsRowsMatchingCondition(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("test.csv");
        Files.write(
            path,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/3,a.phi,b.phi,5,1000",
                "2/3,c.phi,d.phi,0,0",
                "3/3,e.phi,f.phi,3,800",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "two files have Changed > 0",
            new CSV(path).count("Changed", v -> Integer.parseInt(v) > 0),
            Matchers.is(2)
        );
    }

    @Test
    void countsZeroWhenNoRowsMatch(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("test.csv");
        Files.write(
            path,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/2,a.phi,b.phi,0,0",
                "2/2,c.phi,d.phi,0,0",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "no files have Changed > 0",
            new CSV(path).count("Changed", v -> Integer.parseInt(v) > 0),
            Matchers.is(0)
        );
    }

    @Test
    void releasesFileHandleAfterParsing(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("handle.csv");
        Files.write(
            path,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/1,a.phi,b.phi,5,1000",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        new CSV(path).size();
        MatcherAssert.assertThat(
            "file handle must be released after parsing so the file can be deleted",
            Files.deleteIfExists(path),
            Matchers.is(true)
        );
    }

    @Test
    void flushesAndRereadsRoundTrip(@Mktmp final Path temp) throws Exception {
        final Path source = temp.resolve("input.csv");
        Files.write(
            source,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/2,a.phi,b.phi,5,1000",
                "2/2,c.phi,d.phi,3,800",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        final Path flushed = temp.resolve("output.csv");
        new CSV(source).flush(flushed);
        MatcherAssert.assertThat(
            "round-trip through flush must preserve row count",
            new CSV(flushed).size(),
            Matchers.is(2)
        );
    }

    @Test
    void combinesTwoCsvFiles(@Mktmp final Path temp) throws Exception {
        final Path first = temp.resolve("first.csv");
        Files.write(
            first,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/1,a.phi,b.phi,5,1000",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        final Path second = temp.resolve("second.csv");
        Files.write(
            second,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/1,c.phi,d.phi,3,800",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "combined CSV must contain rows from both files",
            new CSV(first).add(new CSV(second)).size(),
            Matchers.is(2)
        );
    }

    @Test
    void parsesHeaderOnlyCsv(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("test.csv");
        Files.write(
            path,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "header-only CSV has zero data rows",
            new CSV(path).size(),
            Matchers.is(0)
        );
    }

    @Test
    void countsZeroOnHeaderOnlyCsv(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("test.csv");
        Files.write(
            path,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "header-only CSV reports zero matches",
            new CSV(path).count("Changed", v -> Integer.parseInt(v) > 0),
            Matchers.is(0)
        );
    }
}
