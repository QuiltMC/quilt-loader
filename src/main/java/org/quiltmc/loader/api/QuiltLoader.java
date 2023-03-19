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

package org.quiltmc.loader.api;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.ObjectShare;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.entrypoint.EntrypointException;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

import net.fabricmc.api.EnvType;

/**
 * The public-facing QuiltLoader instance.
 */
public final class QuiltLoader {
	private QuiltLoader() {}

	/**
	 * Returns all entrypoints declared under a {@code key}, assuming they are of a specific type.
	 *
	 * @param key  the key in entrypoint declaration in {@code fabric.mod.json}
	 * @param type the type of entrypoints
	 * @param <T>  the type of entrypoints
	 * @return the obtained entrypoints
	 * @throws EntrypointException if a problem arises during entrypoint creation
	 * @see #getEntrypointContainers(String, Class)
	 */
	public static <T> List<T> getEntrypoints(String key, Class<T> type) throws EntrypointException {
		return impl().getEntrypoints(key, type);
	}

	/**
	 * Returns all entrypoints declared under a {@code key}, assuming they are of a specific type.
	 *
	 * <p>The entrypoint is declared in the {@code fabric.mod.json} as following:
	 * <pre><blockquote>
	 *   "entrypoints": {
	 *     "&lt;a key&gt;": [
	 *       &lt;a list of entrypoint declarations&gt;
	 *     ]
	 *   }
	 * </blockquote></pre>
	 * Multiple keys can be present in the {@code entrypoints} section.</p>
	 *
	 * <p>An entrypoint declaration indicates that an arbitrary notation is sent
	 * to a {@link LanguageAdapter} to offer an instance of entrypoint. It is
	 * either a string, or an object. An object declaration
	 * is of this form:<pre><blockquote>
	 *   {
	 *     "adapter": "&lt;a custom adatper&gt;"
	 *     "value": "&lt;an arbitrary notation&gt;"
	 *   }
	 * </blockquote></pre>
	 * A string declaration {@code <an arbitrary notation>} is equivalent to
	 * <pre><blockquote>
	 *   {
	 *     "adapter": "default"
	 *     "value": "&lt;an arbitrary notation&gt;"
	 *   }
	 * </blockquote></pre>
	 * where the {@code default} adapter is the {@linkplain LanguageAdapter adapter}
	 * offered by Fabric Loader. </p>
	 *
	 * @param key  the key in entrypoint declaration in {@code fabric.mod.json}
	 * @param type the type of entrypoints
	 * @param <T>  the type of entrypoints
	 * @return the entrypoint containers related to this key
	 * @throws EntrypointException if a problem arises during entrypoint creation
	 * @see LanguageAdapter
	 */
	public static <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) throws EntrypointException {
		return impl().getEntrypointContainers(key, type);
	}

	/**
	 * Get the current mapping resolver.
	 *
	 * <p>When performing reflection, a mod should always query the mapping resolver for
	 * the remapped names of members than relying on other heuristics.</p>
	 *
	 * @return the current mapping resolver instance
	 * @since 0.4.1
	 */
	public static MappingResolver getMappingResolver() {
		return impl().getMappingResolver();
	}

	/**
	 * Gets the container for a given mod.
	 *
	 * @param id the ID of the mod
	 * @return the mod container, if present
	 */
	public static Optional<ModContainer> getModContainer(String id) {
		return impl().getModContainer(id);
	}

	/**
	 * Gets all mod containers.
	 *
	 * @return a collection of all loaded mod containers
	 */
	public static Collection<ModContainer> getAllMods() {
		return impl().getAllMods();
	}

	/**
	 * Checks if a mod with a given ID is loaded.
	 *
	 * @param id the ID of the mod, as defined in {@code fabric.mod.json}
	 * @return whether or not the mod is present in this Fabric Loader instance
	 */
	public static boolean isModLoaded(String id) {
		return impl().isModLoaded(id);
	}

	/**
	 * Gets the mod container that provides the given class.
	 * <p>
	 * This only works for classes loaded by the system classloader and quilt-loader itself.
	 */
	public static Optional<ModContainer> getModContainer(Class<?> clazz) {
		return impl().getModContainer(clazz);
	}

	/**
	 * Checks if Fabric Loader is currently running in a "development"
	 * environment. Can be used for enabling debug mode or additional checks.
	 *
	 * <p>This should not be used to make assumptions on certain features,
	 * such as mappings, but as a toggle for certain functionalities.</p>
	 *
	 * @return whether or not Loader is currently in a "development"
	 * environment
	 */
	public static boolean isDevelopmentEnvironment() {
		return impl().isDevelopmentEnvironment();
	}

	/**
	 * Get the current game instance. Can represent a game client or
	 * server object. As such, the exact return is dependent on the
	 * current environment type.
	 *
	 * <p>The game instance may not always be available depending on the game version and {@link EnvType environment}.
	 *
	 * @return A client or server instance object
	 * @deprecated This method is experimental and it's use is discouraged.
	 */
	@Nullable
	@Deprecated
	public static Object getGameInstance() {
		return impl().getGameInstance();
	}

	/**
	 * Gets the game version, normalised to be usefully compared via
	 * {@link Version#compareTo(Version)} to other versions of the same game.
	 *
	 * @return A normalised version.
	 * @see #getRawGameVersion()
	 */
	public static String getNormalizedGameVersion() {
		return impl().getGameProvider().getNormalizedGameVersion();
	}

	/**
	 * Gets the game version, unnormalised. This generally won't be
	 * usefully comparable to other versions of the same game.

	 * @return A string. This wont be empty or null.
	 * @see #getNormalizedGameVersion()
	 */
	public static String getRawGameVersion() {
		return impl().getGameProvider().getRawGameVersion();
	}

	/**
	 * Get the current game working directory.
	 *
	 * @return the working directory
	 */
	public static Path getGameDir() {
		return impl().getGameDir();
	}

	/**
	 * Get the current directory for temporary files.
	 *
	 * @return the cache directory
	 * @since 0.18.3
	 */
	public static Path getCacheDir() {
		return impl().getCacheDir();
	}

	/**
	 * Get the current directory for game configuration files.
	 *
	 * @return the configuration directory
	 */
	public static Path getConfigDir() {
		return impl().getConfigDir();
	}

	/**
	 * Gets the command line arguments used to launch the game. If this is printed for debugging, make sure {@code sanitize} is {@code true}.
	 * @param sanitize Whether to remove sensitive information
	 * @return the launch arguments
	 */
	public static String[] getLaunchArguments(boolean sanitize) {
		return impl().getLaunchArguments(sanitize);
	}

	/**
	 * Get the object share for inter-mod communication.
	 *
	 * <p>The share allows mods to exchange data without directly referencing each other. This makes simple interaction
	 * easier by eliminating any compile- or run-time dependencies if the shared value type is independent of the mod
	 * (only Java/game/Fabric types like collections, primitives, String, Consumer, Function, ...).
	 *
	 * <p>Active interaction is possible as well since the shared values can be arbitrary Java objects. For example
	 * exposing a {@code Runnable} or {@code Function} allows the "API" user to directly invoke some program logic.
	 *
	 * <p>It is required to prefix the share key with the mod id like {@code mymod:someProperty}. Mods should not
	 * modify entries by other mods. The share is thread safe.
	 *
	 * @return the global object share instance
	 */
	public static ObjectShare getObjectShare() {
		return impl().getObjectShare();
	}

	/**
	 * Creates a table describing the mods currently loaded, suitable for printing in log files or in crash reports.
	 * All of the information contained here is available through {@link #getAllMods()}.
	 */
	public static String createModTable() {
		return impl().createModTable();
	}

	private static QuiltLoaderImpl impl() {
		if (QuiltLoaderImpl.INSTANCE == null) {
			throw new RuntimeException("Accessed QuiltLoader too early!");
 		}

		return QuiltLoaderImpl.INSTANCE;
	}
}
