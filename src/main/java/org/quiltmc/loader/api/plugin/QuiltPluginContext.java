/*
 * Copyright 2022, 2023 QuiltMC
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
import java.nio.file.Path;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Passed to loader plugins as the singular way to access the rest of quilt. */
@ApiStatus.NonExtendable
@QuiltLoaderInternal(QuiltLoaderInternalType.PLUGIN_API)
public interface QuiltPluginContext {

	// ###########
	// # Context #
	// ###########

	/** @return The global plugin manager, which is independent of specific contexts. */
	QuiltPluginManager manager();

	/** @return The plugin that this context is for. */
	QuiltLoaderPlugin plugin();

	/** @return The modID of this plugin. */
	String pluginId();

	/** @return The {@link Path} that the plugin is loaded from. Use this to lookup resources rather than
	 *         {@link Class#getResource(String)}. */
	Path pluginPath();

	/** @return The original {@link ModLoadOption} that this plugin is loaded from. */
	ModLoadOption pluginOption();

	/** @return A specialised Logger for the plugin to use. This will redirect to loaders internal Log, prefixed with
	 *         the {@link #pluginId()} */
	QuiltPluginLogger logger();

	// ##############
	// # Operations #
	// ##############

	/** Adds an additional file to scan for mods, which will go through the same steps as files found in mod folders.
	 * (This is more flexible than loading files manually, since it allows fabric mods to be jar-in-jar'd in quilt mods,
	 * or vice versa. Or any mod type of which a loader plugin can load).
	 * 
	 * @param guiNode The GUI node to display the loaded mod details under
	 * @param direct True if the file is directly loaded rather than being included in another mod (see
	 *            {@link ModLocation#isDirect()})
	 * @deprecated {@link PluginGuiTreeNode} has moved to public API: {@link QuiltTreeNode}. As such please call
	 *             {@link #addFileToScan(Path, QuiltTreeNode, boolean)} instead. */
	@Deprecated
	void addFileToScan(Path file, PluginGuiTreeNode guiNode, boolean direct);

	/** Adds an additional file to scan for mods, which will go through the same steps as files found in mod folders.
	 * (This is more flexible than loading files manually, since it allows fabric mods to be jar-in-jar'd in quilt mods,
	 * or vice versa. Or any mod type of which a loader plugin can load).
	 * 
	 * @param guiNode The GUI node to display the loaded mod details under
	 * @param direct True if the file is directly loaded rather than being included in another mod (see
	 *            {@link ModLocation#isDirect()}) */
	void addFileToScan(Path file, QuiltTreeNode guiNode, boolean direct);

	/** Adds an additional folder to scan for mods, which will be treated in the same way as the regular mods folder.
	 *
	 * @return true if the given folder is a new folder, or false if it has already been added and scanned before. */
	boolean addFolderToScan(Path folder);

	/** "Locks" a zip file that has been opened by {@link QuiltPluginManager#loadZip(Path)} so that it won't be unloaded
	 * if no loaded mod is using it.
	 * 
	 * @param path A path that has been returned by {@link QuiltPluginManager#loadZip(Path)}, <em>not</em> one of it's
	 *            subfolders, or the zip file passed to that method. */
	void lockZip(Path path);

	/** Reports an error, which will be shown in the error gui screen and saved in the crash report file. */
	QuiltDisplayedError reportError(QuiltLoaderText title);

	/** Stops loading as soon as possible. This normally means it will throw an internal exception. This should be used
	 * when you've reported an error via {@link #reportError(QuiltLoaderText)} and don't want to add an extra throwable
	 * stacktrace to the crash report. */
	void haltLoading();

	/** Adds either a folder or jar file to the class path of this plugin. The path is treated as the root of all class
	 * loads.
	 * <p>
	 * Classes loaded from this are only accessible to the plugin - you should not use any classes loaded from this in
	 * your plugins public API!
	 * <p>
	 * If you want to only add a subfolder of the given path then you'll need to copy (or
	 * {@link ExtendedFiles#mount(Path, Path, org.quiltmc.loader.api.MountOption...)}) the files into a new file system.
	 * 
	 * @throws IOException if the given path is a file, and we are unable to open the file as a jar file. */
	void addToPluginClassPath(Path path) throws IOException;

	/** Alternative to {@link #addToPluginClassPath(Path)} which handles only adding some subfolders to the classpath.
	 * 
	 * @param root This must be a folder.
	 * @param subfolders An array of subfolders within the root which will be loaded.
	 * @throws IOException if a file cannot be loaded while adding the paths to the inner filesystem. */
	void addSubfolderToPluginClassPath(Path root, Path... subfolders) throws IOException;

	// ###########
	// # Solving #
	// ###########

	/** Retrieves a context for directly adding {@link LoadOption}s and {@link Rule}s. Note that you shouldn't use this
	 * to add mods. */
	RuleContext ruleContext();

	/** Adds a {@link ModLoadOption} to the {@link RuleContext}, using the specified gui node for all it's location
	 * information.
	 * <p>
	 * This is preferable to calling {@link RuleContext#addOption(LoadOption)} since that adds a "floating" parent node
	 * associated with the plugin itself, not where it might have been loaded from.
	 * 
	 * @param fileNode The {@link PluginGuiTreeNode} which is shown in the 'Files' tab of the error window.
	 * @deprecated {@link PluginGuiTreeNode} has moved to public API: {@link QuiltTreeNode}. As such please call
	 *             {@link #addModLoadOption(ModLoadOption, QuiltTreeNode)} instead. */
	@Deprecated
	void addModLoadOption(ModLoadOption mod, PluginGuiTreeNode fileNode);

	/** Adds a {@link ModLoadOption} to the {@link RuleContext}, using the specified gui node for all it's location
	 * information.
	 * <p>
	 * This is preferable to calling {@link RuleContext#addOption(LoadOption)} since that adds a "floating" parent node
	 * associated with the plugin itself, not where it might have been loaded from.
	 * 
	 * @param fileNode The {@link PluginGuiTreeNode} which is shown in the 'Files' tab of the error window. */
	void addModLoadOption(ModLoadOption mod, QuiltTreeNode fileNode);

	/** Adds a tentative option which can be resolved later by {@link TentativeLoadOption#resolve()}, if it is selected.
	 *
	 * @param option */
	<T extends LoadOption & TentativeLoadOption> void addTentativeOption(T option);

	/** Only callable during {@link QuiltLoaderPlugin#handleError(java.util.Collection)} to identify the given rule as
	 * one which can be removed for the purposes of error message generation. */
	void blameRule(Rule rule);
}
