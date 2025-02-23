/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import org.cactoos.io.ResourceOf;
import org.cactoos.text.Capitalized;
import org.cactoos.text.TextOf;

class Book implements Material {
    private final String мойTitle;
    private byte[] мойData;
    Book(String t, byte[] d) {
        мойTitle = t;
        мойData = d;
    }
    @Override
    public String итог() throws Exception {
        return new Capitalized(
            String.format(
                "%s %s %s",
                мойTitle,
                new String(мойData),
                new TextOf(new ResourceOf("org/eolang/larger/book.txt"))
            )
        ).asString();
    }
    void setData(byte[] d) {
        мойData = d;
    }
}
