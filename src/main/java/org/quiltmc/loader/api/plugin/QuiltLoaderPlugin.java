package org.quiltmc.loader.api.plugin;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;

/** The base type for all plugins.
 * <p>
 * Plugins are applied the following steps:
 * <ol>
 * <li>{@link #load(QuiltPluginContext)} is called to set the {@link QuiltPluginContext}.</li>
 * <li>{@link #addModFolders(Set)} is called to add folders that will be scanned by quilt and other plugins for mods.
 * </li>
 * <li>Quilt Loader will scan all files in those folders, and follow these steps:
 * <ol>
 * <li>If it ends with ".disabled", or is a system or hidden file, then it is skipped.</li>
 * <li>If it is a zip or jar file (or can be opened by {@link FileSystems#newFileSystem(Path, ClassLoader)}) then it
 * will be opened, and checked for a "quilt.mod.json" file. If one is found, then it is loaded as a quilt mod (and
 * possibly as a new plugin - which will be loaded instantly, rather than waiting until the next cycle).</li>
 * <li>If "quilt.mod.json" couldn't be found then the zip root will be passed to {@link #scanZip(Path)}</li>
 * <li>Otherwise it will be passed to {@link #scanUnknownFile(Path)}</li>
 * </ol>
 * </li>
 * <li>{@link #beforeSolve()} is called.</li>
 * <li>Loader will begin solving the rules added</li>
 * </ol>
 * In particular, plugins must never call {@link QuiltLoader} directly - that's designed solely for mods to use after
 * mod loading is complete. */
public interface QuiltLoaderPlugin {

	/** Called at the very start to pass the {@link QuiltPluginContext} that this plugin should use for every call into
	 * quilt.
	 * <p>
	 * Plugins aren't expected to do anything at that modifies quilt at this stage, but they could load configuration
	 * data.
	 * 
	 * @param context The context, to use later. You should store this in a field if you need it.
	 * @param previousData The data written to {@link #unload(Map)} by a different version of this plugin, or an empty
	 *            map if the plugin hasn't been reloaded. */
	void load(QuiltPluginContext context, Map<String, LoaderValue> previousData);

	/** Prepares to unload this plugin, in preparation for loading a different version of the same plugin. If you wish
	 * to keep some data from previous runs into the next run, you should put them into the given map. */
	void unload(Map<String, LoaderValue> data);

	/** Adds mod folders which will be scanned by quilt and plugins for mods. Only {@link Path}s which are provided by
	 * {@link FileSystems#getDefault()}, and {@link Files#isDirectory(Path, java.nio.file.LinkOption...) is a directory}
	 * are permitted. (In other words this only accepts folders which are natively accessible, and not folders inside of
	 * jar files).
	 * 
	 * @param folders The {@link Set} of {@link Path}s that have been added before. This starts with the default mod
	 *            folder. Only the {@link Set#add(Object)} and {@link Set#addAll(java.util.Collection)} modification
	 *            methods are supported. */
	default void addModFolders(Set<Path> folders) {}

	/** Called once per archival file found in any of the folders added by {@link #addModFolders(Set)} or
	 * {@link #onModFolderAdded(Path, Set)}. This is only called for zips that aren't identified as quilt mods, and
	 * aren't system files.
	 * <p>
	 * You can retrieve the file name of the original zip by using {@link QuiltPluginManager#getParent(Path)}.
	 * 
	 * @param root The root of the zip file.
	 * @return A {@link ModLoadOption} if this plugin could load the given zip as a mod, or null if it couldn't. */
	@Nullable
	default ModLoadOption scanZip(Path root) {
		return null;
	}

	/** Called once per file encountered which loader can't open (I.E. those which are not passed to
	 * {@link #scanZip(Path)}). However system files are not passed here.
	 * 
	 * @param file
	 * @return A {@link ModLoadOption} if this plugin could load the given file as a mod, or null if it couldn't. */
	@Nullable
	default ModLoadOption scanUnknownFile(Path file) {
		return null;
	}

	/** Called once per cycle just before the set of {@link Rule}s and {@link LoadOption}s are solved. */
	default void beforeSolve() {}

	/** Called after solving has finished and successfully found the final set of {@link LoadOption}s and mods. None of
	 * the "present" {@link LoadOption}s will be {@link TentativeLoadOption}. This will only be called once, and marks
	 * the end of the final cycle.
	 * <p>
	 * Like resolving, you can submit tasks and queue gui requests during this, which will be completed before the game
	 * can actually be launched. */
	default void finish(ModSolveResult result) {}

	// #######
	// Solving
	// #######

	/** Resolves a single {@link TentativeLoadOption} that was added via
	 * {@link QuiltPluginContext#addTentativeOption(LoadOption, Object)}. This is only called if the option was selected
	 * by the solver - unselected options are not resolved.
	 * <p>
	 * Long running operations should use {@link QuiltPluginContext#submit(java.util.concurrent.Callable)} to perform
	 * those tasks in the future, and possibly on different threads. Operations that require use of the gui should use
	 * {@link QuiltPluginContext#addGuiRequest()} instead, and call submit after that has been accepted or denied.
	 * 
	 * @return A {@link Future} containing (or will contain) the {@link LoadOption} that will replace the
	 *         {@link TentativeLoadOption} next cycle. */
	default Future<? extends LoadOption> resolve(TentativeLoadOption from) {
		throw new IllegalStateException(
			getClass() + " has added a TentativeLoadOption (" + from.getClass() + ") but can't resolve it!"
		);
	}

	/** @return True if this plugin did something which will solve / change the error in future, and so loader won't ask
	 *         any other plugins to solve this. You are expected to call {@link QuiltPluginContext#blameRule(Rule)} if
	 *         you can't actually fix the issue, but can identify a rule to be removed.
	 *         <p>
	 *         If no plugin can identify a rule to be removed then loader will remove a random rule in order to move on
	 *         to the next error. If this returns true then no rules will be removed, and instead loader will assume
	 *         that the error has been handled in some other way. (and it will promptly crash if you haven't) */
	default boolean handleError(List<Rule> ruleChain) {
		return false;
	}

	/** Called whenever a new LoadOption is added, for plugins to add Rules based on this. (For example the default
	 * plugin creates rules based on the dependencies and breaks sections of the quilt.mod.json if this option is a
	 * {@link MainModLoadOption}).
	 * <p>
	 * Most plugins are not expected to implement this. */
	default void onLoadOptionAdded(LoadOption option) {}

	/**
	 * <p>
	 * Most plugins are not expected to implement this. */
	default void onLoadOptionRemoved(LoadOption option) {}
}
