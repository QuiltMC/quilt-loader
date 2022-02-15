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

package org.quiltmc.loader.api;

import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ModContainer {

	ModMetadata metadata();

	/**
	 * Returns the root directory of the mod (inside JAR/folder), exposing its contents.
	 *
	 * <p>A path returned by this method may be incompatible with {@link Path#toFile} as its FileSystem doesn't
	 * necessarily represent the OS file system, but potentially a virtual view of jar contents or another abstraction.
	 *
	 * @return the root directory of the mod.
	 */
	Path rootPath();

	/**
	 * Gets an NIO reference to a file inside the JAR.
	 * 
	 * <p>The path is not guaranteed to exist!</p>
	 *
	 * <p>A path returned by this method may be incompatible with {@link Path#toFile} as its FileSystem doesn't
	 * necessarily represent the OS file system, but potentially a virtual view of jar contents or another abstraction.
	 *
	 * @param file The location from root, using {@code /} as a separator.
	 * @return the path to a given file
	 */
	default Path getPath(String file) {
		Path root = rootPath();
		return root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
	}

	/**
	 * Gets where the mod was loaded from originally, the mod jar/folder itself.
	 *
	 * <p>This location is not necessarily identical to the code source used at runtime, a mod may get copied or
	 * otherwise transformed before being put on the class path. It thus mostly represents the installation and initial
	 * loading, not what is being directly accessed at runtime.
	 *
	 * <p>The mod origin is provided for working with the installation like telling the user where a mod has been
	 * installed at. Accessing the files inside a mod jar/folder should use {@link #getPath} and {@link #rootPath}
	 * instead. Those also abstract jar accesses through the virtual {@code ZipFileSystem} away.
	 *
	 * @return mod origin
	 */
	// TODO: fabric class
	ModOrigin origin();
}
