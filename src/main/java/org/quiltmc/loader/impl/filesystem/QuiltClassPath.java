package org.quiltmc.loader.impl.filesystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Essentially a {@link QuiltJoinedFileSystem} but which caches all paths in advance. Not exposed as a filesystem since
 * this is a bit more dynamic than that. */
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
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		Path quick = files.get(path);
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
