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

package org.quiltmc.loader.impl.util;

import org.quiltmc.loader.impl.filesystem.QuiltBasePath;
import org.quiltmc.loader.impl.filesystem.QuiltClassPath;
import org.quiltmc.loader.impl.filesystem.QuiltMapFileSystem;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class SystemProperties {
	private SystemProperties() {}

	// ###########
	// # General #
	// ###########

	public static final String DEVELOPMENT = "loader.development";
	public static final String SIDE = "loader.side";
	public static final String GAME_JAR_PATH = "loader.gameJarPath";
	public static final String GAME_JAR_PATH_CLIENT = "loader.gameJarPath.client";
	public static final String GAME_JAR_PATH_SERVER = "loader.gameJarPath.server";

	public static final String GAME_VERSION = "loader.gameVersion";
	public static final String REMAP_CLASSPATH_FILE = "loader.remapClasspathFile";
	public static final String UNIT_TEST = "loader.unitTest";
	public static final String DEBUG_MOD_SOLVING = "loader.debug.mod_solving";
	public static final String PRINT_MOD_SOLVING_RESULTS = "loader.mod_solving.print_results";
	public static final String MODS_DIRECTORY = "loader.modsDir";
	public static final String CACHE_DIRECTORY = "loader.cacheDir";
	public static final String CONFIG_DIRECTORY = "loader.configDir";
	// the file extension to be used for configs that do not explicitly declare a specific extension
	public static final String DEFAULT_CONFIG_EXTENSION = "loader.defaultConfigExtension";
	// the file extension to use for ALL configs, overriding explicit defaults
	public static final String GLOBAL_CONFIG_EXTENSION = "loader.globalConfigExtension";
	public static final String LOG_FILE = "loader.log.file";
	public static final String LOG_LEVEL = "loader.log.level";
	public static final String SKIP_MC_PROVIDER = "loader.skipMcProvider";
	// additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
	// names that end with "\*" (windows) or "/*" (any) will make loader scan the folder, otherwise it will be loaded as a mod.
	public static final String ADD_MODS = "loader.addMods";
	// class path groups to map multiple class path entries to a mod (paths separated by path separator, groups by double path separator)
	public static final String PATH_GROUPS = "loader.classPathGroups";
	// system level libraries, matching code sources will not be assumed to be part of the game or mods and remain on the system class path (paths separated by path separator)
	public static final String SYSTEM_LIBRARIES = "loader.systemLibraries";
	public static final String DISABLE_FORKED_GUIS = "loader.disable_forked_guis";
	public static final String DEBUG_LOG_LIB_CLASSIFICATION = "loader.debug.logLibClassification";
	// throw exceptions from entrypoints, discovery etc. directly instead of gathering and attaching as suppressed
	public static final String DEBUG_THROW_DIRECTLY = "loader.debug.throwDirectly";
	// logs class transformation errors to uncover caught exceptions without adequate logging
	//public static final String DEBUG_LOG_TRANSFORM_ERRORS = "fabric.debug.logTransformErrors";
	// workaround for bad load order dependencies
	public static final String DEBUG_LOAD_LATE = "loader.debug.loadLate";
	// override the mod discovery timeout, unit in seconds, <= 0 to disable
	//public static final String DEBUG_DISCOVERY_TIMEOUT = "fabric.debug.discoveryTimeout";
	// override the mod resolution timeout, unit in seconds, <= 0 to disable
	//public static final String DEBUG_RESOLUTION_TIMEOUT = "fabric.debug.resolutionTimeout";
	// replace mod versions (modA:versionA,modB:versionB,...)
	public static final String DEBUG_REPLACE_VERSION = "loader.debug.replaceVersion";
	// defaults to 60 seconds; can be changed by setting the system property
	public static final String DEBUG_RESOLUTION_TIME_LIMIT = "loader.debug.resolutionTimeLimit";
	public static final String DEBUG_DUMP_OVERRIDE_PATHS = "loader.debug.dump_override_paths";
	public static final String ENABLE_EXPERIMENTAL_CHASM = "loader.experimental.enable_chasm";
	public static final String ENABLE_EXPERIMENTAL_LOADING_PLUGINS = "loader.experimental.allow_loading_plugins";
	public static final String JAR_COPIED_MODS = "loader.workaround.jar_copied_mods";
	public static final String JAR_COPY_ALL_MODS = "loader.workaround.jar_copy_all_mods";
	public static final String DISABLE_STRICT_PARSING = "loader.workaround.disable_strict_parsing";
	public static final String LOG_EARLY_CLASS_LOADS = "loader.debug.log_early_class_loads";
	public static final String DEBUG_CLASS_TO_MOD = "loader.debug.dump_class_to_mod";
	public static final String CACHE_SUFFIX = "loader.cache_suffix";
	public static final String DISABLE_OPTIMIZED_COMPRESSED_TRANSFORM_CACHE = "loader.transform_cache.disable_optimised_compression";
	public static final String DISABLE_PRELOAD_TRANSFORM_CACHE = "loader.transform_cache.disable_preload";
	public static final String LOG_CACHE_KEY_CHANGES = "loader.transform_cache.log_changed_keys";
	// enable useTempFile in ZipFileSystem, reduces memory usage when writing transform cache at the cost of speed
	public static final String USE_ZIPFS_TEMP_FILE = "loader.zipfs.use_temp_file";
	public static final String DISABLE_BEACON = "loader.disable_beacon";
	public static final String DEBUG_DUMP_FILESYSTEM_CONTENTS = "loader.debug.filesystem.dump_contents";
	public static final String ALWAYS_DEFER_FILESYSTEM_OPERATIONS = "loader.workaround.defer_all_filesystem_operations";
	public static final String DISABLE_QUILT_CLASS_PATH_CUSTOM_TABLE = "loader.quilt_class_path.disable_custom_table";

	// ##############
	// # Validation #
	// ##############

	/** Integer between 0 and 5. Controls various other validation properties, if they aren't specified.
	 * <ol start="0">
	 * <li>The default. Used for validation that isn't expected to cost performance, although these aren't controllable
	 * via a system property.</li>
	 * <li>Adds double-checking for some optimisations. Enabling these may cause a slight slowdown in some
	 * operations.</li>
	 * <li>UNUSED</li>
	 * <li>UNUSED</li>
	 * <li>Fairly expensive validation. This either implies a small increase in memory usage, or very large performance
	 * slowdown.</li>
	 * <li>Extremely expensive validation. Might imply heavy disk usage, or beyond 1000x performance slowdown for common
	 * tasks, or large increases in memory usage. (No properties use this at the moment).</li>
	 * </ol>
	 */
	public static final int VALIDATION_LEVEL = Integer.getInteger("loader.validation.level", 0);

	/** Controls validation for {@link QuiltClassPath}. Also enabled by {@link #VALIDATION_LEVEL} > 0. */
	public static final String VALIDATE_QUILT_CLASS_PATH = "loader.validation.quilt_class_path";

	/** Controls validation for {@link QuiltBasePath}. Also enabled by {@link #VALIDATION_LEVEL} > 0. */
	public static final String VALIDATE_QUILT_BASE_PATH = "loader.validation.quilt_base_path";

	/** Controls if {@link QuiltMapFileSystem} should validate its internal map on every filesystem operation. Also
	 * enabled by {@link #VALIDATION_LEVEL} > 3 */
	public static final String DEBUG_VALIDATE_FILESYSTEM_CONTENTS = "loader.debug.filesystem.validate_constantly";

	public static boolean getBoolean(String name, boolean fallbackDefault) {
		String value = System.getProperty(name);
		if (value == null) {
			return fallbackDefault;
		}
		return Boolean.parseBoolean(value);
	}
}
