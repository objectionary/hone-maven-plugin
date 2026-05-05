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
}
