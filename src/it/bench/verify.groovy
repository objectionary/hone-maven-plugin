/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

String log = new File(basedir, 'target/hone/phi-optimized/org/eolang/bench/App.phi').text;
assert log.contains("mapMulti")

true
