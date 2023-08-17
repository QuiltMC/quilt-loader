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
import java.nio.file.Files;
import java.nio.file.NotLinkException;
import java.nio.file.Path;

/** Similar to {@link Files}, but for {@link ExtendedFileSystem}. Unlike {@link Files}, most operations can take
 * {@link Path}s from any file system. */
public class ExtendedFiles {

	/** Copies the source file to the target file. If the source file system is read-only then the target file may
	 * become a link to the source file, which is fully copied when it is modified.
	 * <p>
	 * This method is a safe alternative to {@link #mount(Path, Path, MountOption...)}, when passing them
	 * {@link MountOption#COPY_ON_WRITE}, in the sense that it will copy the file if the filesystem doesn't support
	 * mounts. */
	public static Path copyOnWrite(Path source, Path target, CopyOption... options) throws IOException {
		if (target.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) target.getFileSystem()).copyOnWrite(source, target, options);
		} else {
			return Files.copy(source, target, options);
		}
	}

	/** Attempts to mount the source file onto the target file, such that all reads and writes to the target file
	 * actually read and write the source file. (The exact behaviour depends on the options given).
	 * <p>
	 * This is similar to {@link Files#createSymbolicLink(Path, Path, java.nio.file.attribute.FileAttribute...)}, but
	 * the source file and target file don't need to be on the same filesystem.
	 * <p>
	 * This does not support mounting folders.
	 * 
	 * @throws UnsupportedOperationException if the filesystem doesn't support this operation.
	 * @throws IOException if anything goes wrong while mounting the file. */
	public static Path mount(Path source, Path target, MountOption... options) throws IOException {
		if (target.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) target.getFileSystem()).mount(source, target, options);
		} else {
			throw new UnsupportedOperationException(target.getFileSystem() + " does not support file mounts!");
		}
	}

	/** @return True if the file has been mounted with {@link #mount(Path, Path, MountOption...)}. */
	public static boolean isMountedFile(Path file) {
		if (file.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) file.getFileSystem()).isMountedFile(file);
		} else {
			return false;
		}
	}

	/** @return True if the given file was created by {@link #mount(Path, Path, MountOption...)} with
	 *         {@link MountOption#COPY_ON_WRITE}, and the file has not been modified since it was copied. */
	public static boolean isCopyOnWrite(Path file) {
		if (file.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) file.getFileSystem()).isCopyOnWrite(file);
		} else {
			return false;
		}
	}

	/** Reads the target of a mounted file, if it was created by {@link #mount(Path, Path, MountOption...)}.
	 * 
	 * @throws NotLinkException if the given file is not a {@link #isMountedFile(Path)}.
	 * @throws UnsupportedOperationException if this filesystem doesn't support file mounts. */
	public static Path readMountTarget(Path file) throws IOException {
		if (file.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) file.getFileSystem()).readMountTarget(file);
		} else {
			throw new UnsupportedOperationException(file + " is not a mounted file!");
		}
	}
}
