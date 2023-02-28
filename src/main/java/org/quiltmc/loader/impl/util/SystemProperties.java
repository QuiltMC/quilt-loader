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

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class SystemProperties {
	public static final String DEVELOPMENT = "loader.development";
	public static final String SIDE = "loader.side";
	public static final String GAME_JAR_PATH = "loader.gameJarPath";
	public static final String GAME_JAR_PATH_CLIENT = "loader.gameJarPath.client";
	public static final String GAME_JAR_PATH_SERVER = "loader.gameJarPath.server";

	public static final String GAME_VERSION = "loader.gameVersion";
	public static final String REMAP_CLASSPATH_FILE = "loader.remapClasspathFile";
	public static final String DEBUG_MOD_SOLVING = "loader.debug.mod_solving";
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
	public static final String JAR_COPIED_MODS = "loader.workaround.jar_copied_mods";
	public static final String LOG_EARLY_CLASS_LOADS = "loader.debug.log_early_class_loads";
	public static final String DEBUG_CLASS_TO_MOD = "loader.debug.dump_class_to_mod";
	public static final String CACHE_SUFFIX = "loader.cache_suffix";
	public static final String DISABLE_OPTIMIZED_COMPRESSED_TRANSFORM_CACHE = "loader.transform_cache.disable_optimised_compression";
	public static final String DISABLE_PRELOAD_TRANSFORM_CACHE = "loader.transform_cache.disable_preload";

	private SystemProperties() {
	}
}
