package org.quiltmc.loader.impl.util;

import java.nio.file.Files;
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
				if (!Files.exists(base)) {
					throw new Error("Bad LOCALAPPDATA '" + configHome + "'");
				}

				config = base.resolve("QuiltMC").resolve("QuiltLoaderAndMods");

			} else if (os.contains("mac")) {

				Path home = Paths.get(System.getProperty("user.home"));
				Path base = home.resolve("Library").resolve("Application Support");

				config = base.resolve("org.quiltmc.QuiltLoaderAndMods");

			} else {
				String configHome = System.getenv("XDG_CONFIG_HOME");
				if (configHome == null) {
					configHome = System.getProperty("user.home");
					if (!configHome.endsWith("/")) {
						configHome += "/";
					}
					configHome += ".config";
				}
				Path base = Paths.get(configHome);
				if (!Files.exists(base)) {
					throw new Error("Bad XDG_CONFIG_HOME '" + configHome + "'");
				}

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
				if (!Files.exists(base)) {
					throw new Error("Bad LOCALAPPDATA '" + configHome + "'");
				}

				cache = base.resolve("QuiltMC").resolve("QuiltLoaderAndMods").resolve("Cache");

			} else if (os.contains("mac")) {

				Path home = Paths.get(System.getProperty("user.home"));
				Path base = home.resolve("Library").resolve("Caches");

				cache = base.resolve("org.quiltmc.QuiltLoaderAndMods");

			} else {
				String cacheHome = System.getenv("XDG_CACHE_HOME");
				if (cacheHome == null) {
					cacheHome = System.getProperty("user.home");
					if (!cacheHome.endsWith("/")) {
						cacheHome += "/";
					}
					cacheHome += ".cache";
				}
				Path base = Paths.get(cacheHome);
				if (!Files.exists(base)) {
					throw new Error("Bad XDG_CACHE_HOME '" + cacheHome + "'");
				}

				cache = base.resolve("quilt_loader_and_mods");
			}

			QuiltLoaderImpl.ensureDirExists(cache, "global cache");
		}
		return cache;
	}
}
