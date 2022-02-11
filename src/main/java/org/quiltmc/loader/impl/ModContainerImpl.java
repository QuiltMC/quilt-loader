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
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModContainerImpl implements org.quiltmc.loader.api.ModContainer {
	private final InternalModMetadata meta;
	private final FabricLoaderModMetadata fabricMeta;
	private final List<Path> codeSourcePaths;
	private volatile List<Path> roots;

	public ModContainerImpl(ModCandidate candidate) {
		this.meta = candidate.getMetadata();
		this.fabricMeta = meta.asFabricModMetadata();
		this.codeSourcePaths = candidate.getOriginPaths();
	}

	@Override
	public ModMetadata metadata() {
		return meta;
	}


	private static boolean warnedMultiPath = false;
	private static boolean warnedClose = false;
	@Override
	public List<Path> rootPaths() {
		List<Path> ret = roots;

		if (ret == null || !checkFsOpen(ret)) {
			roots = ret = obtainRootPaths(); // obtainRootPaths is thread safe, but we need to avoid plain or repeated reads to root
		}

		return ret;
	}

	public List<Path> codeSourcePaths() {
		return codeSourcePaths;
	}
	private boolean checkFsOpen(List<Path> paths) {
		for (Path path : paths) {
			if (path.getFileSystem().isOpen()) continue;

			if (!warnedClose) {
				if (!QuiltLoaderImpl.INSTANCE.isDevelopmentEnvironment()) warnedClose = true;
				Log.warn(LogCategory.GENERAL, "FileSystem for %s has been closed unexpectedly, existing root path references may break!", this);
			}

			return false;
		}

		return true;
	}

	private List<Path> obtainRootPaths() {
		boolean allDirs = true;

		for (Path path : codeSourcePaths) {
			if (!Files.isDirectory(path)) {
				allDirs = false;
				break;
			}
		}

		if (allDirs) return codeSourcePaths;

		try {
			if (codeSourcePaths.size() == 1) {
				return Collections.singletonList(obtainRootPath(codeSourcePaths.get(0)));
			} else {
				List<Path> ret = new ArrayList<>(codeSourcePaths.size());

				for (Path path : codeSourcePaths) {
					ret.add(obtainRootPath(path));
				}

				return Collections.unmodifiableList(ret);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to obtain root directory for mod '" + fabricMeta.getId() + "'!", e);
		}
	}

	private static Path obtainRootPath(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			return path;
		} else /* JAR */ {
			FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(path, false);
			FileSystem fs = delegate.get();

			if (fs == null) {
				throw new RuntimeException("Could not open JAR file " + path + " for NIO reading!");
			}

			return fs.getRootDirectories().iterator().next();

			// We never close here. It's fine. getJarFileSystem() will handle it gracefully, and so should mods
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