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
	public static final String DEVELOPMENT = "quilt.development";
	public static final String SIDE = "quilt.side";
	public static final String GAME_JAR_PATH = "quilt.gameJarPath";
	public static final String GAME_VERSION = "quilt.gameVersion";
	public static final String REMAP_CLASSPATH_FILE = "quilt.remapClasspathFile";
	public static final String LAUNCHER_NAME = "quilt.launcherName";
	public static final String DEBUG_MOD_RESOLVING = "quilt.debug.mod_resolving";
	public static final String MODS_DIRECTORY = "quilt.modsDir";
	public static final String CONFIG_DIRECTORY = "quilt.configDir";

	private SystemProperties() {
	}
}
