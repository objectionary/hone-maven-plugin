/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.gradle;

/**
 * Simple application that builds a greeting message that
 * is supposed to be modified by the Hone plugin. The number
 * 33 is used here as a placeholder that is expected to be
 * replaced by the number 42.
 * @since 1.0
 */
public final class App {
    public String txt() {
        return String.format("int is %d\n", 33);
    }
}
