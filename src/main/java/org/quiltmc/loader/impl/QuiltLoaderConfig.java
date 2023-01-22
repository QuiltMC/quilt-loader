/*
 * Copyright 2022, 2023 QuiltMC
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

package org.quiltmc.loader.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jetbrains.annotations.VisibleForTesting;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/** User-configurable options. Normally loaded from "CURRENT_DIR/config/quilt-loader.txt". */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class QuiltLoaderConfig {

	// #######
	// General
	// #######

	/** If true then mod folders will be searched recursively. Folders that start with a digit will be ignored. */
	public final boolean loadSubFolders;

	/** If true (and {@link #loadSubFolders} is also true) then subfolders that start with a digit will be parsed as a
	 * version, and only loaded if they match the game version. */
	public final boolean restrictGameVersions;

	// #####
	// Debug
	// #####

	/** If true then the mod state window will always be shown, even if there aren't any warnings. If false then the mod
	 * state window will only be shown in a development environment when there are warnings. */
	public final boolean alwaysShowModStateWindow;

	/** If true then quilt-loader will only use the main thread when scanning, copying, etc. Very useful for debugging,
	 * since everything happens whenever plugins request it. Doesn't apply to the gui.
	 * <p>
	 * Note that all plugin methods are always invoked on the main thread - this only affects actions performed by
	 * quilt-loader, or tasks submitted by plugins. */
	public final boolean singleThreadedLoading;

	public QuiltLoaderConfig(Path from) {
		// Multi-threaded doesn't work yet, so hardcode this
		singleThreadedLoading = true;

		// Unfortunately this loads too early to use QuiltConfig
		// so instead just load from a properties file.
		Properties props = new Properties();

		if (Files.isRegularFile(from)) {
			try (InputStream stream = Files.newInputStream(from)) {
				props.load(stream);
			} catch (IOException io) {
				Log.warn(LogCategory.CONFIG, "Failed to read quilt-loader.txt!", io);
			}
		}

		Properties original = new Properties();
		original.putAll(props);

		loadSubFolders = getBool(props, "load_sub_folders", true);
		restrictGameVersions = getBool(props, "restrict_game_versions", true);
		alwaysShowModStateWindow = getBool(props, "always_show_mod_state_window", false);

		if (!original.equals(props)) {
			try (OutputStream out = Files.newOutputStream(from)) {
				props.store(out, "Quilt-loader configuration: https://github.com/QuiltMC/quilt-loader/wiki/Configuration-options");
			} catch (IOException io) {
				Log.warn(LogCategory.CONFIG, "Failed to write quilt-loader.txt!", io);
			}
		}
	}

	@VisibleForTesting
	QuiltLoaderConfig() {
		this.singleThreadedLoading = true;
		this.alwaysShowModStateWindow = false;
		this.loadSubFolders = true;
		this.restrictGameVersions = true;
	}

	private static boolean getBool(Properties props, String key, boolean _default) {
		String value = props.getProperty(key);
		if (value == null) {
			props.setProperty(key, Boolean.toString(_default));
			return _default;
		}

		// Require that exactly "true" or "false" are used to *change* from the default.
		boolean f = "false".equals(value);
		boolean t = "true".equals(value);
		if (t | f) {
			return t;
		}

		Log.warn(LogCategory.CONFIG, "Unknown / invalid config value for '" + key + "': " + value);

		return _default;
	}
}
