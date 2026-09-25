/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Build summary statistics.
 *
 * @since 0.1.0
 */
public final class Summary {

    /**
     * Directories where to look for statistics.
     */
    private final Collection<Path> roots;

    /**
     * Where to save the summary report.
     */
    private final Path target;

    /**
     * Constructor.
     *
     * @param root Root directory to search for statistics
     */
    Summary(final Path root) {
        this(root, root);
    }

    /**
     * Constructor.
     *
     * @param root Root directory to search for statistics
     * @param target Directory to save the summary report
     */
    Summary(final Path root, final Path target) {
        this(Collections.singletonList(root), target);
    }

    /**
     * Constructor.
     *
     * @param roots Directories to search for statistics, one per module
     * @param target Directory to save the summary report
     */
    Summary(final Collection<Path> roots, final Path target) {
        this.roots = roots;
        this.target = target;
    }

    /**
     * Collects summary statistics from all child modules.
     *
     * @return The path to the generated summary report
     */
    Path collect() {
        final List<CSV> found = new ArrayList<>(0);
        final Path destination = this.target.resolve("hone-summary.csv");
        final Collector collector = new Collector(found, "hone-statistics.csv", destination);
        try {
            for (final Path root : this.roots) {
                if (Files.exists(root)) {
                    Files.walkFileTree(
                        root,
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        collector
                    );
                }
            }
        } catch (final IOException exception) {
            throw new IllegalStateException(
                "Failed to collect summary statistics",
                exception
            );
        }
        try {
            if (found.isEmpty()) {
                Files.deleteIfExists(destination);
            } else {
                Summary.reduce(found).flush(destination);
            }
        } catch (final IOException exception) {
            throw new IllegalStateException(
                "Failed to write summary statistics",
                exception
            );
        }
        return destination;
    }

    private static CSV reduce(final List<CSV> csvs) {
        final CSV res = csvs.stream().reduce(CSV::add).orElseThrow(
            () -> new IllegalStateException(
                "Failed to reduce summary statistics: no CSV files found."
            )
        );
        final AtomicInteger index = new AtomicInteger(1);
        return res.recompute(
            "ID",
            ignore -> String.format("%d/%d", index.getAndIncrement(), res.size())
        );
    }
}
