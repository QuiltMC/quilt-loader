/*
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

package org.quiltmc.loader.impl;

import java.nio.file.FileSystems;

/** User-configurable options. Normally loaded from "CURRENT_DIR/config/quilt-loader.txt". */
public final class QuiltLoaderConfig {

	// #######
	// General
	// #######

	/** If true then mod folders will be searched recursively. Folders that start with a digit will be ignored. */
	public final boolean loadSubFolders;

	/** If true (and {@link #loadSubFolders} is also true) then subfolders that start with a digit will be parsed as a
	 * version, and only loaded if they match the game version. */
	public final boolean restrictGameVersions;

	// ###########
	// Performance
	// ###########

	/** What method to use when loading zip/jar archives that are directly in the filesystem. The default is
	 * {@link ZipLoadType#READ_ZIP}. */
	public final ZipLoadType outerZipLoadType;

	/** What method to use when loading zip/jar archives that are inside an existing zip/jar file. The default is
	 * {@link ZipLoadType#COPY_TO_MEMORY}. */
	public final ZipLoadType innerZipLoadType;

	public enum ZipLoadType {

		/** Directly reads zip files using {@link FileSystems#newFileSystem(java.nio.file.Path, ClassLoader)}. */
		READ_ZIP,

		/** Copies inner zips out from their containing zips to a separate directory (I.E. not any mods folder), and
		 * then reads those like {@link #READ_ZIP}. */
		COPY_ZIP,

		/** Copies the contents of a zip into a memory-based filesystem. */
		COPY_TO_MEMORY;
	}

	// #####
	// Debug
	// #####

	/** If true then quilt-loader will only use the main thread when scanning, copying, etc. Very useful for debugging,
	 * since everything happens whenever plugins request it. Doesn't apply to the gui.
	 * <p>
	 * Note that all plugin methods are always invoked on the main thread - this only affects actions performed by
	 * quilt-loader, or tasks submitted by plugins. */
	public final boolean singleThreadedLoading;

	public QuiltLoaderConfig() {
		// FOR NOW

		loadSubFolders = true;
		restrictGameVersions = true;
		outerZipLoadType = ZipLoadType.READ_ZIP;
		innerZipLoadType = ZipLoadType.COPY_TO_MEMORY;
		singleThreadedLoading = true;
	}
}
