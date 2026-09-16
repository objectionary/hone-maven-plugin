/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.yegor256.Jaxec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the single thread task loop of {@code rewrite.sh}.
 *
 * @since 0.6.1
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoTasksTest {

    @Test
    void runsEveryTaskWhenOneOfThemReadsInput() throws Exception {
        final Matcher matcher = Pattern.compile(
            "  while IFS= read -r cmd.*?done.*?\\n", Pattern.DOTALL
        ).matcher(
            new String(
                Files.readAllBytes(
                    Paths.get("src/main/resources/org/eolang/hone/scaffolding/rewrite.sh")
                ),
                StandardCharsets.UTF_8
            )
        );
        if (!matcher.find()) {
            throw new IllegalStateException("No single thread task loop found in rewrite.sh");
        }
        try (Mktemp temp = new Mktemp()) {
            final Path tasks = temp.path().resolve("tasks.txt");
            Files.write(
                tasks,
                String.format("echo one%nhead -n 1 > /dev/null%necho three%n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            MatcherAssert.assertThat(
                "a task that reads input must not eat the tasks that follow it",
                new Jaxec(
                    "bash", "-c",
                    String.format(
                        "set -e%ntasks=%s%n%s", tasks, matcher.group()
                    )
                ).exec().stdout().trim(),
                Matchers.equalTo(String.format("one%nthree"))
            );
        }
    }
}
