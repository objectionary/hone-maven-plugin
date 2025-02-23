/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import com.jcabi.log.VerboseProcess;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

/**
 * Docker runner.
 *
 * @since 0.1.0
 */
final class Docker {

    /**
     * Use "sudo" for "docker".
     */
    private final boolean sudo;

    /**
     * Ctor.
     */
    Docker() {
        this(false);
    }

    /**
     * Ctor.
     * @param root Run as root?
     */
    Docker(final boolean root) {
        this.sudo = root;
    }

    /**
     * Run this command and its arguments.
     * @param args Arguments.
     * @return Exit code
     * @throws IOException If fails
     */
    public int exec(final String... args) throws IOException {
        return this.exec(Arrays.asList(args));
    }

    /**
     * Run this one.
     * @param args Arguments
     * @return Exit code
     * @throws IOException If fails
     */
    public int exec(final Collection<String> args) throws IOException {
        final List<String> command = new LinkedList<>();
        if (this.sudo) {
            command.add("sudo");
        }
        command.add("docker");
        command.addAll(args);
        return this.fire(command);
    }

    /**
     * Run this one.
     * @param command The command with args
     * @return Exit code
     * @throws IOException If fails
     */
    private int fire(final List<String> command) throws IOException {
        final long start = System.currentTimeMillis();
        Logger.info(this, "+ %s ...", String.join(" ", command));
        final ProcessBuilder bldr = new ProcessBuilder(command);
        try (VerboseProcess proc = new VerboseProcess(bldr, Level.INFO, Level.INFO)) {
            final VerboseProcess.Result ret = proc.waitFor();
            Logger.info(
                this, "+ %s -> 0x%04x in %[ms]s",
                String.join(" ", command), ret.code(),
                System.currentTimeMillis() - start
            );
            if (ret.code() != 0) {
                throw new IOException(
                    String.format("Failed to execute docker, code=0x%04x", ret.code())
                );
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
        return 0;
    }

}
