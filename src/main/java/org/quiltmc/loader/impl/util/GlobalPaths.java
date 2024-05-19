/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class GlobalPaths {

	private static Path config, cache;
	private static boolean actuallyGlobal = true;

	static {
		try {
			String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

			if (os.contains("win")) {
				cache = pathFromEnv("TEMP").resolve("QuiltMC").resolve("QuiltLoaderAndMods");
				config = pathFromEnv("LOCALAPPDATA").resolve("QuiltMC").resolve("QuiltLoaderAndMods");
			} else if (os.contains("mac")) {
				String home = System.getProperty("user.home");

				cache = Paths.get(home, "Library", "Caches", "org.quiltmc.QuiltLoaderAndMods");
				config = Paths.get(home, "Library", "Application Support", "org.quiltmc.QuiltLoaderAndMods");
			} else {
				cache = pathFromEnv("XDG_CACHE_HOME", ".cache").resolve("quilt_loader_and_mods");
				config = pathFromEnv("XDG_CONFIG_HOME", ".config").resolve("quilt_loader_and_mods");
			}

			QuiltLoaderImpl.ensureDirExists(cache, "global cache");
			QuiltLoaderImpl.ensureDirExists(config, "global config");
		} catch (Throwable throwable) {
			Log.warn(LogCategory.GENERAL, "Unable to create global config and cache directories. Falling back to per-instance directories.", throwable);
			actuallyGlobal = false;

			cache = QuiltLoader.getCacheDir().resolve("global");
			config = QuiltLoader.getConfigDir().resolve("global");
			QuiltLoaderImpl.ensureDirExists(cache, "fake global cache");
			QuiltLoaderImpl.ensureDirExists(config, "fake global config");
		}
	}

	public static Path getConfigDir() {
		return config;
	}

	public static Path getCacheDir() {
		return cache;
	}

	public static boolean globalDirsEnabled() {
		return actuallyGlobal;
	}

	private static Path pathFromEnv(String name) {
		return pathFromEnv(name, null);
	}

	private static Path pathFromEnv(String name, String fallback) {
		String value = System.getenv(name);

		if (value != null && !value.isEmpty()) {
			return Paths.get(value);
		}

		if (fallback != null) {
			return Paths.get(System.getProperty("user.home"), fallback);
		} else {
			String os = System.getProperty("os.name");
			throw new RuntimeException("Missing env '" + name + "' for '" + os + "'");
		}
	}
}
