/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

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
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext ctx) {
        final ConditionEvaluationResult result;
        if (new Phino().available("0.0.0.43")) {
            result = ConditionEvaluationResult.enabled("Phino is available");
        } else {
            result = ConditionEvaluationResult.disabled("Phino is not available");
        }
        return result;
    }
}
