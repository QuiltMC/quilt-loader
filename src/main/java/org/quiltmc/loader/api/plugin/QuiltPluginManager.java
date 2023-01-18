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

package org.quiltmc.loader.api.plugin;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;

@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface QuiltPluginManager {

	// #######
	// Loading
	// #######

	/** Returns a task which will load the specified zip file and returns a path to the root of it's contents.
	 * <p>
	 * How the given zip is loaded depends on loaders config settings - in particular the zip could be extracted to a
	 * temporary folder on the same filesystem as the original zip.
	 * <p>
	 * WARNING: if this method allocates a new {@link FileSystem} then that will be closed, <em>unless</em> at least one
	 * of the {@link QuiltLoaderPlugin}s {@link QuiltPluginContext#lockZip(Path) locks} it, or if a chosen mod is loaded
	 * from it.
	 * <p>
	 * The returned task may throw the following exceptions:
	 * <ul>
	 * <li>{@link IOException} if something went wrong while loading the file.</li>
	 * <li>{@link NonZipException} if {@link FileSystems#newFileSystem(Path, ClassLoader)} throws a
	 * {@link ProviderNotFoundException}.</li>
	 * </ul>
	 */
	QuiltPluginTask<Path> loadZip(Path zip);

	/** Creates a new in-memory read-write file system. This can be used for mods that aren't loaded from zips.
	 *
	 * @return The root {@link Path} of the newly allocated {@link FileSystem} */
	Path createMemoryFileSystem(String name);

	/** Creates a new in-memory file system, then copies the contents of the given folder into it.
	 *
	 * @return The root {@link Path} of the newly allocated {@link FileSystem} */
	default Path copyToReadOnlyFileSystem(String name, Path folderRoot) throws IOException {
		return copyToReadOnlyFileSystem(name, folderRoot, false);
	}

	/** Creates a new in-memory file system, then copies the contents of the given folder into it.
	 *
	 * @return The root {@link Path} of the newly allocated {@link FileSystem} */
	Path copyToReadOnlyFileSystem(String name, Path folderRoot, boolean compress) throws IOException;

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

	/** Retrieves the file in the default {@link FileSystem} (that the user can view directly in a file browser) that
	 * contains the given path.
	 * 
	 * @return Either a Path with a {@link FileSystem} equal to {@link FileSystems#getDefault()}, or empty.*/
	Optional<Path> getRealContainingFile(Path file);

	// #################
	// Joined Paths
	// #################

	/** @return True if the given {@link Path} points to multiple root paths. */
	boolean isJoinedPath(Path path);

    /** @return All paths that the given path actually refers to, or null if {@link #isJoinedPath(Path)} returns
     *         false. */
	Collection<Path> getJoinedPaths(Path path);

	/** Creates a new {@link FileSystem} which joins the given list of paths, and returns the path at the root of that
	 * filesystem.
	 * <p>
	 * Filesystems created this way will return true from {@link #isJoinedPath(Path)}, and a copy of the given list of
	 * paths will be returned by {@link #getJoinedPaths(Path)} when the returned path is passed to it.
	 * 
	 * @param name A unique name for the filesystem, used by the URL handling code. In the case of duplicate names quilt
	 *            will pick a different, but related, name to use instead to keep all filesystems unique. */
	Path joinPaths(String name, List<Path> paths);

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

	/** @param mod The path to the mod. This should always be one that was passed to
	 *            {@link QuiltLoaderPlugin#scanUnknownFile(Path, boolean, PluginGuiTreeNode)} or the {@link #getParent(Path)} of
	 *            a path passed to {@link QuiltLoaderPlugin#scanZip(Path, boolean, PluginGuiTreeNode)}. (Paths in
	 *            {@link #getModPaths()} always meet this requirement)
	 * @return The mod id of the loader plugin that added a mod directly from the given path. */
	@Nullable
	String getModProvider(Path mod);

	/** @param mod The path to the mod. This should always be one that was passed to
	 *            {@link QuiltLoaderPlugin#scanUnknownFile(Path, boolean, PluginGuiTreeNode)} or the {@link #getParent(Path)} of
	 *            a path passed to {@link QuiltLoaderPlugin#scanZip(Path, boolean, PluginGuiTreeNode)}. (Paths in
	 *            {@link #getModPaths()} always meet this requirement)
	 * @return The mod load option that is loaded from the given path. */
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

	// #########
	// # State #
	// #########

	// TODO: Replace this with a game-agnostic quilt version!
	@Deprecated
	EnvType getEnvironment();

	/** @return The current folder which will become {@link QuiltLoader#getGameDir()}. Modifying this via [todo] changes
	 *         this value after the VM has been restarted. */
	Path getGameDirectory();

	/** @return The current folder which will become {@link QuiltLoader#getConfigDir()}. */
	Path getConfigDirectory();

	// #######
	// # Gui #
	// #######

	PluginGuiTreeNode getGuiNode(ModLoadOption mod);

	PluginGuiTreeNode getRootGuiNode();

	PluginGuiManager getGuiManager();
}
