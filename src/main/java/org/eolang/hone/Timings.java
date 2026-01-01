/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
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
 * Performance timing recorder that measures and logs execution times.
 *
 * <p>This class records the execution time of various operations
 * and appends them to a CSV file for performance analysis.
 * Each timing entry consists of an operation name and its duration
 * in milliseconds.</p>
 *
 * @since 0.1.0
 */
final class Timings {

    /**
     * Path to the CSV file where timings are recorded.
     */
    private final Path path;

    /**
     * Creates a new timings recorder.
     * @param file Path to the CSV file where timings will be recorded
     */
    Timings(final Path file) {
        this.path = file;
    }

    /**
     * Executes an action and records its execution time.
     * @param name Name of the action being timed
     * @param action The action to execute and measure
     * @throws IOException If recording the timing fails
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
     * Functional interface for actions that can be timed.
     *
     * @since 0.1.0
     */
    public interface Action {
        /**
         * Execute the action.
         * @throws IOException If execution fails
         */
        void exec() throws IOException;
    }

}
