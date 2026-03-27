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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build summary statistics. 
 *
 * @since 0.1.0
 */
public final class Summary {

    private final Path root;
    private final Path target;

    Summary(final Path root) {
        this(root, root);
    }

    Summary(final Path root, final Path target) {
        this.root = root;
        this.target = target;
    }

    /**
     * Collects summary statistics from all child modules and builds a single summary report.
     *
     * @return The path to the generated summary report.
    */

    Path collect() {
        List<CSV> found = new ArrayList<>(0);
        try (Stream<Path> paths = Files.walk(this.root)) {
            paths.filter(Files::isRegularFile)
                .filter(
                    path -> path.getFileName()
                        .toString()
                        .equals("hone-statistics.csv")
                )
                .map(CSV::new)
                .forEach(found::add);
        } catch (final IOException exception) {
            throw new IllegalStateException(
                "Failed to collect summary statistics",
                exception
            );
        }
        final Path destination = this.target.resolve("hone-statistics.csv");
        if (!found.isEmpty()) {
            reduce(found).flush(destination);
        }
        return destination;
    }

    private static CSV reduce(final List<CSV> csvs) {
        final List<List<String>> lines = csvs.stream()
            .map(CSV::rows)
            .flatMap(List::stream)
            .collect(Collectors.toList());
        final int total = lines.size();
        final AtomicInteger index = new AtomicInteger(1);
        return new CSV(
            Stream.concat(
                Stream.of(String.join(",", csvs.get(0).header())),
                lines.stream().map(row -> 
                    String.format(
                        "%d/%d,%s,%s,%s,%s",
                        index.getAndIncrement(),
                        total,
                        row.get(1),
                        row.get(2),
                        row.get(3),
                        row.get(4)
                    )
                )
            ).collect(Collectors.joining("\n"))
        );
    }
}

