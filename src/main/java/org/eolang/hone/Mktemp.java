/*
 * The MIT License (MIT)
 *
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Temp directory.
 *
 * @since 0.1.0
 */
final class Mktemp implements Closeable {

    /**
     * The temp directory.
     */
    private final Path dir;

    /**
     * Ctor.
     * @throws IOException If fails
     */
    Mktemp() throws IOException {
        this.dir = Files.createTempDirectory("tmp");
    }

    /**
     * Return the path.
     * @return Path of the directory
     */
    public Path path() {
        return this.dir;
    }

    @Override
    public void close() throws IOException {
        try (var stream = Files.walk(this.dir)) {
            stream
                .map(Path::toFile)
                .sorted(Comparator.reverseOrder())
                .forEach(File::delete);
        }
    }
}
