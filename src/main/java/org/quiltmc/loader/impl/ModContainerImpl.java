/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl;

import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModContainerImpl implements org.quiltmc.loader.api.ModContainer {
	private final InternalModMetadata meta;
	private final FabricLoaderModMetadata fabricMeta;
	private final Path originPath;
	private volatile Path root;

	public ModContainerImpl(InternalModMetadata meta, Path originPath) {
		this.meta = meta;
		this.fabricMeta = meta.asFabricModMetadata();
		this.originPath = originPath;
	}

	@Override
	public ModMetadata metadata() {
		return meta;
	}

	public Path getOriginPath() {
		return originPath;
	}

	@Override
	public Path rootPath() {
		Path ret = root;

		if (ret == null || !ret.getFileSystem().isOpen()) {
			if (ret != null && !warned) {
				if (!QuiltLoaderImpl.INSTANCE.isDevelopmentEnvironment()) warned = true;
				Log.warn(LogCategory.GENERAL, "FileSystem for %s has been closed unexpectedly, existing root path references may break!", this);
			}

			root = ret = obtainRootPath(); // obtainRootPath is thread safe, but we need to avoid plain or repeated reads to root
		}

		return ret;
	}

	private boolean warned = false;

	private Path obtainRootPath() {
		try {
			if (Files.isDirectory(originPath)) {
				return originPath;
			} else /* JAR */ {
				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(originPath, false);

				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + originPath + " for NIO reading!");
				}

				return delegate.get().getRootDirectories().iterator().next();

				// We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to find root directory for mod '" + fabricMeta.getId() + "'!", e);
		}
	}

	@Deprecated
	public FabricLoaderModMetadata getInfo() {
		return fabricMeta;
	}

	public InternalModMetadata getInternalMeta() {
		return meta;
	}

	@Override
	public String toString() {
		return String.format("%s %s", fabricMeta.getId(), fabricMeta.getVersion());
	}
}