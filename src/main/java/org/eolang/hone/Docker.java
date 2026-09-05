/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import com.jcabi.log.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
     * Hard deadline for any single docker command, in seconds. A hung daemon
     * or a stalled pull must fail the build with a clear message instead of
     * hanging forever (see #839).
     */
    private static final long TIMEOUT = 3600L;

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
    int exec(final String... args) throws IOException {
        return this.exec(Arrays.asList(args));
    }

    /**
     * Docker executable is available?
     * @return TRUE if Docker is here
     */
    boolean available() {
        boolean yes = true;
        try {
            this.exec("--version");
        } catch (final IOException | IllegalStateException ex) {
            Logger.warn(this, "Docker is not available: %s", ex.getMessage());
            yes = false;
        }
        return yes;
    }

    /**
     * Execute a Docker command with the given arguments.
     * @param args Docker command arguments as a collection
     * @return Exit code (always 0 on success)
     * @throws IOException If the command fails or returns non-zero exit code
     */
    int exec(final Collection<String> args) throws IOException {
        final List<String> command = new ArrayList<>(args.size() + 2);
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
        final Process proc = new ProcessBuilder(command).start();
        final Thread stdout = new Thread(
            () -> new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8)
            ).lines().forEach(line -> Logger.info(this, "  %s", line))
        );
        final Thread stderr = new Thread(
            () -> new BufferedReader(
                new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8)
            ).lines().forEach(line -> Logger.info(this, "  %s", line))
        );
        stdout.start();
        stderr.start();
        final boolean done;
        try {
            done = proc.waitFor(Docker.TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(
                String.format("Docker was interrupted: %s", String.join(" ", command)),
                ex
            );
        }
        if (!done) {
            proc.destroyForcibly();
            throw new IOException(
                String.format(
                    "Docker command timed out after %d seconds: %s",
                    Docker.TIMEOUT, String.join(" ", command)
                )
            );
        }
        Logger.info(
            this, "+ %s -> 0x%04x in %[ms]s",
            String.join(" ", command), proc.exitValue(),
            System.currentTimeMillis() - start
        );
        if (proc.exitValue() != 0) {
            throw new IOException(
                String.format("Failed to execute docker, code=0x%04x", proc.exitValue())
            );
        }
        return 0;
    }
}
