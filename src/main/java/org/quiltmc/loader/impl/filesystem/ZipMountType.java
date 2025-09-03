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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.util.zip.ZipEntry;

import org.quiltmc.loader.impl.filesystem.QuiltZipFile.CopyOnWriteZipFile;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.CustomZipInputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public enum ZipMountType {
	READ_ONLY {
		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry, CustomZipInputStream zip)
			throws IOException {
			return new QuiltZipFile(path, source, entry, zip);
		}

		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
			int uncompressedSize, boolean isCompressed) {
			return new QuiltZipFile(path, source, offset, compressedSize, uncompressedSize, isCompressed);
		}
	},
	COPY_ON_WRITE {
		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry, CustomZipInputStream zip)
			throws IOException {
			return new CopyOnWriteZipFile(path, source, entry, zip);
		}

		@Override
		QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
			int uncompressedSize, boolean isCompressed) {

			return new CopyOnWriteZipFile(path, source, offset, compressedSize, uncompressedSize, isCompressed);
		}
	};

	abstract QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry,
		CustomZipInputStream zip) throws IOException;

	abstract QuiltZipFile create(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
		int uncompressedSize, boolean isCompressed);
}
