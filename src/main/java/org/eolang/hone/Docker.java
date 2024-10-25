/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Objectionary.com
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
     * Run this one.
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
                    String.format("Failed to execute docker, code=%d", ret.code())
                );
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
        return 0;
    }

}
