/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024-2025 Objectionary.com
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
 * Collection of YAML rules for Normalizer.
 *
 * @since 0.1.0
 */
final class Rules {

    /**
     * All of them.
     */
    private static final String[] ALL = {
        "none", "thirty-three",
    };

    /**
     * Patterns.
     */
    private final Map<Pattern, Boolean> patterns;

    /**
     * Ctor.
     */
    Rules() {
        this("*");
    }

    /**
     * Ctor.
     * @param ptns The pattern (comma-separated)
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
     * Create a space-separated list of rule
     * file names, for example, the rules defined by the user as
     * "n*,thirty-three" this method will produce "none.yml thirty-three.yml".
     * @return List of rule YML files separated by space
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
     * Copy them all to a destination directory.
     * @param dir Destination directory to copy to
     * @throws IOException If fails
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
     * This rule name is good?
     * @param name THe name of the rule, e.g. "thirty-three"
     * @return TRUE if it's good
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
     * Convert list of patterns to regular expressions.
     * @param ptns List of them
     * @return Map of regular expression patterns
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
