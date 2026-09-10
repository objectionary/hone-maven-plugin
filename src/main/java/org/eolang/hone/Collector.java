/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * A file visitor that gathers statistics files, following symlinks.
 *
 * @since 0.1.0
 */
final class Collector extends SimpleFileVisitor<Path> {

    /**
     * Where to put the found CSVs.
     */
    private final List<CSV> found;

    /**
     * The statistics file name to look for.
     */
    private final String stats;

    /**
     * The summary output, excluded from the walk.
     */
    private final Path output;

    /**
     * Ctor.
     *
     * @param found Where to put the found CSVs
     * @param stats The statistics file name to look for
     * @param output The summary output to exclude
     */
    Collector(final List<CSV> found, final String stats, final Path output) {
        this.found = found;
        this.stats = stats;
        this.output = output;
    }

    @Override
    public FileVisitResult visitFile(
        final Path file, final BasicFileAttributes attrs
    ) {
        if (this.stats.equals(file.getFileName().toString())
            && !Collector.same(this.output, file)) {
            this.found.add(new CSV(file));
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(
        final Path file, final IOException exc
    ) {
        if (exc instanceof FileSystemLoopException) {
            Logger.warn(
                this,
                "Symlink cycle detected at %s, skipping",
                file
            );
        } else {
            Logger.warn(
                this,
                "Can't read %s, its statistics are missing from the summary: %s",
                file, exc.getMessage()
            );
        }
        return FileVisitResult.CONTINUE;
    }

    private static boolean same(final Path first, final Path second) {
        return first.toAbsolutePath().normalize()
            .equals(second.toAbsolutePath().normalize());
    }
}
