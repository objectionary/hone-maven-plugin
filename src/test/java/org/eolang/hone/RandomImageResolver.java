/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * This class is instantiated and then called by JUnit when
 * an argument of a test method is marked with the {@link RandomImage}
 * annotation.
 *
 * @since 0.1.0
 */
public final class RandomImageResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(final ParameterContext context,
        final ExtensionContext ext) {
        return context.getParameter().getType().equals(String.class)
            && context.isAnnotated(RandomImage.class);
    }

    @Override
    public Object resolveParameter(final ParameterContext context,
        final ExtensionContext ext) {
        return String.format(
            "hone-test:%s",
            context.getParameter().getDeclaringExecutable().getName()
        );
    }

}
