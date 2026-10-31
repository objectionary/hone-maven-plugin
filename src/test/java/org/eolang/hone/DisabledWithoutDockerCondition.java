/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

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

    /**
     * The Docker to probe.
     */
    private final Docker docker;

    /**
     * Public ctor, for JUnit to call reflectively.
     */
    public DisabledWithoutDockerCondition() {
        this(new Docker());
    }

    /**
     * Ctor.
     *
     * @param dkr The Docker to probe
     */
    DisabledWithoutDockerCondition(final Docker dkr) {
        this.docker = dkr;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext ctx) {
        final ConditionEvaluationResult result;
        if (this.docker.available()) {
            result = ConditionEvaluationResult.enabled("Docker is available");
        } else {
            result = ConditionEvaluationResult.disabled("Docker is not available");
        }
        return result;
    }
}
