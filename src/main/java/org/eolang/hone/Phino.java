/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.Jaxec;
import com.yegor256.Result;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.cactoos.io.OutputTo;
import org.cactoos.io.ResourceOf;
import org.cactoos.io.TeeInput;
import org.cactoos.iterable.Mapped;
import org.cactoos.scalar.IoChecked;
import org.cactoos.scalar.LengthOf;

/**
 * An abstraction of Phino in command line.
 *
 * @since 0.17.0
 */
final class Phino {

    /**
     * Is it available?
     * @return TRUE if available
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    public boolean available() {
        boolean available = false;
        try {
            final Result result = new Jaxec("phino", "--version").withCheck(false).execUnsafe();
            if (result.code() == 0) {
                available = true;
                Logger.info(
                    this,
                    String.format(
                        "The 'phino' executable found (%s), no need to use Docker",
                        result.stdout().trim()
                    )
                );
            } else {
                Logger.info(
                    this,
                    "The 'phino' executable is found, but it doesn't work, we must use Docker"
                );
            }
        } catch (final IOException ex) {
            Logger.info(
                this,
                String.format(
                    "The 'phino' executable not found, we must use Docker: %s",
                    ex.getMessage()
                )
            );
        }
        return available;
    }

}
