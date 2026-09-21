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
     * The Docker executable.
     */
    private final String binary;

    /**
     * Creates a Docker executor without sudo.
     */
    Docker() {
        this(false);
    }

    /**
     * Creates a Docker executor with optional sudo.
     *
     * @param root Whether to run Docker commands with sudo
     */
    Docker(final boolean root) {
        this(root, "docker");
    }

    /**
     * Creates a Docker executor with optional sudo and a custom executable.
     *
     * @param root Whether to run Docker commands with sudo
     * @param bin The Docker executable
     */
    Docker(final boolean root, final String bin) {
        this.sudo = root;
        this.binary = bin;
    }

    /**
     * Execute a Docker command with the given arguments.
     *
     * @param args Docker command arguments
     * @return Exit code (always 0 on success)
     * @throws IOException If the command fails or returns non-zero exit code
     */
    int exec(final String... args) throws IOException {
        return this.exec(Arrays.asList(args));
    }

    /**
     * Docker is available?
     *
     * <p>The daemon is asked, not the client: {@code docker --version} prints
     * the version of the binary and exits with zero without ever reaching the
     * socket, so a machine with a stopped daemon answered that Docker was
     * usable and the build failed later inside {@code docker run}.</p>
     *
     * <p>The probe has its own short deadline, because a wedged daemon
     * must not block the build for the whole {@link #TIMEOUT} before we
     * fall back to a local phino (see #1060).</p>
     *
     * @return TRUE if Docker is here
     */
    boolean available() {
        boolean yes = true;
        try {
            this.fire(
                this.command(Arrays.asList("info", "--format", "{{.ServerVersion}}")),
                10L
            );
        } catch (final IOException | IllegalStateException ex) {
            Logger.warn(this, "Docker is not available: %s", ex.getMessage());
            yes = false;
        }
        return yes;
    }

    /**
     * Execute a Docker command with the given arguments.
     *
     * @param args Docker command arguments as a collection
     * @return Exit code (always 0 on success)
     * @throws IOException If the command fails or returns non-zero exit code
     */
    int exec(final Collection<String> args) throws IOException {
        return this.fire(this.command(args), Docker.TIMEOUT);
    }

    private static void drained(final Thread... pumps) throws IOException {
        try {
            for (final Thread pump : pumps) {
                pump.join(TimeUnit.SECONDS.toMillis(30L));
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Docker output was interrupted while being read", ex);
        }
    }

    private List<String> command(final Collection<String> args) {
        final List<String> command = new ArrayList<>(args.size() + 2);
        if (this.sudo) {
            command.add("sudo");
        }
        command.add(this.binary);
        command.addAll(args);
        return command;
    }

    private int fire(final List<String> command, final long timeout) throws IOException {
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
            done = proc.waitFor(timeout, TimeUnit.SECONDS);
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
            Docker.drained(stdout, stderr);
            throw new IOException(
                String.format(
                    "Docker command timed out after %d seconds: %s",
                    timeout, String.join(" ", command)
                )
            );
        }
        Docker.drained(stdout, stderr);
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
