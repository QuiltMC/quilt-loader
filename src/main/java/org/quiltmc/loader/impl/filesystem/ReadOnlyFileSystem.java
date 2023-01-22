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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

import org.quiltmc.loader.api.CachedFileSystem;

/** A {@link FileSystem} which is permanently read-only. */
public interface ReadOnlyFileSystem extends CachedFileSystem {

	public static final String READ_ONLY_ERROR_MESSAGE = "This FileSystem is read-only, so it may not be modified.";

	static IOException throwReadOnly() throws IOException {
		throw new IOException(READ_ONLY_ERROR_MESSAGE);
	}

	@Override
	default boolean isPermanentlyReadOnly() {
		return true;
	}

	@Override
	default Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException {
		if (isDirectory(dir)) {
			return dir;
		} else {
			throw throwReadOnly();
		}
	}

	@Override
	default Path createFile(Path path, FileAttribute<?>... attrs) throws IOException {
		throw throwReadOnly();
	}

	@Override
	default boolean isWritable(Path path) {
		return false;
	}
}
