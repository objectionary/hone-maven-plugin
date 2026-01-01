/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

String log = new File(basedir, 'target/hone/phi-optimized/org/eolang/bench/App.phi').text;
assert log.contains("mapMulti")

true
