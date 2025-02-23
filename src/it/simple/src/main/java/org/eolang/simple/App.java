/*
 * The MIT License (MIT)
 *
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.simple;

public class App {
    public static void main(String[] args) {
        double angle = 33.0;
        double sin = Math.sin(angle);
        System.out.printf("sin(%f) = %f\n", angle, sin);
    }
}
