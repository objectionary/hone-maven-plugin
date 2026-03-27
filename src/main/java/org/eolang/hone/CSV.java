/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.cactoos.text.TextOf;
import org.cactoos.text.UncheckedText;

/**
 * CSV summary .
 *
 * @since 0.1.0
 * @checkstyle AbbreviationAsWordInNameCheck (3 lines)
 */
public final class CSV {

    /**
     * CSV content.
     */
    private final String content;

    /**
     * Constructor.
     *
     * @param root CSV file path
     */
    CSV(final Path root) {
        this(new UncheckedText(new TextOf(root)).asString());
    }

    /**
     * Constructor.
     *
     * @param content CSV content
     */
    CSV(final String content) {
        this.content = content;
    }

    /**
     * Retrieves the header of the CSV as a list of column names.
     *
     * @return List of column names from the CSV header
     */
    List<String> header() {
        return Arrays.stream(this.content.split("\n"))
            .findFirst()
            .map(line -> Arrays.asList(line.split(",")))
            .orElseThrow(() -> new IllegalStateException("CSV content is empty"));
    }

    /**
     * Retrieves the rows of the CSV as a list of lists of strings.
     * Each inner list represents a row of the CSV, and each string in the
     * inner list represents a cell value.
     * The method skips the header row and processes only the data rows.
     *
     * @return List of rows, where each row is a list of string values.
     */
    List<List<String>> rows() {
        return Arrays.stream(this.content.split("\n"))
            .skip(1)
            .map(line -> Arrays.asList(line.split(",")))
            .collect(Collectors.toList());
    }

    /**
     * Flushes the CSV content to the specified file path.
     *
     * @param res The path to the file where the CSV content should be written.
     */
    void flush(final Path res) {
        try {
            Files.write(res, this.content.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException exception) {
            throw new IllegalStateException(
                String.format(
                    "Failed to write CSV content to file: %s",
                    res.toString()
                ),
                exception
            );
        }
    }
}
