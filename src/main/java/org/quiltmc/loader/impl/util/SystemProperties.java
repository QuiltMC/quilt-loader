/*
 * Copyright 2016 FabricMC
 * Copyright 2022 QuiltMC
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

public final class SystemProperties {
	public static final String DEVELOPMENT = "loader.development";
	public static final String SIDE = "loader.side";
	public static final String GAME_JAR_PATH = "loader.gameJarPath";
	public static final String GAME_VERSION = "loader.gameVersion";
	public static final String REMAP_CLASSPATH_FILE = "loader.remapClasspathFile";
	public static final String DEBUG_MOD_RESOLVING = "loader.debug.mod_resolving";
	public static final String DEBUG_MOD_SOLVING = "loader.debug.mod_solving";
	public static final String MODS_DIRECTORY = "loader.modsDir";
	public static final String CONFIG_DIRECTORY = "loader.configDir";
	// the file extension to be used for configs that do not explicitly declare a specific extension
	public static final String DEFAULT_CONFIG_EXTENSION = "loader.defaultConfigExtension";
	// the file extension to use for ALL configs, overriding explicit defaults
	public static final String GLOBAL_CONFIG_EXTENSION = "loader.globalConfigExtension";
	public static final String LOG_FILE = "loader.log.file";
	public static final String LOG_LEVEL = "loader.log.level";
	public static final String SKIP_MC_PROVIDER = "loader.skipMcProvider";
	// additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
	public static final String ADD_MODS = "loader.addMods";
	// class path groups to map multiple class path entries to a mod (paths separated by path separator, groups by double path separator)
	public static final String PATH_GROUPS = "loader.classPathGroups";
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
	private SystemProperties() {
	}
}
