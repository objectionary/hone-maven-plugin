/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

final class BookTest {

    @Test
    void printsItself() throws Exception {
        Material m = new Book(
            "Object Thinking",
            new byte[]{ (byte) 0x00 }
        );
        ((Book) m).setData(new byte[]{ (byte) 0x41, (byte) 0x42, (byte) 0x43 });
        String s = m.итог();
        Assertions.assertTrue(s.contains("ABC"), s);
    }

    @Test
    void keepsUnicodeAttributeNames() throws Exception {
        Material m = new Book("x", new byte[] {});
        for (Field f : m.getClass().getDeclaredFields()) {
            String n = f.getName();
            Assertions.assertTrue(n.startsWith("мой"), n);
        }
    }
}
