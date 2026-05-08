/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.yegor256.Jaxec;
import com.yegor256.Result;
import java.io.IOException;

/**
 * An abstraction of Phino in command line.
 * @since 0.17.0
 */
final class Phino {

    /**
     * Is it available?
     * @param expected This is the expected version
     * @return TRUE if available
     */
    boolean available(final String expected) {
        boolean available = false;
        try {
            final Result result = new Jaxec("phino", "--version").withCheck(false).execUnsafe();
            if (result.code() == 0) {
                final String version = result.stdout().trim();
                if (version.equals(expected)) {
                    available = true;
                    Logger.info(
                        this,
                        "The 'phino' executable found (%s), no need to use Docker",
                        version
                    );
                } else {
                    Logger.info(
                        this,
                        "The 'phino' executable is found, but its version (%s) is not equal to the expected one (%s); you can upgrade it via 'cabal update && cabal install --overwrite-policy=always phino-%s' (see https://github.com/objectionary/phino for details)",
                        version, expected, expected
                    );
                }
            } else {
                Logger.info(
                    this,
                    "The 'phino' executable is found, but it doesn't work, we must use Docker"
                );
            }
        } catch (final IOException ex) {
            Logger.info(
                this,
                "The 'phino' executable not found, we must use Docker: %s",
                ex.getMessage()
            );
        }
        return available;
    }
}
