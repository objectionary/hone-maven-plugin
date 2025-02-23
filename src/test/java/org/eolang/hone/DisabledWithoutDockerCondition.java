/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This annotation disables JUnit tests when Docker is
 * not runnable in command line.
 *
 * @since 0.1.0
 */
public final class DisabledWithoutDockerCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext ctx) {
        ConditionEvaluationResult result;
        try {
            new Docker().exec("ps");
            result = ConditionEvaluationResult.enabled("Docker is available");
        } catch (final IOException ex) {
            result = ConditionEvaluationResult.disabled("Docker is not available");
        }
        return result;
    }
}
