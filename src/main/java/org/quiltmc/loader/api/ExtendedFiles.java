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
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.filesystem.ByteArrayInputStreamSupplier;
import org.quiltmc.loader.api.filesystem.IOFunction;
import org.quiltmc.loader.api.filesystem.InputStreamSupplier;
import org.quiltmc.loader.api.filesystem.NotDynamicFileException;

/** Similar to {@link Files}, but for {@link ExtendedFileSystem}. Unlike {@link Files}, most operations can take
 * {@link Path}s from any file system. */
public class ExtendedFiles {

	/** Copies the source file to the target file. Unlike {@link Files#copy(Path, Path, CopyOption...)} and
	 * {@link FasterFiles#copy(Path, Path, CopyOption...)} this also copies dynamic files, while retaining their
	 * original supplier.
	 * <p>
	 * If either of the source or target file systems are not an {@link ExtendedFileSystem} then this method behaves
	 * identically to {@link FasterFiles#copy(Path, Path, CopyOption...)}
	 * 
	 * @return The target file.
	 * @throws IOException if anything goes wrong */
	public static Path copyExt(Path source, Path target, CopyOption... options) throws IOException {
		FileSystem fs = target.getFileSystem();
		if (fs instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) fs).copyExt(source, target, options);
		} else {
			return FasterFiles.copy(source, target, options);
		}
	}

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

	/** Mounts a sub-file of a source file onto the target file, such that all reads to the target file actually read
	 * from a sub-section of the source file, optionally decompressed. This method does not support modifying the source
	 * file - you must either specify the option {@link MountOption#READ_ONLY} or {@link MountOption#COPY_ON_WRITE}.
	 * <p>
	 * Unlike {@link #mount(Path, Path, MountOption...)}, there is explicitly no way to retrieve the original source
	 * file later, as this allows quilt to optimise recursive sub-file mounts.
	 * 
	 * @param source The file to mount from.
	 * @param offset Where the sub-file begins
	 * @param length The maximum length to read from the source file.
	 * @param decompressor Optional decompressor. This function will be called when the file is opened for reading
	 * @param decompressedLength The length of the output after decompression.
	 * @param target The destination path to mount.
	 * @throws UnsupportedOperationException if the filesystem doesn't support this operation.
	 * @throws IOException if anything goes wrong while mounting the file. */
	public static Path mountSubFile(Path source, long offset, int length, @Nullable IOFunction<InputStream,
		InputStream> decompressor, int decompressedLength, Path target, MountOption... options) throws IOException {
		if (target.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) target.getFileSystem()).mountSubFile(
				source, offset, length, decompressor, decompressedLength, target, options
			);
		} else {
			throw new UnsupportedOperationException(target.getFileSystem() + " does not support sub-file mounts!");
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

	/** Creates a new file in this file system that has dynamic content, provided by the given {@link Supplier}.
	 * <p>
	 * {@link Files#copy(Path, Path, CopyOption...) Copying} or {@link Files#move(Path, Path, CopyOption...) moving} the
	 * file will copy its contents at the time of copying (or moving), unless the target file system has an identical
	 * provider ({@link FileSystem#provider()}). If you want to be able to copy the supplier across providers you should
	 * use {@link ExtendedFiles#copyExt(Path, Path, CopyOption...)}
	 * 
	 * @param file a Path from this {@link ExtendedFileSystem}
	 * @param supplier The source for the dynamic file's content. This will be re-queried every time
	 *            {@link Files#newInputStream(Path, java.nio.file.OpenOption...)} is called.
	 * @return The file
	 * @throws IOException if there is already a file for the given path, or the parent file is not already a directory,
	 *             or if anything else goes wrong.
	 * @throws UnsupportedOperationException if this filesystem doesn't support dynamic files. */
	public static Path createDynamicFile(Path file, Supplier<byte[]> supplier) throws IOException {
		FileSystem fs = file.getFileSystem();
		if (fs instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) fs).createDynamicFile(file, supplier);
		} else {
			throw new UnsupportedOperationException(fs + " does not support dynamic files!");
		}
	}

	/** Creates a new file in this file system that has dynamic content, provided by the given
	 * {@link InputStreamSupplier}.
	 * <p>
	 * {@link Files#copy(Path, Path, CopyOption...) Copying} or {@link Files#move(Path, Path, CopyOption...) moving} the
	 * file will copy its contents at the time of copying (or moving), unless the target file system has an identical
	 * provider ({@link FileSystem#provider()}). If you want to be able to copy the supplier across providers you should
	 * use {@link ExtendedFiles#copyExt(Path, Path, CopyOption...)}
	 * 
	 * @param file a Path from this {@link ExtendedFileSystem}
	 * @param supplier The source for the dynamic file's content. This will be re-queried every time
	 *            {@link Files#newInputStream(Path, java.nio.file.OpenOption...)} is called.
	 * @return The file
	 * @throws IOException if there is already a file for the given path, or the parent file is not already a directory,
	 *             or if anything else goes wrong.
	 * @throws UnsupportedOperationException if this filesystem doesn't support dynamic files. */
	public static Path createDynamicFile(Path file, InputStreamSupplier supplier) throws IOException {
		FileSystem fs = file.getFileSystem();
		if (fs instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) fs).createDynamicFile(file, supplier);
		} else {
			throw new UnsupportedOperationException(fs + " does not support dynamic files!");
		}
	}

	/** @return True if the given file has been created by {@link #createDynamicFile(Path, Supplier)} */
	public static boolean isDynamicFile(Path file) {
		if (file.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) file.getFileSystem()).isDynamicFile(file);
		} else {
			return false;
		}
	}

	/** Retrieves the {@link InputStreamSupplier} that was used to create the given file, if it was created with
	 * {@link #createDynamicFile(Path, Supplier)} or {@link #createDynamicFile(Path, Supplier)}. The byte array version
	 * will always return {@link ByteArrayInputStreamSupplier}, which can be used to obtain the original
	 * {@link Supplier}.
	 * 
	 * @throws NotDynamicFileException if the given file is not a {@link #createDynamicFile(Path, Supplier) dynamic
	 *             file}
	 * @throws UnsupportedOperationException if the file system doesn't support dynamic files. */
	public static InputStreamSupplier readDynamicFileSource(Path file) throws NotDynamicFileException {
		if (file.getFileSystem() instanceof ExtendedFileSystem) {
			return ((ExtendedFileSystem) file.getFileSystem()).readDynamicFileSource(file);
		} else {
			throw new UnsupportedOperationException(file + " is not a dynamic file!");
		}
	}
}
