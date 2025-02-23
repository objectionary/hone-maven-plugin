/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Timings.
 *
 * @since 0.1.0
 */
final class Timings {

    /**
     * Path of the file.
     */
    private final Path path;

    /**
     * Ctor.
     * @param file Location of the CSV file to modify
     */
    Timings(final Path file) {
        this.path = file;
    }

    /**
     * Run and record.
     * @param name Name of the action
     * @param action The action
     * @throws IOException If fails
     */
    public void through(final String name, final Timings.Action action) throws IOException {
        final long start = System.currentTimeMillis();
        try {
            action.exec();
        } finally {
            final File dir = this.path.toFile().getParentFile();
            if (dir.mkdirs()) {
                Logger.debug(this, "Directory created: %[file]s", dir);
            }
            Files.write(
                this.path,
                String.format(
                    "%s,%d\n", name, System.currentTimeMillis() - start
                ).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.CREATE
            );
        }
    }

    /**
     * What to do.
     *
     * @since 0.1.0
     */
    public interface Action {
        /**
         * Exec it.
         * @throws IOException If fails
         */
        void exec() throws IOException;
    }

}
