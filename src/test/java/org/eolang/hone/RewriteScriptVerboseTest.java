/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the verbose messages of {@code rewrite.sh}. The shell expands
 * every substitution in the arguments of {@code verbose} before the function
 * decides whether to print, so a message whose text costs a {@code du} or a
 * {@code diff} paid for it on every class, with verbosity off (see #1041).
 *
 * @since 0.30.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class RewriteScriptVerboseTest {

    @Test
    void buildsNoMessageTheShellWouldThrowAway() throws IOException {
        final Collection<String> costly = new ArrayList<>(0);
        for (final String line
            : Files.readAllLines(
                Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh"),
                StandardCharsets.UTF_8
            )) {
            if (line.trim().startsWith("verbose \"") && line.contains("$(")) {
                costly.add(line.trim());
            }
        }
        MatcherAssert.assertThat(
            "a verbose message must not run a command to build its text, since the shell runs it even when nothing is printed",
            costly,
            Matchers.empty()
        );
    }
}
