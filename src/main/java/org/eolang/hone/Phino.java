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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * An abstraction of Phino in command line.
 *
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
     *
     * @param executable The executable to run, instead of "phino"
     */
    Phino(final String executable) {
        this.executable = executable;
    }

    /**
     * Is it available?
     *
     * <p>Both streams of the probe are read. A process that writes more to
     * stderr than the pipe holds blocks on the write and never exits, so a
     * phino that warns about anything at all would look like a phino that
     * hangs, and the plugin would fall back to Docker with a message that
     * says nothing about the executable it just found.</p>
     *
     * @param expected This is the expected version
     * @return TRUE if available
     */
    boolean available(final String expected) {
        final Optional<String> output = Phino.probe(this.executable, 3L);
        final boolean match;
        if (output.isPresent()) {
            final String version = output.get();
            if (version.equals(expected)) {
                Logger.info(
                    this,
                    "The 'phino' executable found (%s)",
                    version
                );
                match = true;
            } else {
                Logger.info(
                    this,
                    "The 'phino' executable is found, but its version (%s) is not equal to the expected one (%s); you can upgrade it via 'cabal update && cabal install --overwrite-policy=always phino-%s' (see https://github.com/objectionary/phino for details)",
                    version, expected, expected
                );
                match = false;
            }
        } else {
            match = false;
        }
        return match;
    }

    /**
     * Probe whether an executable runs {@code --version} cleanly within the
     * deadline.
     *
     * <p>Both streams of the probe are read. A process that writes more to
     * stderr than the pipe holds blocks on the write and never exits.</p>
     *
     * @param executable The executable to run
     * @param timeout Deadline in seconds
     * @return Trimmed stdout when exit code is zero, empty otherwise
     */
    static Optional<String> probe(final String executable, final long timeout) {
        Optional<String> result = Optional.empty();
        try {
            final Process proc = new ProcessBuilder(executable, "--version").start();
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            final Thread pump = new Thread(
                () -> Phino.pump(stdout, proc.getInputStream())
            );
            pump.setDaemon(true);
            pump.start();
            final Thread errs = new Thread(
                () -> Phino.pump(new ByteArrayOutputStream(), proc.getErrorStream())
            );
            errs.setDaemon(true);
            errs.start();
            final boolean probed;
            try {
                probed = proc.waitFor(timeout, TimeUnit.SECONDS);
            } catch (final InterruptedException ex) {
                proc.destroyForcibly();
                proc.getInputStream().close();
                proc.getErrorStream().close();
                throw ex;
            }
            if (probed) {
                pump.join();
                errs.join();
                if (proc.exitValue() == 0) {
                    result = Optional.of(
                        new String(stdout.toByteArray(), StandardCharsets.UTF_8).trim()
                    );
                } else {
                    Logger.info(
                        Phino.class,
                        "The '%s --version' probe exited with %d, we must use Docker",
                        executable, proc.exitValue()
                    );
                }
            } else {
                proc.destroyForcibly();
                proc.getInputStream().close();
                proc.getErrorStream().close();
                pump.join(TimeUnit.SECONDS.toMillis(1L));
                errs.join(TimeUnit.SECONDS.toMillis(1L));
                Logger.info(
                    Phino.class,
                    "The '%s --version' probe timed out, we must use Docker",
                    executable
                );
            }
        } catch (final IOException ex) {
            Logger.info(
                Phino.class,
                "The '%s' executable not found, we must use Docker: %s",
                executable, ex.getMessage()
            );
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

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
            Logger.debug(
                Phino.class,
                "Reading of the probe stream stopped: %s",
                ex.getMessage()
            );
        }
    }
}
