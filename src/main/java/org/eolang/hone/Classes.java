/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finder of {@code .class} files in a directory.
 *
 * <p>It follows symbolic links, the way the rest of the plugin does since
 * #1114, and steps over a link that makes a cycle instead of failing on it,
 * because a directory with a cycle and no classes is still a directory with
 * no classes (see #1246).</p>
 *
 * @since 0.30.0
 */
final class Classes extends SimpleFileVisitor<Path> {

    /**
     * Whether at least one class was found.
     */
    private final AtomicBoolean found;

    /**
     * Ctor.
     */
    Classes() {
        super();
        this.found = new AtomicBoolean(false);
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
        FileVisitResult res = FileVisitResult.CONTINUE;
        if (file.toString().endsWith(".class")) {
            this.found.set(true);
            res = FileVisitResult.TERMINATE;
        }
        return res;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc)
        throws IOException {
        if (!(exc instanceof FileSystemLoopException)) {
            throw exc;
        }
        return FileVisitResult.CONTINUE;
    }

    /**
     * Is there at least one {@code .class} file in the directory?
     *
     * @param dir The directory
     * @return TRUE if there is
     * @throws IOException If the directory can't be walked
     */
    boolean in(final Path dir) throws IOException {
        Files.walkFileTree(
            dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, this
        );
        return this.found.get();
    }
}
