/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024-2025 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
