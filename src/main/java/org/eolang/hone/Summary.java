/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Build summary statistics.
 *
 * @since 0.1.0
 */
public final class Summary {

    /**
     * Where to look for statistics.
     */
    private final Path root;

    /**
     * Where to save the summary report.
     */
    private final Path target;

    /**
     * Constructor.
     *
     * @param root Root directory to search for statistics.
     */
    Summary(final Path root) {
        this(root, root);
    }

    /**
     * Constructor.
     *
     * @param root Root directory to search for statistics.
     * @param target Directory to save the summary report.
     */
    Summary(final Path root, final Path target) {
        this.root = root;
        this.target = target;
    }

    /**
     * Collects summary statistics from all child modules.
     *
     * @return The path to the generated summary report.
     */
    Path collect() {
        final List<CSV> found = new ArrayList<>(0);
        final String stats = "hone-statistics.csv";
        try (Stream<Path> paths = Files.walk(this.root)) {
            paths.filter(Files::isRegularFile)
                .filter(
                    path -> path.getFileName()
                        .toString()
                        .equals(stats)
                )
                .map(CSV::new)
                .forEach(found::add);
        } catch (final IOException exception) {
            throw new IllegalStateException(
                "Failed to collect summary statistics",
                exception
            );
        }
        final Path destination = this.target.resolve(stats);
        if (!found.isEmpty()) {
            Summary.reduce(found).flush(destination);
        }
        return destination;
    }

    private static CSV reduce(final List<CSV> csvs) {
        final AtomicInteger index = new AtomicInteger(1);
        final CSV res = csvs.stream().reduce(CSV::add).orElseThrow(
            () -> new IllegalStateException(
                "Failed to reduce summary statistics: no CSV files found."
            )
        );
        final int size = res.size();
        return res.recompute(
            "ID",
            ignore -> String.format("%d/%d", index.getAndIncrement(), size)
        );
    }
}
