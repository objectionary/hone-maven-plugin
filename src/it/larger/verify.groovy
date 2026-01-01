/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

String log = new File(basedir, 'build.log').text;
assert log.contains("BUILD SUCCESS")

true
