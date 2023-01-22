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

package org.quiltmc.loader.api;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Collection;
import java.util.stream.Stream;

/** A type of {@link FileSystem} which provides various replacements for {@link Files} methods which may execute much
 * faster than their {@link Files} counterparts. Generally you should use {@link FasterFiles} instead of this. */
public interface FasterFileSystem {

	/** Either the same speed as, or slightly faster than {@link Files#createFile(Path, FileAttribute...)}. Otherwise
	 * this has the same semantics as {@link Files#createFile(Path, FileAttribute...)}.
	 * 
	 * @see FasterFiles#createFile(Path, FileAttribute...) */
	default Path createFile(Path path, FileAttribute<?>... attrs) throws IOException {
		return Files.createFile(path, attrs);
	}

	default Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException {
		return Files.createDirectories(dir, attrs);
	}

	default boolean isSymbolicLink(Path path) {
		return Files.isSymbolicLink(path);
	}

	default boolean isDirectory(Path path, LinkOption... options) {
		return Files.isDirectory(path, options);
	}

	default boolean isRegularFile(Path path, LinkOption[] options) {
		return Files.isRegularFile(path, options);
	}

	/** Either the same speed as, or faster than {@link Files#exists(Path, LinkOption...)}. Otherwise this has the same
	 * semantics as {@link Files#exists(Path, LinkOption...)}.
	 * 
	 * @see FasterFiles#exists(Path, LinkOption...) */
	default boolean exists(Path path, LinkOption... options) {
		return Files.exists(path, options);
	}

	/** Either the same speed as, or faster than {@link Files#notExists(Path, LinkOption...)}. Otherwise this has the
	 * same semantics as {@link Files#notExists(Path, LinkOption...)}.
	 * 
	 * @see FasterFiles#notExists(Path, LinkOption...) */
	default boolean notExists(Path path, LinkOption... options) {
		return Files.notExists(path, options);
	}

	default boolean isReadable(Path path) {
		return Files.isReadable(path);
	}

	default boolean isWritable(Path path) {
		return Files.isWritable(path);
	}

	default boolean isExecutable(Path path) {
		return Files.isExecutable(path);
	}

	default Stream<Path> list(Path dir) throws IOException {
		return Files.list(dir);
	}

	default Collection<? extends Path> getChildren(Path dir) throws IOException {
		return FasterFiles.getChildrenIndirect(dir);
	}
}
