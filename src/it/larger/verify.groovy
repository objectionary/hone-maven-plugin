/*
 * MIT License
 *
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

String log = new File(basedir, 'build.log').text;
assert log.contains("BUILD SUCCESS")

true
