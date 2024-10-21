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
import java.io.IOException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Pull Docker image from Docker Hub.
 *
 * <p>This goal pulls Docker image from
 * <a href="https://hub.docker.com">Docker Hub</a> to your machine. You
 * may skip this goal and simply use the <tt>optimize</tt> goal, which
 * will automatically pull the image from the Hub. However, it would be
 * cleaner to use <tt>pull</tt>, then <tt>optimize</tt>, and
 * then <tt>rmi</tt> (which deletes the image from your machine).</p>
 *
 * @since 0.1.0
 */
@Mojo(name = "pull", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public final class PullMojo extends AbstractMojo {

    @Override
    public void exec() throws IOException {
        new Docker(this.sudo).exec(
            "pull",
            this.image
        );
        Logger.info(this, "Docker image '%s' was pulled", this.image);
    }
}
