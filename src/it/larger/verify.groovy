/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */

String log = new File(basedir, 'build.log').text;
assert log.contains("BUILD SUCCESS")
assert log =~ /Optimized [1-9]\d*\/\d+ files/: "at least one file must be optimized, not just assembled"

File stats = new File(basedir, 'target/hone-statistics.csv');
assert stats.exists(): "File 'hone-statistics.csv' does not exist"
String row = stats.readLines().find { it.contains('Shelf.phi') }
assert row != null: "Shelf.phi must be listed in hone-statistics.csv"
assert (row =~ /Shelf\.phi","[^"]*",[1-9]\d*,/): "Shelf.phi must have a positive Changed count, but the row is: ${row}"

true
