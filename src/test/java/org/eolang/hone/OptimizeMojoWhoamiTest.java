/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link OptimizeMojo#whoami}.
 * @since 0.6.0
 */
@SuppressWarnings("JTCOP.RuleEveryTestHasProductionClass")
final class OptimizeMojoWhoamiTest {

    @Test
    void formatsWhoamiAsUidColonGid() {
        MatcherAssert.assertThat(
            "whoami must format the Docker --user value as 'uid:gid', not 'uid:euid' (see #492)",
            OptimizeMojo.whoami(
                new OptimizeMojoWhoamiTest.FakeCLibrary(1000, 2000, 3000)
            ),
            Matchers.is("1000:3000")
        );
    }

    /**
     * Fixed-value stub of {@link OptimizeMojo.CLibrary} that returns
     * distinct values for uid, euid, and gid so callers can be checked
     * for picking up the right one.
     * @since 0.6.0
     */
    private static final class FakeCLibrary implements OptimizeMojo.CLibrary {

        /**
         * Real user ID to return.
         */
        private final int uid;

        /**
         * Effective user ID to return.
         */
        private final int euid;

        /**
         * Group ID to return.
         */
        private final int gid;

        /**
         * Ctor.
         * @param ruid Real user ID
         * @param reuid Effective user ID
         * @param rgid Group ID
         */
        FakeCLibrary(final int ruid, final int reuid, final int rgid) {
            this.uid = ruid;
            this.euid = reuid;
            this.gid = rgid;
        }

        @Override
        public int getuid() {
            return this.uid;
        }

        @Override
        public int geteuid() {
            return this.euid;
        }

        @Override
        public int getgid() {
            return this.gid;
        }
    }
}
