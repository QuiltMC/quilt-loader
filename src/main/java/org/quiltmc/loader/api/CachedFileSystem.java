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

package org.quiltmc.loader.api;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Implemented by quilt-loaders {@link FileSystem} which shouldn't change after a certain point. This applies both to
 * filesystems which are stored in memory, and filesystems which are loaded from files on-disk. Filesystems which are
 * backed by folders (such as a development environment) will always return false from {@link #isPermanentlyReadOnly()}.
 * <p>
 * You need to call {@link #isPermanentlyReadOnly()} to check if that point has been reached. */
public interface CachedFileSystem {

	/** @return True if this {@link FileSystem} won't accept file creation, deletion, or modification, and the
	 *         underlying files are not expected to be changed either.
	 *         <p>
	 *         IMPORTANT: If this method ever returns true, then it will always return true in the future! (The inverse
	 *         doesn't hold true - a writable filesystem may become read-only after a certain point). */
	boolean isPermanentlyReadOnly();

	/** Direct method replacement for {@link Files#exists(Path, LinkOption...)}, which will be faster than it for
	 * {@link CachedFileSystem}s. */
	public static boolean doesExist(Path path, LinkOption... options) {
		if (path.getFileSystem() instanceof CachedFileSystem) {
			return ((CachedFileSystem) path.getFileSystem()).exists(path, options);
		} else {
			return Files.exists(path, options);
		}
	}

	/** Either the same speed as, or faster than {@link Files#exists(Path, LinkOption...)}. Otherwise this has the same
	 * semantics as {@link Files#exists(Path, LinkOption...)}.
	 * 
	 * @see #doesExist(Path, LinkOption...) */
	default boolean exists(Path path, LinkOption... options) {
		return Files.exists(path, options);
	}
}
