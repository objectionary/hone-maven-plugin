/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Objectionary.com
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
