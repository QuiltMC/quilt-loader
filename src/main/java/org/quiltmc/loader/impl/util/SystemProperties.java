/*
 * Copyright 2016 FabricMC
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
	public static final String DEVELOPMENT = "fabric.development";
	public static final String SIDE = "fabric.side";
	public static final String GAME_JAR_PATH = "fabric.gameJarPath";
	public static final String GAME_VERSION = "fabric.gameVersion";
	public static final String REMAP_CLASSPATH_FILE = "fabric.remapClasspathFile";
	public static final String LAUNCHER_NAME = "fabric.launcherName";
	public static final String DEBUG_MOD_RESOLVING = "fabric.debug.mod_resolving";
	public static final String DEBUG_MOD_SOLVING = "fabric.debug.mod_solving";
	public static final String MODS_DIRECTORY = "fabric.modsDir";
	public static final String CONFIG_DIRECTORY = "fabric.configDir";
	public static final String LOG_FILE = "fabric.log.file";
	public static final String LOG_LEVEL = "fabric.log.level";
	public static final String SKIP_MC_PROVIDER = "fabric.skipMcProvider";
	// TODO: check which of these is used
	// additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
	public static final String ADD_MODS = "fabric.addMods";
	// file containing the class path for in-dev runtime mod remapping
	public static final String REMAP_CLASSPATH_FILE = "fabric.remapClasspathFile";
	// class path groups to map multiple class path entries to a mod (paths separated by path separator, groups by double path separator)
	public static final String PATH_GROUPS = "fabric.classPathGroups";
	// throw exceptions from entrypoints, discovery etc. directly instead of gathering and attaching as suppressed
	public static final String DEBUG_THROW_DIRECTLY = "fabric.debug.throwDirectly";
	// logs class transformation errors to uncover caught exceptions without adequate logging
	public static final String DEBUG_LOG_TRANSFORM_ERRORS = "fabric.debug.logTransformErrors";
	// disables mod load order shuffling to be the same in-dev as in production
	public static final String DEBUG_DISABLE_MOD_SHUFFLE = "fabric.debug.disableModShuffle";
	// workaround for bad load order dependencies
	public static final String DEBUG_LOAD_LATE = "fabric.debug.loadLate";
	// override the mod discovery timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_DISCOVERY_TIMEOUT = "fabric.debug.discoveryTimeout";
	// override the mod resolution timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_RESOLUTION_TIMEOUT = "fabric.debug.resolutionTimeout";
	// replace mod versions (modA:versionA,modB:versionB,...)
	public static final String DEBUG_REPLACE_VERSION = "fabric.debug.replaceVersion";
	private SystemProperties() {
	}
}
