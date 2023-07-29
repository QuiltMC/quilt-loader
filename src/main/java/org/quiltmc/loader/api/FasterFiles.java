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
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Provides various optimised replacements for {@link Files} which will be faster if the underlying {@link FileSystem}
 * is a {@link FasterFileSystem}. (This is true for all quilt file systems, but won't be true for any others).
 * <p>
 * This only contains methods whose implementation in {@link Files} doesn't just directly call an equivalent method in
 * {@link FileSystemProvider}. */
public final class FasterFiles {

	/** Direct method replacement for {@link Files#createFile(Path, FileAttribute...)}, which may be slightly faster
	 * that it for {@link FasterFileSystem}s.
	 * 
	 * @see Files#createFile(Path, FileAttribute...) */
	public static Path createFile(Path path, FileAttribute<?>... attrs) throws IOException {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).createFile(path, attrs);
		} else {
			return Files.createFile(path, attrs);
		}
	}

	public static Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException {
		if (dir.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) dir.getFileSystem()).createDirectories(dir, attrs);
		} else {
			return Files.createDirectories(dir, attrs);
		}
	}

	public static Path copy(Path source, Path target, CopyOption... options) throws IOException {
		if (target.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) target.getFileSystem()).copy(source, target, options);
		} else {
			return Files.copy(source, target, options);
		}
	}

	public static boolean isSymbolicLink(Path path) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).isSymbolicLink(path);
		} else {
			return Files.isSymbolicLink(path);
		}
	}

	public static boolean isDirectory(Path path, LinkOption... options) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).isDirectory(path, options);
		} else {
			return Files.isDirectory(path, options);
		}
	}

	public static boolean isRegularFile(Path path, LinkOption... options) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).isRegularFile(path, options);
		} else {
			return Files.isRegularFile(path, options);
		}
	}

	/** Direct method replacement for {@link Files#exists(Path, LinkOption...)}, which will be faster than it for
	 * {@link FasterFileSystem}s.
	 * 
	 * @see Files#exists(Path, LinkOption...) */
	public static boolean exists(Path path, LinkOption... options) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).exists(path, options);
		} else {
			return Files.exists(path, options);
		}
	}

	public static boolean notExists(Path path, LinkOption... options) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).notExists(path, options);
		} else {
			return Files.notExists(path, options);
		}
	}

	public static boolean isReadable(Path path) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).isReadable(path);
		} else {
			return Files.isReadable(path);
		}
	}

	public static boolean isWritable(Path path) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).isWritable(path);
		} else {
			return Files.isWritable(path);
		}
	}

	public static boolean isExecutable(Path path) {
		if (path.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) path.getFileSystem()).isExecutable(path);
		} else {
			return Files.isExecutable(path);
		}
	}

	// Walk methods might be optimizable, but they are fairly complicated
	// so for now we don't provide them here

	// Walk-as-stream however might be optimizable directly

	public static Stream<Path> list(Path dir) throws IOException {
		if (dir.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) dir.getFileSystem()).list(dir);
		} else {
			return Files.list(dir);
		}
	}

	/** Very similar to {@link #list(Path)}, but returns a {@link Collection} rather than a {@link Stream}. This is
	 * generally much faster if the filesystem stores all files in a {@link Map}, but may be slower as it will need to
	 * collect all paths from {@link #list(Path)} if this is not the case.
	 * 
	 * @return An unmodifiable {@link Collection}. */
	public static Collection<? extends Path> getChildren(Path dir) throws IOException {
		if (dir.getFileSystem() instanceof FasterFileSystem) {
			return ((FasterFileSystem) dir.getFileSystem()).getChildren(dir);
		} else {
			return getChildrenIndirect(dir);
		}
	}

	static Collection<? extends Path> getChildrenIndirect(Path dir) throws IOException {
		return Collections.unmodifiableCollection(Files.list(dir).collect(Collectors.toCollection(ArrayList::new)));
	}
}
