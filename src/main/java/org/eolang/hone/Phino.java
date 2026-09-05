/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * An abstraction of Phino in command line.
 * @since 0.17.0
 */
final class Phino {

    /**
     * The executable to run.
     */
    private final String executable;

    /**
     * Ctor.
     */
    Phino() {
        this("phino");
    }

    /**
     * Ctor.
     * @param executable The executable to run, instead of "phino"
     */
    Phino(final String executable) {
        this.executable = executable;
    }

    /**
     * Is it available?
     * @param expected This is the expected version
     * @return TRUE if available
     */
    boolean available(final String expected) {
        boolean available = false;
        try {
            final Process proc = new ProcessBuilder(
                this.executable, "--version"
            ).start();
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            final Thread pump = new Thread(
                () -> Phino.pump(stdout, proc.getInputStream())
            );
            pump.start();
            final boolean probed = proc.waitFor(3L, TimeUnit.SECONDS);
            if (probed) {
                pump.join();
                available = Phino.version(proc, stdout, expected, this);
            } else {
                proc.destroyForcibly();
                Logger.info(
                    this,
                    "The 'phino --version' probe timed out, we must use Docker"
                );
            }
        } catch (final IOException ex) {
            Logger.info(
                this,
                "The 'phino' executable not found, we must use Docker: %s",
                ex.getMessage()
            );
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return available;
    }

    /**
     * Drain a process stdout into a buffer, byte by byte.
     * @param stdout Where to collect the output
     * @param input The input stream to drain
     */
    private static void pump(final ByteArrayOutputStream stdout, final InputStream input) {
        try {
            final byte[] buffer = new byte[1024];
            while (true) {
                final int read = input.read(buffer);
                if (read == -1) {
                    break;
                }
                stdout.write(buffer, 0, read);
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Decide whether the probed phino is the expected one.
     * @param proc The probed process
     * @param stdout Collected version output
     * @param expected The version we need
     * @param self Logger target
     * @return TRUE if the versions match
     */
    private static boolean version(
        final Process proc, final ByteArrayOutputStream stdout,
        final String expected, final Phino self
    ) {
        final boolean match;
        final String version = new String(
            stdout.toByteArray(), StandardCharsets.UTF_8
        ).trim();
        if (proc.exitValue() == 0 && version.equals(expected)) {
            Logger.info(
                self,
                "The 'phino' executable found (%s), no need to use Docker",
                version
            );
            match = true;
        } else {
            Logger.info(
                self,
                "The 'phino' executable is found, but its version (%s) is not equal to the expected one (%s); you can upgrade it via 'cabal update && cabal install --overwrite-policy=always phino-%s' (see https://github.com/objectionary/phino for details)",
                version, expected, expected
            );
            match = false;
        }
        return match;
    }
}
