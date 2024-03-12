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
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class QuiltMemoryFileSystemProvider extends QuiltMapFileSystemProvider<QuiltMemoryFileSystem, QuiltMemoryPath> {
	public QuiltMemoryFileSystemProvider() {
		if (instance == null) {
			instance = this;
		}
	}

	public static final String SCHEME = "quilt.mfs";

	private static QuiltMemoryFileSystemProvider instance;

	static final String READ_ONLY_EXCEPTION = "This FileSystem is read-only";
	static final QuiltFSP<QuiltMemoryFileSystem> PROVIDER = new QuiltFSP<>(SCHEME);

	public static QuiltMemoryFileSystemProvider instance() {
		QuiltMemoryFileSystemProvider found = findInstance();
		if (found != null) {
			return found;
		}
		throw new IllegalStateException("Unable to load QuiltMemoryFileSystemProvider via services!");
	}

	public static QuiltMemoryFileSystemProvider findInstance() {
		if (instance != null) {
			return instance;
		}

		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if (provider instanceof QuiltMemoryFileSystemProvider) {
				return (QuiltMemoryFileSystemProvider) provider;
			}
		}

		return instance;
	}

	@Override
	protected QuiltFSP<QuiltMemoryFileSystem> quiltFSP() {
		return PROVIDER;
	}

	@Override
	protected Class<QuiltMemoryFileSystem> fileSystemClass() {
		return QuiltMemoryFileSystem.class;
	}

	@Override
	protected Class<QuiltMemoryPath> pathClass() {
		return QuiltMemoryPath.class;
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		throw new IOException("Only direct creation is supported");
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return ((QuiltMemoryPath) path).fs.getFileStores().iterator().next();
	}
}
