/*
 * Copyright 2022 QuiltMC
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Essentially a {@link QuiltJoinedFileSystem} but which caches all paths in advance. Not exposed as a filesystem since
 * this is a bit more dynamic than that. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class QuiltClassPath {

	private final List<Path> roots = new ArrayList<>();
	private final Map<String, Path> files = new HashMap<>();

	public void addRoot(Path root) {
		if (root instanceof QuiltJoinedPath) {
			QuiltJoinedFileSystem fs = ((QuiltJoinedPath) root).fs;

			for (Path from : fs.from) {
				addRoot(from);
			}

		} else if (root instanceof QuiltMemoryPath) {
			QuiltMemoryFileSystem fs = ((QuiltMemoryPath) root).fs;

			if (fs instanceof QuiltMemoryFileSystem.ReadWrite) {
				roots.add(root);
			} else {
				for (Path key : fs.files.keySet()) {
					files.putIfAbsent(key.toString(), key);
				}
			}

		} else {
			roots.add(root);
		}
	}

	public Path findResource(String path) {
		String absolutePath = path;
		if (!path.startsWith("/")) {
			absolutePath = "/" + path;
		}
		Path quick = files.get(absolutePath);
		if (quick != null) {
			return quick;
		}

		for (Path root : roots) {
			Path ext = root.resolve(path);
			if (Files.exists(ext)) {
				return ext;
			}
		}

		return null;
	}
}
