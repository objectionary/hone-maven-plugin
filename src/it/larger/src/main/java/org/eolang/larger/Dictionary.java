/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.larger;

import java.util.HashMap;
import java.util.Map;

class Dictionary<K, V> {
    private final Map<K, V> map = new HashMap<K, V>(0);
    void put(K k, V v) {
        map.put(k, v);
    }
    V get(K k) {
        return map.get(k);
    }
}
