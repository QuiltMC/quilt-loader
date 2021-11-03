package org.quiltmc.loader.api;

import java.nio.file.Path;

public interface ModContainer {

	ModMetadata metadata();

	/**
	 * Returns the root directory of the mod.
	 * 
	 * <p>It may be the root directory of the mod JAR or the folder of the mod.</p>
	 *
	 * @return the root directory of the mod
	 */
	Path rootPath();

	/**
	 * Gets an NIO reference to a file inside the JAR.
	 * 
	 * <p>The path is not guaranteed to exist!</p>
	 *
	 * @param file The location from root, using {@code /} as a separator.
	 * @return the path to a given file
	 */
	default Path getPath(String file) {
		Path root = rootPath();
		return root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
	}
}
