/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Temporary directory that automatically cleans up after itself.
 *
 * <p>This class creates a temporary directory and implements {@link Closeable}
 * to ensure the directory and all its contents are deleted when closed.
 * It should be used with try-with-resources for automatic cleanup.</p>
 *
 * @since 0.1.0
 */
final class Mktemp implements Closeable {

    /**
     * Path to the temporary directory.
     */
    private final Path dir;

    /**
     * Creates a new temporary directory.
     * @throws IOException If directory creation fails
     */
    Mktemp() throws IOException {
        this.dir = Files.createTempDirectory("tmp");
    }

    /**
     * Get the path to the temporary directory.
     * @return Path of the temporary directory
     */
    public Path path() {
        return this.dir;
    }

    @Override
    public void close() throws IOException {
        try (Stream<Path> stream = Files.walk(this.dir)) {
            stream
                .map(Path::toFile)
                .sorted(Comparator.reverseOrder())
                .forEach(File::delete);
        }
    }
}
