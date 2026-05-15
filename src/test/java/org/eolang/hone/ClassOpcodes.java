/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;

/**
 * Opcode tally of a compiled Java class.
 *
 * <p>Reads a {@code .class} file at the given path, walks every
 * instruction inside every method of the class, and returns a
 * frequency map keyed by the lowercase JVM mnemonic of each opcode
 * (e.g., {@code invokedynamic}, {@code iload}, {@code return}).
 * Pseudo-instructions like labels, line numbers, and frames are
 * skipped (their opcode is {@code -1}).</p>
 *
 * <p>Intended for tests that need to assert that optimization
 * either preserved, introduced, or removed a given JVM
 * instruction in the resulting bytecode.</p>
 *
 * @since 0.6.0
 */
final class ClassOpcodes {

    /**
     * Path to the compiled {@code .class} file.
     */
    private final Path file;

    /**
     * Ctor.
     * @param src Path to the {@code .class} file to inspect
     */
    ClassOpcodes(final Path src) {
        this.file = src;
    }

    /**
     * Count opcodes used inside the class.
     * @return Map from lowercase JVM mnemonic to its number of occurrences
     * @throws IOException If the file cannot be read
     */
    Map<String, Integer> counts() throws IOException {
        final ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(this.file)).accept(node, 0);
        final Map<String, Integer> tally = new HashMap<>();
        for (final MethodNode method : node.methods) {
            for (final AbstractInsnNode insn : method.instructions) {
                final int code = insn.getOpcode();
                if (code >= 0) {
                    tally.merge(
                        Printer.OPCODES[code].toLowerCase(Locale.ROOT),
                        1, Integer::sum
                    );
                }
            }
        }
        return tally;
    }
}
