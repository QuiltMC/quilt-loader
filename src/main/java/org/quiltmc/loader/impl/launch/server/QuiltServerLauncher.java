/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.launch.server;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class QuiltServerLauncher {
	private static final ClassLoader parentLoader = QuiltServerLauncher.class.getClassLoader();
	private static String mainClass = "net.fabricmc.loader.launch.knot.KnotServer";

	public static void main(String[] args) {
		URL propUrl = parentLoader.getResource("quilt-server-launch.properties");

		if (propUrl != null) {
			Properties properties = new Properties();

			try (InputStreamReader reader = new InputStreamReader(propUrl.openStream(), StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (properties.containsKey("launch.mainClass")) {
				mainClass = properties.getProperty("launch.mainClass");
			}
		}

		boolean dev = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));

		if (!dev) {
			try {
				setup(args);
			} catch (Exception e) {
				throw new RuntimeException("Failed to setup Quilt server environment!", e);
			}
		}

		try {
			Class<?> c = Class.forName(mainClass);
			c.getMethod("main", String[].class).invoke(null, (Object) args);
		} catch (Exception e) {
			throw new RuntimeException("An exception occurred when launching the server!", e);
		}
	}

	private static void setup(String... runArguments) throws IOException {
		if (System.getProperty(SystemProperties.GAME_JAR_PATH) == null) {
			System.setProperty(SystemProperties.GAME_JAR_PATH, getServerJarPath());
		}

		Path serverJar = Paths.get(System.getProperty(SystemProperties.GAME_JAR_PATH)).toAbsolutePath().normalize();

		if (!Files.exists(serverJar)) {
			System.err.println("The Minecraft server .JAR is missing (" + serverJar + ")!");
			System.err.println();
			System.err.println("Quilt's server-side launcher expects the server .JAR to be provided.");
			System.err.println("You can edit its location in quilt-server-launcher.properties.");
			System.err.println();
			System.err.println("Without the official Minecraft server .JAR, Quilt Loader cannot launch.");
			throw new RuntimeException("Missing game jar at " + serverJar);
		}
	}

	private static String getServerJarPath() throws IOException {
		// Pre-load "quilt-server-launcher.properties"
		Path propertiesFile = Paths.get("quilt-server-launcher.properties");
		Properties properties = new Properties();

		if (Files.exists(propertiesFile)) {
			try (Reader reader = Files.newBufferedReader(propertiesFile)) {
				properties.load(reader);
			}
		}

		// Most popular Minecraft server hosting platforms do not allow
		// passing arbitrary arguments to the server .JAR. Meanwhile,
		// Mojang's default server filename is "server.jar" as of
		// a few versions... let's use this.
		if (!properties.containsKey("serverJar")) {
			properties.put("serverJar", "server.jar");

			try (Writer writer = Files.newBufferedWriter(propertiesFile)) {
				properties.store(writer, null);
			}
		}

		return (String) properties.get("serverJar");
	}
}
