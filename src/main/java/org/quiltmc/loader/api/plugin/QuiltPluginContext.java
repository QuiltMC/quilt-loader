package org.quiltmc.loader.api.plugin;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleContext;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;

/** Passed to loader plugins as the singular way to access the rest of quilt. */
@ApiStatus.NonExtendable
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
	 * @param guiNode TODO*/
	void addFileToScan(Path file, PluginGuiTreeNode guiNode);

	/** "Locks" a zip file that has been opened by {@link QuiltPluginManager#loadZip(Path)} so that it won't be unloaded
	 * if no loaded mod is using it.
	 * 
	 * @param path A path that has been returned by {@link QuiltPluginManager#loadZip(Path)}, <em>not</em> one of it's
	 *            subfolders, or the zip file passed to that method. */
	void lockZip(Path path);

	// ##############
	// # Scheduling #
	// ##############

	/** Submits a task to be completed after plugin resolution, but before the current cycle ends. The tasks may be
	 * executed on a different thread, depending on loaders config options.
	 * <p>
	 * This should only be called by {@link QuiltLoaderPlugin#resolve(QuiltPluginContext, Object)},
	 * {@link QuiltLoaderPlugin#finish(org.quiltmc.loader.api.plugin.solver.ModSolveResult)}, or by any tasks that are
	 * passed to this function during their execution.
	 * 
	 * @return A {@link Future} which will contain the result of the task, or the failure state if something went
	 *         wrong. */
	<V> Future<V> submit(Callable<V> task);

	// #######
	// # Gui #
	// #######

	/** Used to ask the real user of something. Normally this will append something to the existing gui rather than
	 * opening a new gui each time this is called.
	 * <p>
	 * TODO: Create all gui stuff! for now this just throws an {@link AbstractMethodError} */
	default <V> Future<V> addGuiRequest() {
		throw new AbstractMethodError("// TODO: Add gui support!");
	}

	// ###########
	// # Solving #
	// ###########

	/** Retrieves a context for directly adding {@link LoadOption}s and {@link Rule}s. Note that you shouldn't use this
	 * to add mods. */
	RuleContext ruleContext();

	/** Adds a tentative option which can be resolved later by
	 * {@link QuiltLoaderPlugin#resolve(QuiltPluginContext, TentativeLoadOption)}, if it is selected.
	 * 
	 * @param option */
	<T extends LoadOption & TentativeLoadOption> void addTentativeOption(T option);

	/** Only callable during {@link QuiltLoaderPlugin#handleError(java.util.List)} to identify the given rule as one
	 * which can be removed for the purposes of error message generation. */
	void blameRule(Rule rule);
}
