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
            new CSV(path).count("Changed", CSV::positive),
            Matchers.is(2)
        );
    }

    @Test
    void countsWithoutThrowingOnMalformedChangedCell(@Mktmp final Path temp)
        throws Exception {
        final Path path = temp.resolve("test.csv");
        Files.write(
            path,
            String.join(
                System.lineSeparator(),
                "ID,Before,After,Changed,LinesPerSec",
                "1/3,a.phi,b.phi,5,1000",
                "2/3,c.phi,d.phi,,0",
                "3/3,e.phi,f.phi,abc,0",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "an empty or non-numeric 'Changed' cell must not break the count (see #864)",
            new CSV(path).count("Changed", CSV::positive),
            Matchers.is(2)
        );
    }

    @Test
        void readsAFileThatCarriesOnlyItsHeader(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("empty.csv");
        Files.write(
            path,
            "ID,Before,After,Changed,LinesPerSec\n".getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "a file with a header and no rows must count nothing, not fail",
            new CSV(path).count("Changed", CSV::positive),
            Matchers.is(0)
        );
    }

    @Test
    void keepsTheColumnsOfAFileThatCarriesOnlyItsHeader(@Mktmp final Path temp) throws Exception {
        final Path header = temp.resolve("header.csv");
        Files.write(
            header,
            "ID,Before,After,Changed,LinesPerSec\n".getBytes(StandardCharsets.UTF_8)
        );
        final Path full = temp.resolve("full.csv");
        Files.write(
            full,
            "ID,Before,After,Changed,LinesPerSec\n1/1,a.phi,b.phi,5,1000\n".getBytes(StandardCharsets.UTF_8)
        );
        MatcherAssert.assertThat(
            "the columns of an empty file must survive being added to another one",
            new CSV(header).add(new CSV(full)).count("Changed", CSV::positive),
            Matchers.is(1)
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

    @Test
    void parsesCompletelyEmptyCsv(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("empty.csv");
        Files.write(path, new byte[0]);
        MatcherAssert.assertThat(
            "empty CSV has zero data rows without throwing IndexOutOfBoundsException",
            new CSV(path).size(),
            Matchers.is(0)
        );
    }

    @Test
    void countsZeroOnCompletelyEmptyCsv(@Mktmp final Path temp) throws Exception {
        final Path path = temp.resolve("empty.csv");
        Files.write(path, new byte[0]);
        MatcherAssert.assertThat(
            "empty CSV reports zero matches without throwing IndexOutOfBoundsException",
            new CSV(path).count("Changed", v -> Integer.parseInt(v) > 0),
            Matchers.is(0)
        );
    }

    @Test
    void mergesHeadersWhenAddingCsvsWithDifferentColumns(@Mktmp final Path temp)
        throws Exception {
        final Path left = temp.resolve("left.csv");
        final Path right = temp.resolve("right.csv");
        final Path merged = temp.resolve("merged.csv");
        Files.write(
            left,
            String.join(
                System.lineSeparator(),
                "ID,Changed",
                "1,3",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            right,
            String.join(
                System.lineSeparator(),
                "ID,Before",
                "2,5",
                ""
            ).getBytes(StandardCharsets.UTF_8)
        );
        new CSV(left).add(new CSV(right)).flush(merged);
        MatcherAssert.assertThat(
            "the merged CSV keeps the union of both headers (see #865)",
            new String(Files.readAllBytes(merged), StandardCharsets.UTF_8),
            Matchers.allOf(
                Matchers.containsString("ID,Changed,Before"),
                Matchers.containsString("1,3,"),
                Matchers.containsString("2,,5")
            )
        );
    }
}
