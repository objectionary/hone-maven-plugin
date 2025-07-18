/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;
import org.cactoos.io.OutputTo;
import org.cactoos.io.ResourceOf;
import org.cactoos.io.TeeInput;
import org.cactoos.iterable.Mapped;
import org.cactoos.scalar.IoChecked;
import org.cactoos.scalar.LengthOf;

/**
 * Optimization rules used by Phino.
 *
 * <p>This class handles pattern-based selection of optimization rules,
 * supporting wildcards and exclusions. Rules are stored as YAML files
 * and can be copied to a target directory for use by the optimizer.</p>
 *
 * @since 0.1.0
 */
final class Rules {

    /**
     * All available rule names.
     */
    private static final String[] ALL = {
        "none", "33-to-42",
    };

    /**
     * Compiled regex patterns for rule matching with inclusion/exclusion flags.
     */
    private final Map<Pattern, Boolean> patterns;

    /**
     * Creates a rule manager that includes all rules.
     */
    Rules() {
        this("*");
    }

    /**
     * Creates a rule manager with specific patterns.
     * @param ptns Comma-separated patterns (e.g., "simple,b*,!abc")
     */
    Rules(final String ptns) {
        this.patterns = Rules.regexs(ptns);
    }

    @Override
    public String toString() {
        return String.join(
            " ",
            new Mapped<>(
                t -> t.toString(),
                this.patterns
            )
        );
    }

    /**
     * Get the YAML file names for all matching rules.
     * <p>For example, if rules are defined as "n*,33-to-42",
     * this method will return "none.yml 33-to-42.yml".</p>
     * @return Iterable of YAML file names for matching rules
     */
    public Iterable<String> yamls() {
        final Collection<String> files = new LinkedList<>();
        for (final String name : Rules.ALL) {
            if (!this.matches(name)) {
                continue;
            }
            files.add(String.format("%s.yml", name));
        }
        return files;
    }

    /**
     * Copy all matching rule files to the specified directory.
     * @param dir Destination directory where rule files will be copied
     * @throws IOException If copying files fails
     */
    public void copyTo(final Path dir) throws IOException {
        if (dir.toFile().getParentFile().mkdirs()) {
            Logger.debug(this, "Directory created at %[file]s", dir);
        }
        for (final String file : this.yamls()) {
            final Path target = dir.resolve(file);
            new IoChecked<>(
                new LengthOf(
                    new TeeInput(
                        new ResourceOf(
                            String.format("org/eolang/hone/rules/%s", file)
                        ),
                        new OutputTo(target)
                    )
                )
            ).value();
        }
    }

    /**
     * Check if a rule name matches the configured patterns.
     * @param name The name of the rule, e.g. "thirty-three"
     * @return TRUE if the rule matches and should be included
     */
    private boolean matches(final CharSequence name) {
        boolean matches = false;
        for (final Map.Entry<Pattern, Boolean> ent : this.patterns.entrySet()) {
            if (ent.getKey().matcher(name).matches()) {
                if (ent.getValue()) {
                    matches = true;
                }
                break;
            }
        }
        return matches;
    }

    /**
     * Convert pattern strings to compiled regular expressions.
     * @param ptns Comma-separated pattern strings with optional negation
     * @return Map of compiled patterns to inclusion flags
     */
    private static Map<Pattern, Boolean> regexs(final String ptns) {
        final Map<Pattern, Boolean> list = new HashMap<>(0);
        for (final String ptn : ptns.split("\\s*,\\s*")) {
            if (ptn.isEmpty()) {
                continue;
            }
            boolean negative = false;
            String body = ptn;
            if (ptn.charAt(0) == '!') {
                negative = true;
                body = body.substring(1);
            }
            list.put(
                Pattern.compile(
                    String.format(
                        "^\\Q%s\\E$",
                        body.replace("*", "\\E.*\\Q")
                    )
                ),
                !negative
            );
        }
        return list;
    }
}
