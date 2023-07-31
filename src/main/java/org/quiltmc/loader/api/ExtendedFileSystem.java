/*
 * Copyright 2023 QuiltMC
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

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NotLinkException;
import java.nio.file.Path;

/** A {@link FileSystem} which may support additional features, beyond those which normal file systems support. Similar
 * to regular file systems, you should generally use {@link ExtendedFiles} to perform these operations. */
public interface ExtendedFileSystem extends FasterFileSystem {

	/** Copies the source file to the target file. If the source file system is read-only then this will
	 * {@link #mount(Path, Path, MountOption...)} the given file with {@link MountOption#COPY_ON_WRITE}.
	 * 
	 * @param source A {@link Path}, which might not be in this {@link FileSystem}.
	 * @param target A {@link Path} which must be from this {@link ExtendedFileSystem}
	 * @return target */
	default Path copyOnWrite(Path source, Path target, CopyOption... options) throws IOException {
		return Files.copy(source, target, options);
	}

	/** Mounts the given source file on the target file, such that all reads and writes will actually read and write the
	 * source file. (The exact behaviour depends on the options given).
	 * <p>
	 * This is similar to {@link Files#createSymbolicLink(Path, Path, java.nio.file.attribute.FileAttribute...)} except
	 * the source and target files don't need to be on the same filesystem.
	 * <p>
	 * Note that this does not support mounting folders.
	 * 
	 * @param source A path from any {@link FileSystem}.
	 * @param target A path from this {@link ExtendedFileSystem}.
	 * @param options Options which control how the file is mounted.
	 * @throws UnsupportedOperationException if this filesystem doesn't support file mounts. */
	default Path mount(Path source, Path target, MountOption... options) throws IOException {
		throw new UnsupportedOperationException(getClass() + " doesn't support ExtendedFileSystem.mount");
	}

	/** @return True if the file has been mounted with {@link #mount(Path, Path, MountOption...)}. */
	default boolean isMountedFile(Path file) {
		return false;
	}

	/** @return True if the given file was created by {@link #mount(Path, Path, MountOption...)} with
	 *         {@link MountOption#COPY_ON_WRITE}, and the file has not been modified since it was copied. */
	default boolean isCopyOnWrite(Path file) {
		return false;
	}

	/** Reads the target of a mounted file, if it was created by {@link #mount(Path, Path, MountOption...)}.
	 * 
	 * @throws NotLinkException if the given file is not a {@link #isMountedFile(Path)}.
	 * @throws UnsupportedOperationException if this filesystem doesn't support file mounts. */
	default Path readMountTarget(Path file) throws IOException {
		throw new UnsupportedOperationException(getClass() + " doesn't support ExtendedFileSystem.mount");
	}
}
