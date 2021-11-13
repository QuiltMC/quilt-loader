package org.quiltmc.loader.api.plugin;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Set;

public interface QuiltPluginManager {

	/** @return An unmodifiable set of {@link Path}s which quilt and plugins will scan through when looking for mods. */
	Set<Path> getModFolders();

	/** Loads the specified zip file and returns a path to the root of it's contents.
	 * <p>
	 * How the given zip is loaded depends on loaders config settings - in particular the zip could be extracted to a
	 * temporary folder on the same filesystem as the original zip.
	 * <p>
	 * WARNING: if this method allocates a new {@link FileSystem} then that will be closed, <em>unless</em> at least one
	 * of the {@link QuiltLoaderPlugin}s locks it, or if a chosen mod is loaded from it. */
	Path loadZip(Path zip);

	/** @return A joined path which fully describes the given path. If the given path is a sub-folder of one returned by
	 *         {@link #loadZip(Path)} then that zip path will prefix the given path.
	 *         <p>
	 *         For example if "/minecraft/mods/buildcraft-9.0.0.jar" is passed to loadZip, and then
	 *         "/assets/buildcraft/logo.png" is passed here, then this would return
	 *         "/minecraft/mods/buildcraft-9.0.0.jar!/assets/buildcraft/logo.png". */
	String describePath(Path path);

	/** Retrieves the direct parent of the given path. If the given path is one that's been returned by
	 * {@link #loadZip(Path)} then this will return the path of the zip - otherwise this will return
	 * {@link Path#getParent()} */
	Path getParent(Path path);
}
