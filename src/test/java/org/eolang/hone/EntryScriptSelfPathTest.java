/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the way {@code entry.sh} resolves {@code SELF} before
 * {@code cd}-ing into it. A relative {@code SELF} still resolves fine at
 * the {@code cd} itself, but every later {@code "${SELF}/..."} reference
 * is then evaluated against the new, already-entered directory, doubling
 * the relative prefix and pointing nowhere (see #1077).
 *
 * @since 0.24.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class EntryScriptSelfPathTest {

    @Test
    void resolvesSelfToAnAbsolutePathBeforeCd() throws IOException {
        MatcherAssert.assertThat(
            "entry.sh must resolve SELF to an absolute path before cd-ing into it",
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/entry.sh")
                ),
                StandardCharsets.UTF_8
            ),
            Matchers.containsString("SELF=$(\"${RP}\" \"$(dirname \"$0\")\")")
        );
    }

    @Test
    void showsThatAnUnresolvedRelativeSelfBreaksAfterCd() throws IOException {
        MatcherAssert.assertThat(
            "cd-ing into a relative SELF and reusing it doubles the relative prefix",
            new Jaxec(
                "bash", "-c",
                "d=$(mktemp -d);mkdir $d/a;touch $d/a/m;cd $d;s=$(dirname a/e.sh);cd $s;cat $s/m"
            ).withCheck(false).execUnsafe().code(),
            Matchers.not(Matchers.equalTo(0))
        );
    }
}
