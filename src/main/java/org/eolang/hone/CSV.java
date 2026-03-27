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
 */
public final class CSV {

    private final String content;

    CSV(final Path root) {
        this(new UncheckedText(new TextOf(root)).asString());
    }

    CSV(final String content) {
        this.content = content;
    }

    List<String> header() {
        return Arrays.stream(this.content.split("\n"))
            .findFirst()
            .map(line -> Arrays.asList(line.split(",")))
            .orElseThrow(() -> new IllegalStateException("CSV content is empty"));
    }

    List<List<String>> rows() {
        return Arrays.stream(this.content.split("\n"))
            .skip(1)
            .map(line -> Arrays.asList(line.split(",")))
            .collect(Collectors.toList());
    }

    void append(final String row) {
        this.content.concat("\n").concat(row);
    }

    public void flush(Path res) {
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
