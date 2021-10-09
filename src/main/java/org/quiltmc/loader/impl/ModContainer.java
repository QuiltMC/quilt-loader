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

import net.fabricmc.loader.api.metadata.ModMetadata;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModContainer implements net.fabricmc.loader.api.ModContainer {
	private final InternalModMetadata meta;
	private final LoaderModMetadata fabricMeta;
	private final URL originUrl;
	private volatile Path root;

	public ModContainer(InternalModMetadata meta, URL originUrl) {
		this.meta = meta;
		this.fabricMeta = meta.asFabricModMetadata();
		this.originUrl = originUrl;
	}

	@Override
	public ModMetadata getMetadata() {
		return fabricMeta;
	}

	@Override
	public Path getRootPath() {
		Path ret = root;

		if (ret == null) {
			root = ret = obtainRootPath(); // obtainRootPath is thread safe, but we need to avoid plain or repeated reads to root
		}

		return ret;
	}

	private Path obtainRootPath() {
		try {
			Path holder = UrlUtil.asPath(originUrl).toAbsolutePath();

			if (Files.isDirectory(holder)) {
				return holder;
			} else /* JAR */ {
				FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(holder, false);
				if (delegate.get() == null) {
					throw new RuntimeException("Could not open JAR file " + holder.getFileName() + " for NIO reading!");
				}

				return delegate.get().getRootDirectories().iterator().next();

				// We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
			}
		} catch (IOException | UrlConversionException e) {
			throw new RuntimeException("Failed to find root directory for mod '" + meta.id() + "'!", e);
		}
	}

	@Deprecated
	public LoaderModMetadata getInfo() {
		return fabricMeta;
	}

	public InternalModMetadata getInternalMeta() {
		return meta;
	}

	public URL getOriginUrl() {
		return originUrl;
	}
}
