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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltUnifiedFileSystemProvider extends QuiltMapFileSystemProvider<QuiltUnifiedFileSystem, QuiltUnifiedPath> {
	public QuiltUnifiedFileSystemProvider() {}

	public static final String SCHEME = "quilt.ufs";

	static final String READ_ONLY_EXCEPTION = "This FileSystem is read-only";
	static final QuiltFSP<QuiltUnifiedFileSystem> PROVIDER = new QuiltFSP<>(SCHEME);

	public static QuiltUnifiedFileSystemProvider instance() {
		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			if (provider instanceof QuiltUnifiedFileSystemProvider) {
				return (QuiltUnifiedFileSystemProvider) provider;
			}
		}
		throw new IllegalStateException("Unable to load QuiltUnifiedFileSystemProvider via services!");
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	protected QuiltFSP<QuiltUnifiedFileSystem> quiltFSP() {
		return PROVIDER;
	}

	@Override
	protected Class<QuiltUnifiedFileSystem> fileSystemClass() {
		return QuiltUnifiedFileSystem.class;
	}

	@Override
	protected Class<QuiltUnifiedPath> pathClass() {
		return QuiltUnifiedPath.class;
	}

	@Override
	public QuiltUnifiedPath getPath(URI uri) {
		return PROVIDER.getFileSystem(uri).root.resolve(uri.getPath());
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		throw new IOException("Only direct creation is supported");
	}
}
