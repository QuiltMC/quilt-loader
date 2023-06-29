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

import org.quiltmc.loader.impl.QuiltLoaderImpl;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class GlobalPaths {

	private static Path config, cache;

	public static Path getConfigDir() {
		if (config == null) {
			String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

			if (os.contains("win")) {
				String configHome = System.getenv("LOCALAPPDATA");
				Path base = Paths.get(configHome);
				config = base.resolve("QuiltMC").resolve("QuiltLoaderAndMods");

			} else if (os.contains("mac")) {

				Path home = Paths.get(System.getProperty("user.home"));
				Path base = home.resolve("Library").resolve("Application Support");

				config = base.resolve("org.quiltmc.QuiltLoaderAndMods");

			} else {
				String configHome = System.getenv("XDG_CONFIG_HOME");
				if (configHome == null || configHome.isEmpty()) {
					configHome = System.getProperty("user.home");
					if (!configHome.endsWith("/")) {
						configHome += "/";
					}
					configHome += ".config";
				}
				Path base = Paths.get(configHome);
				config = base.resolve("quilt_loader_and_mods");
			}

			QuiltLoaderImpl.ensureDirExists(config, "global config");
		}
		return config;
	}

	public static Path getCacheDir() {
		if (cache == null) {
			String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

			if (os.contains("win")) {
				String configHome = System.getenv("LOCALAPPDATA");
				Path base = Paths.get(configHome);
				cache = base.resolve("QuiltMC").resolve("QuiltLoaderAndMods").resolve("Cache");

			} else if (os.contains("mac")) {

				Path home = Paths.get(System.getProperty("user.home"));
				Path base = home.resolve("Library").resolve("Caches");

				cache = base.resolve("org.quiltmc.QuiltLoaderAndMods");

			} else {
				String cacheHome = System.getenv("XDG_CACHE_HOME");
				if (cacheHome == null || cacheHome.isEmpty()) {
					cacheHome = System.getProperty("user.home");
					if (!cacheHome.endsWith("/")) {
						cacheHome += "/";
					}
					cacheHome += ".cache";
				}
				Path base = Paths.get(cacheHome);
				cache = base.resolve("quilt_loader_and_mods");
			}

			QuiltLoaderImpl.ensureDirExists(cache, "global cache");
		}
		return cache;
	}
}
