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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This class helps mark potentially slow test methods.
 *
 * @since 0.1.0
 */
public final class MayBeSlow implements BeforeEachCallback, AfterEachCallback {

    /**
     * When we started.
     */
    private final long start = System.currentTimeMillis();

    /**
     * Watcher.
     */
    private final Thread watch = new Thread(
        new Runnable() {
            @Override
            public void run() {
                long cycle = 1L;
                while (true) {
                    try {
                        Thread.sleep(Math.min(5_000L * cycle, 60_000L));
                    } catch (final InterruptedException ex) {
                        break;
                    }
                    Logger.warn(
                        this,
                        "We're still running the test (%[ms]s), please wait...",
                        System.currentTimeMillis() - MayBeSlow.this.start
                    );
                    ++cycle;
                }
            }
        }
    );

    @Override
    public void beforeEach(final ExtensionContext ctx) {
        Logger.warn(
            this,
            "The test %s may take longer than a minute; if you want to see the full output of it, set the logging level to \"DEBUG\" for the \"com.yegor256.farea\" logging facility, in the \"src/test/resources/log4j.properties\" file",
            ctx.getDisplayName()
        );
        this.watch.start();
    }

    @Override
    public void afterEach(final ExtensionContext ctx) {
        this.watch.interrupt();
        Logger.warn(
            this,
            "Indeed, it took %[ms]s to run %s",
            System.currentTimeMillis() - this.start, ctx.getDisplayName()
        );
    }
}
