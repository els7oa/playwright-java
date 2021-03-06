/*
 * Copyright (c) Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.playwright.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DriverJar extends Driver {
  private final Path driverTempDir;

  DriverJar() throws IOException, URISyntaxException, InterruptedException {
    driverTempDir = Files.createTempDirectory("playwright-java-");
    driverTempDir.toFile().deleteOnExit();
    extractDriverToTempDir();
    installBrowsers();
  }

  private void installBrowsers() throws IOException, InterruptedException {
    Path driver = driverTempDir.resolve("playwright-cli");
    ProcessBuilder pb = new ProcessBuilder(driver.toString(), "install");
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    Process p = pb.start();
    boolean result = p.waitFor(10, TimeUnit.MINUTES);
    if (!result) {
      throw new RuntimeException("Timed out waiting for browsers to install");
    }
  }

  private void extractDriverToTempDir() throws URISyntaxException, IOException {
    ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    URI uri = classloader.getResource("driver/" + platformDir()).toURI();
    // Create zip filesystem if loading from jar.
    try (FileSystem fileSystem = "jar".equals(uri.getScheme()) ? FileSystems.newFileSystem(uri, Collections.emptyMap()) : null) {
      Files.list(Paths.get(uri)).forEach(filePath -> {
        try {
          extractResource(filePath, driverTempDir);
        } catch (IOException e) {
          throw new RuntimeException("Failed to extract driver from " + uri, e);
        }
      });
    }
  }

  private static String platformDir() {
    String name = System.getProperty("os.name").toLowerCase();
    if (name.contains("windows")) {
      return System.getProperty("os.arch").equals("amd64") ? "win32_x64" : "win32";
    }
    if (name.contains("linux")) {
      return "linux";
    }
    if (name.contains("mac os x")) {
      return "mac";
    }
    throw new RuntimeException("Unexpected os.name value: " + name);
  }

  private static Path extractResource(Path from, Path toDir) throws IOException {
    Path path = toDir.resolve(from.getFileName().toString());
    Files.copy(from, path);
    path.toFile().setExecutable(true);
    path.toFile().deleteOnExit();
    return path;
  }

  @Override
  Path driverDir() {
    return driverTempDir;
  }
}
