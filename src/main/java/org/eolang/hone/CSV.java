/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.cactoos.list.ListOf;

/**
 * CSV summary .
 * @since 0.1.0
 * @checkstyle AbbreviationAsWordInNameCheck (3 lines)
 */
public final class CSV {

    /**
    * CSV header.
    */
    private final List<String> headers;

    /**
     * CSV rows.
     */
    private final List<Map<String, String>> records;

    /**
     * Constructor.
     * @param root CSV file path
     * @checkstyle ConstructorsCodeFreeCheck (3 lines)
     */
    CSV(final Path root) {
        this(CSV.from(root));
    }

    /**
     * Constructor.
     * @param other The CSV to copy fields from
     * @checkstyle ConstructorsCodeFreeCheck (3 lines)
     */
    private CSV(final CSV other) {
        this(other.headers, other.records);
    }

    /**
     * Constructor.
     * @param headers CSV headers
     * @param records CSV records
     */
    private CSV(
        final List<String> headers,
        final List<Map<String, String>> records
    ) {
        this.headers = headers;
        this.records = records;
    }

    /**
     * Combines this CSV with another CSV, concatenating their records.
     * @param other The other CSV to combine with this one
     * @return A new CSV instance containing the combined records of both CSVs
     */
    CSV add(final CSV other) {
        final List<Map<String, String>> combined = new ArrayList<>(this.records);
        combined.addAll(other.records);
        return new CSV(this.headers, combined);
    }

    /**
    * Number of records in the CSV.
    * @return The number of records in the CSV
    */
    int size() {
        return this.records.size();
    }

    /**
     * Counts rows where a column value matches the given condition.
     * @param header The column name to check
     * @param condition Predicate applied to the column value
     * @return Number of matching rows
     */
    int count(final String header, final Predicate<String> condition) {
        return (int) this.records.stream()
            .filter(row -> condition.test(row.get(header)))
            .count();
    }

    /**
     * Recomputes the values of a column using a transformation function.
     * @param header The name of the column to recompute
     * @param modification Transformation function
     * @return A new CSV instance with the recomputed values
     */
    CSV recompute(
        final String header,
        final Function<String, String> modification
    ) {
        return new CSV(
            this.headers,
            this.records.stream().map(
                row -> {
                    final Map<String, String> data = new HashMap<>(row);
                    data.put(header, modification.apply(row.get(header)));
                    return data;
                }
            ).collect(Collectors.toList())
        );
    }

    /**
     * Flushes the CSV content to the specified file path.
     * @param res The path to the file where the CSV content should be written
     */
    void flush(final Path res) {
        try (
            CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(res, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(this.headers.toArray(new String[0])).get()
            )
        ) {
            for (final Map<String, String> record : this.records) {
                printer.printRecord(
                    this.headers.stream().map(record::get).collect(Collectors.toList())
                );
            }
        } catch (final IOException exception) {
            throw new IllegalStateException(
                String.format("Failed to flush CSV content to file: %s", res),
                exception
            );
        }
    }

    private static List<Map<String, String>> rows(final Collection<CSVRecord> records) {
        return records.stream()
            .map(CSVRecord::toMap)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("PMD.UnnecessaryLocalRule")
    private static CSV from(final Path csv) {
        try (
            Reader reader = new InputStreamReader(
                Files.newInputStream(csv),
                StandardCharsets.UTF_8
            );
            CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(false)
                .get()
                .parse(reader)
        ) {
            return new CSV(
                new ListOf<>(parser.getHeaderNames()),
                CSV.rows(parser.getRecords())
            );
        } catch (final IOException exception) {
            throw new IllegalStateException(
                String.format("Failed to read CSV file: %s", csv),
                exception
            );
        }
    }
}
