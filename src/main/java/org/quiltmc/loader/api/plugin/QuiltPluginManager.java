package org.quiltmc.loader.api.plugin;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

public interface QuiltPluginManager {

	// #######
	// Loading
	// #######

	/** Loads the specified zip file and returns a path to the root of it's contents.
	 * <p>
	 * How the given zip is loaded depends on loaders config settings - in particular the zip could be extracted to a
	 * temporary folder on the same filesystem as the original zip.
	 * <p>
	 * WARNING: if this method allocates a new {@link FileSystem} then that will be closed, <em>unless</em> at least one
	 * of the {@link QuiltLoaderPlugin}s locks it, or if a chosen mod is loaded from it. */
	Path loadZip(Path zip);

	// #################
	// Identifying Paths
	// #################

	/** @return A joined path which fully describes the given path. If the given path is a sub-folder of one returned by
	 *         {@link #loadZip(Path)} then that zip path will prefix the given path.
	 *         <p>
	 *         For example if "/minecraft/mods/buildcraft-9.0.0.jar" is passed to loadZip, and then
	 *         "/assets/buildcraft/logo.png" is passed here, then this would return something similar to
	 *         "/minecraft/mods/buildcraft-9.0.0.jar!/assets/buildcraft/logo.png". */
	String describePath(Path path);

	/** Retrieves the direct parent of the given path. If the given path is one that's been returned by
	 * {@link #loadZip(Path)} then this will return the path of the zip - otherwise this will return
	 * {@link Path#getParent()} */
	Path getParent(Path path);

	// ###################
	// Reading Mod Folders
	// ###################

	/** @return An unmodifiable set of {@link Path}s which quilt and plugins will scan through when looking for mods. */
	Set<Path> getModFolders();

	/** @return The mod id of the loader plugin that added that mod folder, or null if the given path isn't in
	 *         {@link #getModFolders()}. */
	@Nullable
	String getFolderProvider(Path modFolder);

	// ############
	// Reading Mods
	// ############

	// by Path

	/** @return An unmodifiable set of all the {@link Path}s that have been recognised as a mod. Use
	 *         {@link #getModProvider(Path)} or {@link #getModLoadOption(Path)} for more details about the mod. */
	Set<Path> getModPaths();

	/** @return The mod id of the loader plugin that added a mod directly from the given path. The path should always be
	 *         one that was passed to {@link QuiltLoaderPlugin#scanUnknownFile(Path)} or the {@link #getParent(Path)} of
	 *         a path passed to {@link QuiltLoaderPlugin#scanZip(Path)}. */
	@Nullable
	String getModProvider(Path mod);

	/** @return The mod load option that is loaded from the given path. The path should always be one that was passed to
	 *         {@link QuiltLoaderPlugin#scanUnknownFile(Path)} or the {@link #getParent(Path)} of a path passed to
	 *         {@link QuiltLoaderPlugin#scanZip(Path)}. */
	@Nullable
	ModLoadOption getModLoadOption(Path mod);

	// by Mod ID

	/** @return An unmodifiable set of all mod ids that have been added as {@link ModLoadOption}s so far. */
	Set<String> getModIds();

	/** @param modId A modid which is in {@link #getModIds()}
	 * @return An unmodifiable map of all {@link ModLoadOption}s of which there are only one candidate option per
	 *         version. */
	Map<Version, ModLoadOption> getVersionedMods(String modId);

	/** @return An unmodifiable collection of every {@link ModLoadOption} which has duplicate {@link Version}s. */
	Collection<ModLoadOption> getExtraMods(String modId);

	/** @return An unmodifiable collection of every {@link ModLoadOption} present for the given id. This includes the
	 *         {@link Map#values()} from {@link #getVersionedMods(String)} and the collection from
	 *         {@link #getExtraMods(String)}. */
	Collection<ModLoadOption> getAllMods(String modId);
}
