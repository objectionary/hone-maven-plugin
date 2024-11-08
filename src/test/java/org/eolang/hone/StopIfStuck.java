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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This class helps stop tests that are stuck (run for too long).
 *
 * @since 0.1.0
 */
public final class StopIfStuck implements BeforeEachCallback, AfterEachCallback {

    /**
     * When we started.
     */
    private final long start = System.currentTimeMillis();

    /**
     * The thread in which the test is running.
     */
    private Thread main;

    /**
     * Watcher.
     */
    private final Thread watch = new Thread(
        () -> {
            while (true) {
                try {
                    Thread.sleep(1_000L);
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (System.currentTimeMillis() - this.start > TimeUnit.MINUTES.toMillis(45L)) {
                    this.main.interrupt();
                    Logger.warn(
                        StopIfStuck.class,
                        "Looks like the test is stuck (running for %[ms]s already), killing it...",
                        System.currentTimeMillis() - this.start
                    );
                    break;
                }
            }
        }
    );

    @Override
    public void beforeEach(final ExtensionContext ctx) {
        this.main = Thread.currentThread();
        this.watch.start();
    }

    @Override
    public void afterEach(final ExtensionContext ctx) {
        this.watch.interrupt();
    }
}
