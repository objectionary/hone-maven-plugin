/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.Jaxec;
import com.yegor256.Result;
import java.io.IOException;

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
