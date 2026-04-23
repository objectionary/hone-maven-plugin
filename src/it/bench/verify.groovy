/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

String log = new File(basedir, 'target/hone/phi-optimized/org/eolang/bench/App.phi').text;
assert log.contains("mapMulti")

String build = new File(basedir, 'build.log').text;
assert build.contains("Optimized 1/1 files"): "'Optimized 1/1 files' must appear in log";

true
