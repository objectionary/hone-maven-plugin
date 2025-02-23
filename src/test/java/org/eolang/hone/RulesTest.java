/*
 * The MIT License (MIT)
 *
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link Rules}.
 *
 * @since 0.1.0
 */
final class RulesTest {

    @Test
    void filtersAndSaves() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Rules rules = new Rules("n*,aaa*,*{,!f*");
            rules.copyTo(temp.path().resolve("a/b/c"));
            MatcherAssert.assertThat(
                String.format("file must be written, because of %s", rules),
                temp.path().resolve("a/b/c/none.yml").toFile().exists(),
                Matchers.is(true)
            );
        }
    }

    @Test
    void skipsSome() throws Exception {
        try (Mktemp temp = new Mktemp()) {
            final Rules rules = new Rules("!none,t*");
            rules.copyTo(temp.path().resolve("a/b/c"));
            MatcherAssert.assertThat(
                String.format("file must be written, because of %s", rules),
                temp.path().resolve("a/b/c/thirty-three.yml").toFile().exists(),
                Matchers.is(true)
            );
            MatcherAssert.assertThat(
                String.format("file must be absent, because of %s", rules),
                temp.path().resolve("a/b/c/none.yml").toFile().exists(),
                Matchers.is(false)
            );
        }
    }
}
