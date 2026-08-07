/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link Rules}.
 * @since 0.1.0
 */
@ExtendWith(MktmpResolver.class)
final class RulesTest {

    @Test
    void filtersAndSaves(@Mktmp final Path temp) throws Exception {
        final Rules rules = new Rules("n*,aaa*,*{,!f*");
        rules.copyTo(temp.resolve("a/b/c"));
        MatcherAssert.assertThat(
            String.format("file must be written, because of %s", rules),
            temp.resolve("a/b/c/none.yml").toFile().exists(),
            Matchers.is(true)
        );
    }

    @Test
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    void skipsSome(@Mktmp final Path temp) throws Exception {
        final Rules rules = new Rules("!none,33*");
        rules.copyTo(temp.resolve("a/b/c"));
        MatcherAssert.assertThat(
            String.format("file must be written, because of %s", rules),
            temp.resolve("a/b/c/33-to-42.yml").toFile().exists(),
            Matchers.is(true)
        );
        MatcherAssert.assertThat(
            String.format("file must be absent, because of %s", rules),
            temp.resolve("a/b/c/none.yml").toFile().exists(),
            Matchers.is(false)
        );
    }

    @Test
    void discoversOneRuleFromClasspathWithoutSuffix() {
        MatcherAssert.assertThat(
            "Should discover none from classpath (without YML suffix)",
            new Rules("none").yamls(),
            Matchers.iterableWithSize(1)
        );
    }

    @Test
    void discoversNothingWithSuffix() {
        MatcherAssert.assertThat(
            "Should NOT discover none.yml from classpath",
            new Rules("none.yml").yamls(),
            Matchers.emptyIterable()
        );
    }

    @Test
    void discoversRulesFromClasspath() {
        MatcherAssert.assertThat(
            "Should discover none.yml from classpath",
            new Rules("*").yamls(),
            Matchers.hasItems(
                "none.yml",
                "streams/7xx/701-static-lambda-to-invokedynamic.phr"
            )
        );
    }

    @Test
    void copiesAllRulesFromClasspath(@Mktmp final Path temp) throws IOException {
        new Rules("*").copyTo(temp.resolve("copies"));
        MatcherAssert.assertThat(
            "Should copy .phr rules too",
            temp.resolve(
                "copies/streams/7xx/701-static-lambda-to-invokedynamic.phr"
            ).toFile().exists(),
            Matchers.is(true)
        );
    }

    @Test
    void mintsSyntheticNamesInDisjointNamespaces(@Mktmp final Path temp) throws IOException {
        new Rules("*").copyTo(temp);
        final Pattern minting = Pattern.compile(
            "function:\\s*random-string\\s*\\R\\s*args:\\s*\\[\\s*'\"([^\"]+)\""
        );
        final Map<String, Collection<String>> minters = new HashMap<>(0);
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(temp)) {
            files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        for (final Path file : files) {
            final Matcher found = minting.matcher(
                Files.readString(file, StandardCharsets.UTF_8)
            );
            while (found.find()) {
                minters.computeIfAbsent(found.group(1), key -> new HashSet<>(0))
                    .add(temp.relativize(file).toString());
            }
        }
        MatcherAssert.assertThat(
            "two rules cannot mint synthetic names from one pattern, since every phino process draws the same sequence and the duplicates collide inside one class (see #777)",
            minters.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::toString)
                .collect(Collectors.toList()),
            Matchers.empty()
        );
    }

    @Test
    void yamlsReturnsSortedList() {
        final Iterable<String> yamls = new Rules("*").yamls();
        String previous = null;
        for (final String yaml : yamls) {
            if (previous != null) {
                MatcherAssert.assertThat(
                    String.format(
                        "yamls() is not sorted: '%s' > '%s'",
                        previous, yaml
                    ),
                    previous.compareTo(yaml),
                    Matchers.lessThanOrEqualTo(0)
                );
            }
            previous = yaml;
        }
    }
}
