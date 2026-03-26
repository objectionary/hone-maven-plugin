/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
import java.nio.file.Files;

String log = new File(basedir, 'build.log').text;
assert log.contains("BUILD SUCCESS")

// 'common' module contains hone-statistics.csv file
def common = new File(basedir, 'common/target/hone-statistics.csv').toPath();
assert Files.exists(common): "File 'common/target/hone-statistics.csv' does not exist";
assert Files.readAllLines(common).size() == 2: "header and one line";
assert common.toFile().text.contains("Common.phi");

// 'client' module contains hone-statistics.csv file
def client = new File(basedir, 'client/target/hone-statistics.csv').toPath();
assert Files.exists(client): "File 'client/target/hone-statistics.csv' does not exist";
assert Files.readAllLines(client).size() == 2: "header and one line";
assert client.toFile().text.contains("Client.phi");

// 'server' module contains hone-statistics.csv file
def server = new File(basedir, 'server/target/hone-statistics.csv').toPath();
assert Files.exists(server): "File 'server/target/hone-statistics.csv' does not exist";
assert Files.readAllLines(server).size() == 2: "header and one line";
assert server.toFile().text.contains("Server.phi");

/*
@todo #440:90min build a top-level statistics file for a modular project.
 Currently, each module contains its own hone-statistics.csv file.
 We need to aggregate these files into a single one at the top level of the project.
 This will allow us to have a comprehensive view of the statistics for the entire 
 project in one place. The aggregated file should contain all the entries from the
 individual module files.
 When this task is completed, uncomment the code below and make sure it works correctly.

def main = new File(basedir, 'target/hone-statistics.csv').toPath();
assert Files.exists(main): "File 'target/hone-statistics.csv' does not exist";
assert Files.readAllLines(main).size() == 4: "header and three lines";
assert main.toFile().text.contains("Common.phi");
assert main.toFile().text.contains("Client.phi");
assert main.toFile().text.contains("Server.phi");
*/

true
