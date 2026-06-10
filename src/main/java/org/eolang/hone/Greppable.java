/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A {@code grep -E} pattern that matches a class whose bytecode mentions
 * any of a given set of {@code java.util.stream} method names.
 *
 * <p>Each method name is turned into the hex-byte form that jeo uses to
 * encode a string literal inside a {@code .xmir} file (for example,
 * {@code "map"} becomes {@code 6D-61-70}) and the alternatives are anchored
 * between the XML tag boundaries {@code >} and {@code <}, so a name matches
 * only when its bytes are the <em>entire</em> content of an {@code <o>}
 * element — not when they happen to be a prefix or suffix of a longer
 * sequence (e.g. {@code "mapped/X"} or {@code "filtered"}). The anchoring
 * also keeps the pattern within POSIX ERE, the dialect understood by
 * {@code grep -E}.</p>
 *
 * @since 0.27.2
 */
final class Greppable {

    /**
     * Method names to match.
     */
    private final String[] methods;

    /**
     * Ctor.
     * @param names Method names to match
     */
    Greppable(final String... names) {
        this.methods = names.clone();
    }

    @Override
    public String toString() {
        final Collection<String> hexes = new ArrayList<>(this.methods.length);
        for (final String method : this.methods) {
            hexes.add(Greppable.hex(method));
        }
        return String.format(">(%s)<", String.join("|", hexes));
    }

    /**
     * Convert a string to a dash-separated sequence of upper-case hex byte
     * values, matching the way jeo encodes a string literal inside a
     * {@code .xmir} file (for example, {@code "map"} becomes {@code "6D-61-70"}).
     * @param text The string to convert
     * @return Hex-byte representation, dash-separated
     */
    private static String hex(final String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        final Collection<String> codes = new ArrayList<>(bytes.length);
        for (final byte chr : bytes) {
            codes.add(String.format("%02X", Byte.toUnsignedInt(chr)));
        }
        return String.join("-", codes);
    }
}
