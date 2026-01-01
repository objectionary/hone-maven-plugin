/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
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
 * Docker command executor with optional sudo support.
 *
 * <p>This class provides a wrapper for executing Docker commands
 * with proper error handling and logging. It supports running
 * commands with sudo when required.</p>
 *
 * @since 0.1.0
 */
final class Docker {

    /**
     * Whether to prepend "sudo" to Docker commands.
     */
    private final boolean sudo;

    /**
     * Creates a Docker executor without sudo.
     */
    Docker() {
        this(false);
    }

    /**
     * Creates a Docker executor with optional sudo.
     * @param root Whether to run Docker commands with sudo
     */
    Docker(final boolean root) {
        this.sudo = root;
    }

    /**
     * Execute a Docker command with the given arguments.
     * @param args Docker command arguments
     * @return Exit code (always 0 on success)
     * @throws IOException If the command fails or returns non-zero exit code
     */
    public int exec(final String... args) throws IOException {
        return this.exec(Arrays.asList(args));
    }

    /**
     * Execute a Docker command with the given arguments.
     * @param args Docker command arguments as a collection
     * @return Exit code (always 0 on success)
     * @throws IOException If the command fails or returns non-zero exit code
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
     * Execute the assembled command and handle the process.
     * @param command Complete command with all arguments
     * @return Exit code (always 0 on success)
     * @throws IOException If the command fails or returns non-zero exit code
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
