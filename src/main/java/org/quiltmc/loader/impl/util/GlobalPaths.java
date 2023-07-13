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
				String configHome = System.getenv("LOCALAPPDATA");
				Path base = Paths.get(configHome);
				config = base.resolve("QuiltMC").resolve("QuiltLoaderAndMods");
				cache = base.resolve("QuiltMC").resolve("QuiltLoaderAndMods").resolve("Cache");

			} else if (os.contains("mac")) {
				Path home = Paths.get(System.getProperty("user.home"));
				Path base = home.resolve("Library").resolve("Application Support");

				config = base.resolve("org.quiltmc.QuiltLoaderAndMods");
				cache = base.resolve("org.quiltmc.QuiltLoaderAndMods");
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
				cache = base.resolve("quilt_loader_and_mods");
			}

			QuiltLoaderImpl.ensureDirExists(cache, "global cache");
			QuiltLoaderImpl.ensureDirExists(config, "global config");
		} catch (Throwable throwable) {
			Log.warn(LogCategory.GENERAL, "Unable to create global config and cache directories. Falling back to per-instance directories.", throwable);
			actuallyGlobal = false;
			config = QuiltLoader.getConfigDir().resolve("global");
			cache = QuiltLoader.getCacheDir().resolve("global");
			QuiltLoaderImpl.ensureDirExists(config, "fake global config");
			QuiltLoaderImpl.ensureDirExists(config, "fake global cache");
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
}
