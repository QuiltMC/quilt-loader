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

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
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

	// ##############
	// # Operations #
	// ##############

	/** Adds an additional file to scan for mods, which will go through the same steps as files found in mod folders.
	 * (This is more flexible than loading files manually, since it allows fabric mods to be jar-in-jar'd in quilt mods,
	 * or vice versa. Or any mod type of which a loader plugin can load).
	 * 
	 * @param guiNode The GUI node to display the loaded mod details under
	 * @param direct True if the file is directly loaded rather than being included in another mod (see {@link ModLocation#isDirect()}) */
	void addFileToScan(Path file, PluginGuiTreeNode guiNode, boolean direct);

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

	// ##############
	// # Scheduling #
	// ##############

	/** Submits a task to be completed after plugin resolution, but before the current cycle ends. The task may be
	 * executed on a different thread, depending on loaders config options.
	 * <p>
	 * This should only be called by {@link QuiltLoaderPlugin#resolve(QuiltPluginContext, Object)},
	 * {@link QuiltLoaderPlugin#finish(org.quiltmc.loader.api.plugin.solver.ModSolveResult)}, or by any tasks that are
	 * passed to this function during their execution.
	 * 
	 * @return A {@link QuiltPluginTask} which will contain the result of the task, or the failure state if something
	 *         went wrong. */
	<V> QuiltPluginTask<V> submit(Callable<V> task);

	/** Submits a task to be completed after plugin resolution, and additionally after the given tasks have completed,
	 * but before the current cycle ends. The task may be executed on a different thread, depending on loaders config
	 * options. Note that the task will still be executed, <em>even if the dependencies failed.</em> This is to allow
	 * the task to handle errors directly.
	 * 
	 * @param deps The tasks that must complete before the given task can be executed.
	 * @return A {@link QuiltPluginTask} which will contain the result of the task, or the failure state if something
	 *         went wrong. */
	<V> QuiltPluginTask<V> submitAfter(Callable<V> task, QuiltPluginTask<?>... deps);

	// #######
	// # Gui #
	// #######

	/** Used to ask the real user of something. Normally this will append something to the existing gui rather than
	 * opening a new gui each time this is called.
	 * <p>
	 * TODO: Create all gui stuff! for now this just throws an {@link AbstractMethodError} */
	default <V> QuiltPluginTask<V> addGuiRequest() {
		throw new AbstractMethodError("// TODO: Add gui support!");
	}

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
	 * @param fileNode The {@link PluginGuiTreeNode} which is shown in the 'Files' tab of the error window.*/
	void addModLoadOption(ModLoadOption mod, PluginGuiTreeNode fileNode);

	/** Adds a tentative option which can be resolved later by
	 * {@link QuiltLoaderPlugin#resolve(QuiltPluginContext, TentativeLoadOption)}, if it is selected.
	 * 
	 * @param option */
	<T extends LoadOption & TentativeLoadOption> void addTentativeOption(T option);

	/** Only callable during {@link QuiltLoaderPlugin#handleError(java.util.List)} to identify the given rule as one
	 * which can be removed for the purposes of error message generation. */
	void blameRule(Rule rule);
}
