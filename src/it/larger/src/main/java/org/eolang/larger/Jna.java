/*
 * The MIT License (MIT)
 *
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import com.sun.jna.Library;
import com.sun.jna.Native;

interface Jna extends Library {
    Jna INSTANCE = Native.load("c", Jna.class);
    int getpid();
}
