/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.IoCheckedText;
import org.cactoos.text.TextOf;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This annotation disables JUnit tests when phino is
 * not runnable in command line.
 * @since 0.1.0
 */
public final class DisabledWithoutPhinoCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext ctx) {
        final ConditionEvaluationResult result;
        final String expected;
        try {
            expected = new IoCheckedText(
                new TextOf(
                    new ResourceOf("org/eolang/hone/default-phino-version.txt")
                )
            ).asString().trim();
        } catch (final IOException ex) {
            throw new IllegalStateException(
                "Cannot read the pinned phino version from default-phino-version.txt",
                ex
            );
        }
        if (new Phino().available(expected)) {
            result = ConditionEvaluationResult.enabled("Phino is available");
        } else {
            result = ConditionEvaluationResult.disabled("Phino is not available");
        }
        return result;
    }
}
