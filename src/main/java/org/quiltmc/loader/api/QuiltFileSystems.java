/*
 * Copyright 2025 QuiltMC
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
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.quiltmc.loader.impl.filesystem.QuiltFileSystemsImpl;

/** Factory methods for quilt's {@link ExtendedFileSystem} */
public final class QuiltFileSystems {

	/** Creates a new {@link ExtendedFileSystem} which stores all of its content in-memory. The filesystem does not need
	 * to be explicitly closed, and calling {@link FileSystem#close()} will have no effect.
	 * 
	 * @param name A name for the file system, used for the URL and in logging. Invalid characters will be sanitized.
	 * @return A reference to the file system */
	public static ExtendedFileSystemRef createExtendedFileSystem(String name) {
		return QuiltFileSystemsImpl.createExtendedFileSystem(name);
	}

	/** Creates a new {@link ExtendedFileSystem} which loads zip file contents directly from the given file.
	 * <p>
	 * This does NOT handle jar features like multi-release jars - for that you'll want to use {@link #loadJarFile(String, Path)} instead.
	 * 
	 * @param name A name for the file system, used for the URL and in logging. Invalid characters will be sanitized.
	 * @param zipFile A path to an existing file.
	 * @return A reference to the file system, which is read-only.
	 * @throws IOException if reading from the zip file failed. */
	public static ExtendedFileSystemRef loadZipFile(String name, Path zipFile) throws IOException {
		return QuiltFileSystemsImpl.loadZipFile(name, zipFile);
	}

	/** Creates a new {@link ExtendedFileSystem} which loads zip file contents directly from the given file, and
	 * {@link ExtendedFileSystem#mount(Path, Path, MountOption...) mounts} every multi-release jar entry.
	 * 
	 * @param name A name for the file system, used for the URL and in logging. Invalid characters will be sanitized.
	 * @param jarFile A path to an existing file.
	 * @return A reference to the file system, which is read-only.
	 * @throws IOException if reading from the jar file failed. */
	public static ExtendedFileSystemRef loadJarFile(String name, Path jarFile) throws IOException {
		return QuiltFileSystemsImpl.loadJarFile(name, jarFile);
	}

	/** Creates a new {@link ExtendedFileSystem} which loads zip file contents directly from the given file. The
	 * returned file system mounts all zip entries using
	 * {@link ExtendedFileSystem#copyOnWrite(Path, Path, java.nio.file.CopyOption...)}, which allows the resulting file
	 * system to be modified safely without affecting the underlying zip file. This is different to
	 * {@link #loadZipFile(String, Path)}, which returns a read-only file system.
	 * <p>
	 * This does NOT handle jar features like multi-release jars - for that you'll want to use
	 * {@link #loadJarFile(String, Path)} instead.
	 * 
	 * @param name A name for the file system, used for the URL and in logging. Invalid characters will be sanitized.
	 * @param zipFile A path to an existing file.
	 * @return A reference to the file system.
	 * @throws IOException if reading from the zip file failed. */
	public static ExtendedFileSystemRef loadZipFileCopyOnWrite(String name, Path zipFile) throws IOException {
		return QuiltFileSystemsImpl.loadZipFileCopyOnWrite(name, zipFile);
	}

	/** Creates a new {@link ExtendedFileSystem} which loads zip file contents directly from the given file, and
	 * {@link ExtendedFileSystem#mount(Path, Path, MountOption...) mounts} every multi-release jar entry. The returned
	 * file system mounts all jar entries using
	 * {@link ExtendedFileSystem#copyOnWrite(Path, Path, java.nio.file.CopyOption...)}, which allows the resulting file
	 * system to be modified safely without affecting the underlying jar file. This is different to
	 * {@link #loadJarFile(String, Path)}, which returns a read-only file system.
	 * 
	 * @param name A name for the file system, used for the URL and in logging. Invalid characters will be sanitized.
	 * @param jarFile A path to an existing file.
	 * @return A reference to the file system.
	 * @throws IOException if reading from the jar file failed. */
	public static ExtendedFileSystemRef loadJarFileCopyOnWrite(String name, Path jarFile) throws IOException {
		return QuiltFileSystemsImpl.loadJarFileCopyOnWrite(name, jarFile);
	}

	public static abstract class ExtendedFileSystemRef {

		/** The file system, exposed as a regular java {@link FileSystem}. This is always the same object as
		 * {@link #quiltFileSystem}. */
		public final FileSystem javaFileSystem;

		/** The file system, exposed as a specialised {@link ExtendedFileSystem}. This is always the same object as
		 * {@link #javaFileSystem}. */
		public final ExtendedFileSystem quiltFileSystem;

		/** The singular root path in the file system. */
		public final Path root;

		public ExtendedFileSystemRef(ExtendedFileSystem fs, Path root) {
			this.javaFileSystem = (FileSystem) fs;
			this.quiltFileSystem = fs;
			this.root = root;
		}

		/** Specialised method, only intended to be used by the creator of the file system. Calling this results in
		 * {@link CachedFileSystem#isPermanentlyReadOnly()} returning true, always, and ensures all subsequent file and
		 * filesystem modifications fail.
		 * <p>
		 * The filesystems returned by {@link QuiltFileSystems#loadZipFile(String, Path)} and
		 * {@link QuiltFileSystems#loadJarFile(String, Path)} will have already called this method. */
		public abstract void markAsReadOnly();
	}
}
