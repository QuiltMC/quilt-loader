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

package org.quiltmc.loader.impl.plugin;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltTreeNode;
import org.quiltmc.loader.api.gui.QuiltTreeNode.SortOrder;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.NonZipException;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.QuiltPluginTask;
import org.quiltmc.loader.api.plugin.gui.PluginGuiManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
import org.quiltmc.loader.api.plugin.solver.AliasedLoadOption;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult.SpecificLoadOptionResult;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderConfig;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ArgumentModCandidateFinder;
import org.quiltmc.loader.impl.discovery.ClasspathModCandidateFinder;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.filesystem.QuiltBaseFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedPath;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.filesystem.ZeroByteFileException;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.gui.QuiltFork;
import org.quiltmc.loader.impl.gui.QuiltJsonGuiMessage;
import org.quiltmc.loader.impl.gui.QuiltStatusNode;
import org.quiltmc.loader.impl.metadata.qmj.V1ModMetadataReader;
import org.quiltmc.loader.impl.plugin.UnsupportedModChecker.UnsupportedModDetails;
import org.quiltmc.loader.impl.plugin.UnsupportedModChecker.UnsupportedType;
import org.quiltmc.loader.impl.plugin.base.InternalModContainerBase;
import org.quiltmc.loader.impl.plugin.fabric.StandardFabricPlugin;
import org.quiltmc.loader.impl.plugin.gui.TempQuilt2OldStatusNode;
import org.quiltmc.loader.impl.plugin.quilt.ModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.ProvidedModOption;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreak;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDep;
import org.quiltmc.loader.impl.plugin.quilt.StandardQuiltPlugin;
import org.quiltmc.loader.impl.report.QuiltReport;
import org.quiltmc.loader.impl.report.QuiltReportedError;
import org.quiltmc.loader.impl.report.QuiltStringSection;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl.LoadOptionResult;
import org.quiltmc.loader.impl.solver.Sat4jWrapper;
import org.quiltmc.loader.impl.util.AsciiTableGenerator;
import org.quiltmc.loader.impl.util.AsciiTableGenerator.AsciiTableColumn;
import org.quiltmc.loader.impl.util.AsciiTableGenerator.AsciiTableRow;
import org.quiltmc.loader.impl.util.FileHasherImpl;
import org.quiltmc.loader.impl.util.HashUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.util.sat4j.specs.TimeoutException;

import net.fabricmc.api.EnvType;

/** The main manager for loader plugins, and the mod finding process in general.
 * <p>
 * Unlike {@link QuiltLoader} itself, it does make sense to have multiple of these at once: one for loading plugins that
 * will be used, and many more for "simulating" mod loading. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltPluginManagerImpl implements QuiltPluginManager {

	public final boolean simulationOnly;
	public final QuiltLoaderConfig config;
	final GameProvider game;
	final Version gameVersion;

	private final Path gameDir, configDir, modsDir, cacheDir;
	private final Path absGameDir, absModsDir;
	final Map<Path, Path> pathParents = new HashMap<>();
	final Map<Path, String> customPathNames = new HashMap<>();
	Map<Path, List<List<Path>>> sourcePaths;

	public final FileHasherImpl hasher;

	/** Map of folder to the plugin id which added it. */
	final Map<Path, String> modFolders = new LinkedHashMap<>();
	final Map<Path, QuiltStatusNode> modPathGuiNodes = new HashMap<>();
	final Map<Path, PathLoadState> modPaths = new LinkedHashMap<>();
	final Map<ModLoadOption, String> modProviders = new HashMap<>();
	final Map<String, PotentialModSet> modIds = new LinkedHashMap<>();

	final Map<TentativeLoadOption, BasePluginContext> tentativeLoadOptions = new LinkedHashMap<>();

	public final StandardQuiltPlugin theQuiltPlugin;
	private final StandardFabricPlugin theFabricPlugin;

	BuiltinPluginContext theQuiltPluginContext;
	BuiltinPluginContext theFabricPluginContext;

	final Map<QuiltLoaderPlugin, BasePluginContext> plugins = new LinkedHashMap<>();
	final Map<String, QuiltPluginContextImpl> pluginsById = new HashMap<>();
	final Map<String, QuiltPluginClassLoader> pluginsByPackage = new HashMap<>();

	/** Every mod id that contained a plugin, at any point. Used to scan for plugins at the start of each cycle. */
	final Set<String> idsWithPlugins = new HashSet<>();
	boolean pluginIdsChanged = false;

	final Sat4jWrapper solver = new Sat4jWrapper();

	/** Set to null if {@link QuiltLoaderConfig#singleThreadedLoading} is true, otherwise this will be a useful
	 * value. */
	private final ExecutorService executor;

	final Queue<MainThreadTask> mainThreadTasks;

	public final GuiManagerImpl guiManager = GuiManagerImpl.MANAGER;
	/** The root tree node for the "files" tab. */
	public final QuiltStatusNode guiFileRoot = QuiltFork.createTreeNode();
	public final QuiltStatusNode guiModsRoot =  QuiltFork.createTreeNode();
	private QuiltStatusNode guiNodeModsFromPlugins;
	final Map<ModLoadOption, QuiltStatusNode> modGuiNodes = new HashMap<>();
	final List<QuiltJsonGuiMessage> errors = new ArrayList<>();
	public final Map<UnsupportedModChecker.UnsupportedType, QuiltDisplayedError> guiUnknownMods = new HashMap<>();

	/** Only written by {@link #runSingleCycle()}, only read during crash report generation. */
	private PerCycleStep perCycleStep;

	/** Only written by {@link #runInternal(boolean)}, only read during crash report generation. */
	private int cycleNumber = 0;

	// TEMP
	final Deque<QuiltTreeNode> state = new ArrayDeque<>();

	public QuiltPluginManagerImpl(Path gameDir, Path configDir, Path modsDir, Path cacheDir, GameProvider game, QuiltLoaderConfig options) {
		this(gameDir, configDir, modsDir, cacheDir, game, false, options);
	}

	public QuiltPluginManagerImpl(Path gameDir, Path configDir, Path modsDir, Path cacheDir, GameProvider game, boolean simulationOnly, QuiltLoaderConfig config) {
		this.simulationOnly = simulationOnly;
		this.game = game;
		gameVersion = game == null ? null : Version.of(game.getNormalizedGameVersion());
		this.config = config;
		this.gameDir = gameDir;
		this.configDir = configDir;
		this.modsDir = modsDir;
		this.cacheDir = cacheDir;
		this.absGameDir = gameDir.toAbsolutePath().normalize();
		this.absModsDir = modsDir.toAbsolutePath().normalize();

		this.hasher = new FileHasherImpl(this::getParent);

		this.executor = config.singleThreadedLoading ? null : Executors.newCachedThreadPool();
		this.mainThreadTasks = config.singleThreadedLoading ? new ArrayDeque<>() : new ConcurrentLinkedQueue<>();

		customPathNames.put(gameDir, "<game>");
		customPathNames.put(modsDir, "<mods>");

		theQuiltPlugin = new StandardQuiltPlugin();
		theFabricPlugin = new StandardFabricPlugin();
	}

	private BuiltinPluginContext addBuiltinPlugin(BuiltinQuiltPlugin plugin, String id) {
		BuiltinPluginContext ctx = new BuiltinPluginContext(this, id, plugin);
		plugin.load(ctx, Collections.emptyMap());
		plugins.put(plugin, ctx);
		return ctx;
	}

	// #######
	// Loading
	// #######

	@Override
	public QuiltPluginTask<Path> loadZip(Path zip) {
		if (config.singleThreadedLoading) {
			try {
				return QuiltPluginTask.createFinished(loadZipNow(zip));
			} catch (IOException | NonZipException e) {
				return QuiltPluginTask.createFailed(e);
			}
		}
		return submit(null, () -> loadZipNow(zip));
	}

	/** Kept for backwards compatibility with the first versions of RGML-Quilt, as it invoked this using reflection. */
	@Deprecated
	private Path loadZip0(Path zip) throws IOException, NonZipException {
		return loadZipNow(zip);
	}

	@Override
	public Path loadZipNow(Path zip) throws IOException, NonZipException {
		String name = zip.getFileName().toString();
		try {
			QuiltZipPath qRoot = new QuiltZipFileSystem(name, zip, "").getRoot();
			pathParents.put(qRoot, zip);
			return qRoot;
		} catch (IOException e) {
			if (name.endsWith(".zip") || name.endsWith(".jar")) {
				if (e instanceof ZeroByteFileException) {
					throw e;
				}
				// Something probably went wrong while trying to load them as zips
				throw new IOException("Failed to read " + zip + " as a zip file: " + e.getMessage(), e);
			} else {
				throw new NonZipException(e);
			}
		}
	}

	@Override
	public Path createMemoryFileSystem(String name) {
		return new QuiltMemoryFileSystem.ReadWrite(name, true).getRoot();
	}

	@Override
	public Path copyToReadOnlyFileSystem(String name, Path folderRoot, boolean compress) throws IOException {
		return new QuiltMemoryFileSystem.ReadOnly(name, true, folderRoot, compress).getRoot();
	}

	@Override
	public List<List<Path>> convertToSourcePaths(Path path) {
		if (sourcePaths == null) {
			throw new IllegalStateException("Called too early - we haven't been able to generate the paths yet!");
		}
		if (path.getFileSystem() == FileSystems.getDefault()) {
			return Collections.singletonList(Collections.singletonList(path));
		}
		List<List<Path>> sources = sourcePaths.get(path);
		if (sources == null) {
			throw new IllegalArgumentException(
				"Unknown source path " + path + " " + path.getFileSystem()
					+ " - you can only call this for known paths!"
			);
		}
		return sources;
	}

	class SourcePathGenerator {
		final Map<FileSystem, QuiltMemoryFileSystem.ReadWrite> fsMap = new HashMap<>();
		final Map<Path, List<List<Path>>> tmpPaths = new HashMap<>();
		final Map<QuiltMemoryFileSystem.ReadWrite, QuiltMemoryFileSystem.ReadOnly> fsReadOnlyCopies = new HashMap<>();

		void generate() {
			for (Map.Entry<Path, Path> entry : pathParents.entrySet()) {
				createFS(entry.getKey());
				createFS(entry.getValue());
			}

			for (Path from : modPaths.keySet()) {
				createFS(from);
			}

			for (Map.Entry<Path, Path> entry : pathParents.entrySet()) {
				generate(entry.getKey());
				generate(entry.getValue());
			}

			for (Path from : modPaths.keySet()) {
				generate(from);
			}

			for (QuiltMemoryFileSystem.ReadWrite rw : fsMap.values()) {
				fsReadOnlyCopies.put(rw, rw.replaceWithReadOnly(false));
			}

			sourcePaths = new HashMap<>();

			for (Map.Entry<Path, List<List<Path>>> entry : tmpPaths.entrySet()) {
				List<List<Path>> oldList = entry.getValue();
				List<List<Path>> newList = new ArrayList<>();
				for (List<Path> oldPaths : oldList) {
					List<Path> newPaths = new ArrayList<>();
					for (Path oldPath : oldPaths) {
						FileSystem oldFS = oldPath.getFileSystem();
						QuiltMemoryFileSystem.ReadOnly newFS = fsReadOnlyCopies.get(oldFS);
						if (newFS == null) {
							newPaths.add(oldPath);
						} else {
							newPaths.add(newFS.getPath(oldPath.toString()));
						}
					}
					newList.add(unmodifiableList(newPaths));
				}
				sourcePaths.put(entry.getKey(), unmodifiableList(newList));
			}
		}

		private <T> List<T> unmodifiableList(List<T> from) {
			switch (from.size()) {
				case 0: {
					return Collections.emptyList();
				}
				case 1: {
					return Collections.singletonList(from.get(0));
				}
				default: {
					return Collections.unmodifiableList(Arrays.asList((T[]) from.toArray(new Object[0])));
				}
			}
		}

		void generate(Path path) {
			tmpPaths.put(path, walkSourcePaths(path));
		}

		void createFS(Path path) {
			FileSystem fs = path.getFileSystem();
			if (fs == FileSystems.getDefault()) {
				return;
			}
			if (!fsMap.containsKey(fs)) {
				String name;
				if (fs instanceof QuiltBaseFileSystem) {
					name = ((QuiltBaseFileSystem<?, ?>) fs).getName();
				} else {
					name = fs.toString();
				}
				QuiltMemoryFileSystem.ReadWrite gnFS;
				fsMap.put(fs, gnFS = new QuiltMemoryFileSystem.ReadWrite("shadow_" + name, true));
			}
		}

		Path map(Path from) {
			FileSystem fs = from.getFileSystem();
			if (fs == FileSystems.getDefault()) {
				return from;
			}
			QuiltMemoryFileSystem.ReadWrite shadowFs = fsMap.get(fs);
			if (shadowFs == null) {
				throw new IllegalStateException("Missing file system " + fs);
			}
			Path real = shadowFs.getPath(from.toString().replace(fs.getSeparator(), shadowFs.getSeparator()));
			Path parent = real.getParent();
			if (parent != null) {
				try {
					shadowFs.createDirectories(parent);
				} catch (IOException e) {
					throw new IllegalStateException("Failed to create reasonable parents!", e);
				}
			}
			try {
				if (Files.isRegularFile(from) && !Files.exists(real)) {
					shadowFs.createFile(real);
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to create the file!", e);
			}
			return real;
		}

		List<List<Path>> walkSourcePaths(Path from) {
			if (from.getFileSystem() == FileSystems.getDefault()) {
				return Collections.singletonList(Collections.singletonList(from));
			}

			Path fromRoot = from.getFileSystem().getPath("/");
			Collection<Path> joinedPaths = getJoinedPaths(fromRoot);

			if (joinedPaths != null) {
				List<List<Path>> paths = new ArrayList<>();
				for (Path path : joinedPaths) {
					for (List<Path> upper : walkSourcePaths(path)) {
						List<Path> fullList = new ArrayList<>();
						fullList.addAll(upper);
						fullList.add(map(from));
						paths.add(Collections.unmodifiableList(fullList));
					}
				}
				return unmodifiableList(paths);
			}

			Path parent = getParent(fromRoot);
			if (parent == null) {
				// That's not good
				return Collections.singletonList(Collections.singletonList(map(from)));
			} else {
				List<List<Path>> paths = new ArrayList<>();
				for (List<Path> upper : walkSourcePaths(parent)) {
					List<Path> fullList = new ArrayList<>();
					fullList.addAll(upper);
					fullList.add(map(from));
					paths.add(Collections.unmodifiableList(fullList));
				}
				return unmodifiableList(paths);
			}
		}
	}


	// #################
	// Identifying Paths
	// #################

	@Override
	public String describePath(Path path) {

		String custom = customPathNames.get(path);
		if (custom != null) {
			return custom;
		}

		StringBuilder sb = new StringBuilder();

		if (path.getNameCount() > 0) {
			sb.append(path.getFileName().toString());
		}

		if (path instanceof QuiltJoinedPath) {
			Collection<Path> parents = getJoinedPaths(((QuiltJoinedPath) path).getFileSystem().getRoot());
			sb.insert(0, "]/");
			for (Path p : parents) {
				sb.insert(0, describePath(p));
				sb.insert(0, ";");
			}
			// Replace the first semicolon with a square bracket
			sb.replace(0, 1, "[");
			return sb.toString();
		}

		Path p = path;
		Path upper;

		while (true) {
			upper = pathParents.get(p);

			if (upper == null) {
				upper = p.getParent();

				if (upper == null) {
					break;
				}
				sb.insert(0, '/');
			} else {
				sb.insert(0, '!');
			}

			custom = customPathNames.get(upper);
			if (custom != null) {
				sb.insert(0, custom);
				break;
			}

			if (upper.getNameCount() > 0) {
				sb.insert(0, upper.getFileName());
			}

			p = upper;
		}

		return sb.toString();
	}

	@Override
	public Path getParent(Path path) {
		return pathParents.getOrDefault(path, path.getParent());
	}

	@Override
	public Optional<Path> getRealContainingFile(Path file) {
		Path next = file;
		while (next.getFileSystem() != FileSystems.getDefault()) {
			next = getParent(next);
			if (next == null) {
				return Optional.empty();
			}
		}
		return Optional.of(next);
	}

	// #################
	// Joined Paths
	// #################

	@Override
	public boolean isJoinedPath(Path path) {
		return path instanceof QuiltJoinedPath;
	}

	@Override
	public Collection<Path> getJoinedPaths(Path path) {
		if (path instanceof QuiltJoinedPath) {
			QuiltJoinedPath joined = (QuiltJoinedPath) path;
			QuiltJoinedFileSystem jfs = joined.getFileSystem();
			int count = jfs.getBackingPathCount();
			List<Path> paths = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				paths.add(jfs.getBackingPath(i, joined));
			}
			return paths;
		}
		return null;
	}

	@Override
	public Path joinPaths(String name, List<Path> paths) {
		// Defensive copy
		List<Path> copiedPaths = Arrays.asList(paths.toArray(new Path[0]));
		if (copiedPaths.size() < 2) {
			throw new IllegalArgumentException("Too few paths! Just don't join them!");
		}
		return new QuiltJoinedFileSystem(name, copiedPaths).getRoot();
	}

	// ###################
	// Reading Mod Folders
	// ###################

	@Override
	public Set<Path> getModFolders() {
		return Collections.unmodifiableSet(modFolders.keySet());
	}

	@Override
	public @Nullable String getFolderProvider(Path modFolder) {
		return modFolders.get(modFolder);
	}

	// ############
	// Reading Mods
	// ############

	// By Path

	@Override
	public Set<Path> getModPaths() {
		return Collections.unmodifiableSet(modPaths.keySet());
	}

	@Override
	@Deprecated
	public @Nullable String getModProvider(Path mod) {
		return modProviders.get(getModLoadOption(mod));
	}

	@Override
	@Deprecated
	public @Nullable ModLoadOption getModLoadOption(Path file) {
		PathLoadState loadState = modPaths.get(file);
		if (loadState == null) {
			return null;
		}
		return loadState.getCurrentModOption();
	}

	@Override
	public @Nullable Map<String, List<ModLoadOption>> getModLoadOptions(Path mod) {
		PathLoadState loadState = modPaths.get(mod);
		return loadState == null ? null : loadState.getMap();
	}

	// by Mod ID

	@Override
	public Set<String> getModIds() {
		return Collections.unmodifiableSet(modIds.keySet());
	}

	@Override
	public Map<Version, ModLoadOption> getVersionedMods(String modId) {
		PotentialModSet set = modIds.get(modId);
		if (set != null) {
			return Collections.unmodifiableMap(set.byVersionSingles);
		} else {
			return Collections.emptyMap();
		}
	}

	@Override
	public Collection<ModLoadOption> getExtraMods(String modId) {
		PotentialModSet set = modIds.get(modId);
		if (set != null) {
			return Collections.unmodifiableCollection(set.extras);
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public Collection<ModLoadOption> getAllMods(String modId) {
		PotentialModSet set = modIds.get(modId);
		if (set != null) {
			return Collections.unmodifiableCollection(set.all);
		} else {
			return Collections.emptySet();
		}
	}

	// #########
	// # State #
	// #########

	@Override
	public Path getGameDirectory() {
		return gameDir;
	}

	@Override
	public Path getConfigDirectory() {
		return configDir;
	}

	@Override
	public Path getCacheDirectory() {
		return cacheDir;
	}

	@Override
	@Deprecated
	public EnvType getEnvironment() {
		if (game != null) {
			return MinecraftQuiltLoader.getEnvironmentType();
		}
		return EnvType.CLIENT;
	}

	// #######
	// # Gui #
	// #######

	@Override
	public PluginGuiTreeNode getGuiNode(ModLoadOption mod) {
		return modGuiNodes.get(mod);
	}

	@Override
	public PluginGuiTreeNode getRootGuiNode() {
		return guiFileRoot;
	}

	@Override
	public PluginGuiManager getGuiManager() {
		return guiManager;
	}

	public QuiltDisplayedError reportError(BasePluginContext reporter, QuiltLoaderText title) {
		QuiltJsonGuiMessage error = new QuiltJsonGuiMessage(null, reporter != null ? reporter.pluginId : null, title);
		errors.add(error);
		return error;
	}

	public void haltLoading(BasePluginContext reporter) {
		// TODO: Check if we're actually in the middle of #runInternal() and throw a different exception if we're not.
		// also if we're on a different thread then inform the main plugin manager that it should stop quickly
		// (although this might just mean telling it to stop waiting for a plugin task?)
		try {
			checkForErrors();
		} catch (TreeContainsModError | QuiltReportedError e) {
			throw HaltLoadingError.INSTANCE;
		}
		throw new Error();
	}

	// ############
	// # Internal #
	// ############

	public QuiltStatusNode getModsFromPluginsGuiNode() {
		if (guiNodeModsFromPlugins == null) {
			guiNodeModsFromPlugins = guiFileRoot.addChild(QuiltLoaderText.translate("gui.text.floating_mods_from_plugins"));
		}
		return guiNodeModsFromPlugins;
	}

	public List<QuiltJsonGuiMessage> getErrors() {
		Collections.sort(errors, Comparator.comparingInt(e -> e.ordering));
		return Collections.unmodifiableList(errors);
	}

	Class<?> findClass(String name, String pkg) throws ClassNotFoundException {
		if (pkg == null) {
			return null;
		}
		QuiltPluginClassLoader cl = pluginsByPackage.get(pkg);
		return cl == null ? null : cl.loadClass(name);
	}

	// Internal (Running)

	public ModSolveResultImpl run(boolean scanClasspath) throws QuiltReportedError {

		final QuiltReport report;

		outer: try {
			return runInternal(scanClasspath);
		} catch (ModSolvingError e) {
			e.printStackTrace();
			report = new QuiltReport("Quilt Loader: Crash Report");
			reportError(theQuiltPluginContext, QuiltLoaderText.translate("error.unhandled"))
				.appendDescription(QuiltLoaderText.translate("error.unhandled.desc"))
				.appendReportText("Unhandled ModSolvingError!")
				.setOrdering(-100)
				.appendThrowable(e)
				.addOpenQuiltSupportButton();

			break outer;
		} catch (TreeContainsModError | HaltLoadingError e) {
			report = new QuiltReport("Quilt Loader: Load Error Report");
			break outer;
		} catch (TimeoutException e) {
			e.printStackTrace();
			report = new QuiltReport("Quilt Loader: Load Timeout Report");
			reportError(theQuiltPluginContext, QuiltLoaderText.translate("error.unhandled"))
				.appendDescription(QuiltLoaderText.translate("error.unhandled.desc"))
				.appendReportText("Load timeout!")
				.appendThrowable(e)
				.setOrdering(-100)
				.addOpenQuiltSupportButton();
			break outer;
		} catch (QuiltReportedError e) {
			report = e.report;
			break outer;
		} catch (Throwable t) {
			t.printStackTrace();
			report = new QuiltReport("Quilt Loader: Critial Error Report");
			reportError(theQuiltPluginContext, QuiltLoaderText.translate("error.unhandled"))
				.appendDescription(QuiltLoaderText.translate("error.unhandled.desc"))
				.appendReportText("Unhandled Throwable!")
				.setOrdering(-100)
				.appendThrowable(t)
				.addOpenQuiltSupportButton();
			break outer;
		}

		// It's arguably the most important version - if anything goes wrong while writing this report
		// at least we know what code was used to generate it.
		report.overview("Quilt Loader Version: " + QuiltLoaderImpl.VERSION);

		if (!errors.isEmpty()) {
			int number = 1;

			for (QuiltJsonGuiMessage error : errors) {
				List<String> lines = error.toReportText();

				report.addStringSection("Error " + number, error.ordering, lines.toArray(new String[0]));
				number++;
			}
		}

		QuiltStringSection pluginState = report.addStringSection("Plugin State", 0);
		if (perCycleStep != null) {
			pluginState.lines("Cycle number = " + cycleNumber, "Cycle Step = " + perCycleStep, "");
		}

		pluginState.lines("Loaded Plugins (" + plugins.size() + "):");
		for (BasePluginContext ctx : plugins.values()) {
			StringBuilder sb = new StringBuilder();
			sb.append(" - '").append(ctx.pluginId()).append("'");
			if (ctx instanceof BuiltinPluginContext) {
				sb.append(" (Builtin)");
			} else if (ctx instanceof QuiltPluginContextImpl) {
				QuiltPluginContextImpl impl = (QuiltPluginContextImpl) ctx;
				sb.append(" from " + describePath(impl.optionFrom.from()));
			}
			pluginState.lines(sb.toString());
		}
		pluginState.lines("");

		// TODO: What other loader state do we need?
		pluginState.lines("");

		try {
			QuiltStringSection modTable = report.addStringSection("Mod Table", 100);
			modTable.setShowInLogs(false);
			appendModTable(modTable::lines);
		} catch (Throwable e) {
			report.addStacktraceSection("Crash while gathering mod table", 100, e);
		}

		try {
			QuiltStringSection modDetails = report.addStringSection("Mod Details", 100);
			modDetails.setShowInLogs(false);
			appendModDetails(modDetails::lines);
		} catch (Throwable e) {
			report.addStacktraceSection("Crash while gathering mod details", 100, e);
		}

		populateModsGuiTab(null);

		throw new QuiltReportedError(report);
	}

	private void populateModsGuiTab(ModSolveResultImpl result) {
		// WARNING Temporary mod list
		// (This will be moved to on mod load option addition)
		for (Entry<String, PotentialModSet> entry : modIds.entrySet()) {
			String id = entry.getKey();
			PotentialModSet set = entry.getValue();
			QuiltStatusNode gui = guiModsRoot.addChild(QuiltLoaderText.of(id), SortOrder.ALPHABETICAL_ORDER);
			if (set.all.size() > 1) {
				gui.icon(QuiltLoaderGui.iconFolder());
			} else {
				gui.icon(QuiltLoaderGui.iconUnknownFile());
			}

			for (ModLoadOption option : set.all) {
				QuiltStatusNode modNode = gui.addChild(QuiltLoaderText.of(option.version().toString()), SortOrder.ALPHABETICAL_ORDER);
				option.populateModsTabInfo(modNode);
				if (result != null && result.directMods().containsValue(option)) {
					modNode.icon(modNode.icon().withDecoration(QuiltLoaderGui.iconTick()));
				}
			}
		}

		class TempNode {
			final String name;
			QuiltLoaderIcon typeIcon;
			final Map<String, TempNode> children = new TreeMap<>();

			UnsupportedModDetails details;

			TempNode(String name) {
				this.name = name;
			}

			void populate(QuiltTreeNode dst) {
				if (children.isEmpty()) {
					if (details != null) {
						details.addToFilesNode(dst);
					}
					return;
				}

				StringBuilder sb = new StringBuilder();
				TempNode start = this;
				while (start.children.size() == 1) {
					TempNode next = start.children.values().iterator().next();
					if (next.typeIcon != QuiltLoaderGui.iconFolder()) {
						break;
					}
					start = next;
					if (sb.length() > 0) {
						sb.append("/");
					}
					sb.append(start.name);
				}

				dst = dst.addChild(SortOrder.ALPHABETICAL_ORDER);
				dst.text(QuiltLoaderText.of(sb.length() == 0 ? name : sb.toString()));
				dst.icon(start.typeIcon);

				if (details != null) {
					details.addToFilesNode(dst);
				}

				for (TempNode child : start.children.values()) {
					child.populate(dst.addChild(SortOrder.ALPHABETICAL_ORDER).text(QuiltLoaderText.of(child.name)).icon(child.typeIcon));
				}
			}
		}

		Map<UnsupportedType, TempNode> roots = new HashMap<>();

		for (PathLoadState loadState : modPaths.values()) {
			Path path = loadState.path;
			ModLoadOption current = loadState.getCurrentModOption();
			QuiltStatusNode guiNode = modPathGuiNodes.get(path);
			if (current == null) {
				final UnsupportedType type;
				if (loadState.unsupportedType != null) {
					type = loadState.unsupportedType.type;
					loadState.unsupportedType.addToFilesNode(guiNode);
				} else {
					type = UnsupportedType.UNKNOWN;
					guiNode.addChild(QuiltLoaderText.translate("warn.unhandled_mod")).level(QuiltWarningLevel.WARN);
				}

				List<List<Path>> sourcePaths = convertToSourcePaths(path);
				// Since this is a *user* path, we only handle the first path
				if (sourcePaths.size() < 1) {
					continue;
				}

				TempNode currentNode = roots.computeIfAbsent(type, t -> {
					TempNode node = new TempNode("/");
					node.typeIcon = QuiltLoaderGui.iconFolder();
					return node;
				});
				for (Path subPath : sourcePaths.get(0)) {
					if (absModsDir != null || absGameDir != null) {
						Path real = subPath.toAbsolutePath();
						if (absModsDir != null && real.startsWith(absModsDir)) {
							currentNode = currentNode.children.computeIfAbsent("<mods>", t -> new TempNode("<mods>"));
							currentNode.typeIcon = QuiltLoaderGui.iconFolder();
							if (real.equals(absModsDir)) {
								continue;
							}
							subPath = absModsDir.relativize(real);
						} else if (absGameDir != null && real.startsWith(absGameDir)) {
							currentNode = currentNode.children.computeIfAbsent("<game>", t -> new TempNode("<game>"));
							currentNode.typeIcon = QuiltLoaderGui.iconFolder();
							if (real.equals(absGameDir)) {
								continue;
							}
							subPath = absGameDir.relativize(real);
						}
					}
					for (Path element : subPath) {
						String fileName = element.getFileName().toString();
						currentNode = currentNode.children.computeIfAbsent(fileName, t -> new TempNode(fileName));
						currentNode.typeIcon = QuiltLoaderGui.iconFolder();
					}
					currentNode.typeIcon = QuiltLoaderGui.iconUnknownFile();
				}

				currentNode.details = loadState.unsupportedType;
			}
		}

		for (Map.Entry<UnsupportedType, TempNode> entry : roots.entrySet()) {
			UnsupportedType type = entry.getKey();
			TempNode node = entry.getValue();
			QuiltDisplayedError message = QuiltLoaderGui.createError(QuiltLoaderText.of("todo: translate: Unknown mods of type " + type));
			guiUnknownMods.put(type, message);
			node.populate(message.treeNode());
		}
	}

	public String createModTable() {
		StringBuilder sb = new StringBuilder();
		appendModTable(line -> {
			sb.append(line);
			sb.append("\n");
		});
		return sb.toString();
	}

	private void appendModTable(Consumer<String> to) {

		// Columns:
		// - Name
		// - ID
		// - version
		// - loader plugin
		// - source path(s)

		AsciiTableGenerator table = new AsciiTableGenerator();

		AsciiTableColumn modColumn = table.addColumn("Mod", false);
		AsciiTableColumn id = table.addColumn("ID", false);
		AsciiTableColumn version = table.addColumn("Version", false);
		AsciiTableColumn plugin = table.addColumn("Plugin", false);
		AsciiTableColumn flags = table.addColumn("Flags", false);
		AsciiTableColumn hash = table.addColumn("File Hash (SHA-1)", false);
		AsciiTableColumn file = table.addColumn("File(s)", false);
		AsciiTableColumn subFile = null;

		List<ModLoadOption> mods = new ArrayList<>();

		if (!modIds.containsKey(QuiltLoaderImpl.MOD_ID)) {
			AsciiTableRow row = table.addRow();
			row.put(modColumn, "Quilt Loader");
			row.put(id, QuiltLoaderImpl.MOD_ID);
			row.put(version, QuiltLoaderImpl.VERSION);
			row.put(plugin, "!missing!");
		}

		for (PotentialModSet set : this.modIds.values()) {
			mods.addAll(set.all);
		}

		for (ModLoadOption mod : mods.stream().sorted(Comparator.comparing(i -> i.metadata().name())).collect(Collectors.toList())) {
			AsciiTableRow row = table.addRow();
			// - Name
			// - ID
			// - version
			// - loader plugin
			// - source path(s)
			row.put(modColumn, mod.metadata().name());
			row.put(id, mod.metadata().id());
			row.put(version, mod.metadata().version());
			row.put(plugin, mod.loader().pluginId());
			StringBuilder flagStr = new StringBuilder();
			flagStr.append(theQuiltPlugin.hasDepsChanged(mod) ? QuiltLoaderImpl.FLAG_DEPS_CHANGED : '.');
			flagStr.append(theQuiltPlugin.hasDepsRemoved(mod) ? QuiltLoaderImpl.FLAG_DEPS_REMOVED : '.');
			row.put(flags, flagStr);

			List<List<Path>> allPaths = InternalModContainerBase.walkSourcePaths(this, mod.from());

			for (int pathsIndex = 0; pathsIndex < allPaths.size(); pathsIndex++) {
				List<Path> paths = allPaths.get(pathsIndex);

				Path from = paths.get(0);
				if (FasterFiles.isRegularFile(from)) {
					String hashString;
					try {
						hashString = HashUtil.hashToString(hasher.computeNormalHash(from));
					} catch (IOException e) {
						hashString = "<" + e.getMessage() + ">";
					}
					row.put(hash, hashString);
				}

				if (pathsIndex != 0) {
					row = table.addRow();
				}

				row.put(file, QuiltLoaderImpl.prefixPath(absGameDir, absModsDir, paths.get(0)));

				if (paths.size() > 1) {
					if (subFile == null) {
						subFile = table.addColumn("Sub-File", false);
					}
					StringBuilder subPathStr = new StringBuilder();
					Iterator<Path> pathsIter = paths.iterator();
					pathsIter.next(); // skip first element
					while (pathsIter.hasNext()) {
						subPathStr.append(QuiltLoaderImpl.prefixPath(absGameDir, absModsDir, pathsIter.next()));
						if (pathsIter.hasNext()) {
							subPathStr.append("!");
						}
					}

					row.put(subFile, subPathStr.toString());
				}
			}
		}

		table.appendTable(to);
	}

	public String createModDetails() {
		StringBuilder sb = new StringBuilder();
		appendModTable(line -> {
			sb.append(line);
			sb.append("\n");
		});
		return sb.toString();
	}

	private void appendModDetails(Consumer<String> to) {
		// Sorted by path

		// Path to contained paths (each is sorted)
		// Each path is the highest-level parent path
		// so for real folders this is `mods/`
		// and for jij it's the file of the containing mod
		// so <mods>/buildcraft-9.0.0.jar!META-INF/jars/LibBlockAttributes-1.0.0.jar
		//  would have one entry:
		// - '<mods>/buildcraft-9.0.0.jar' -> [ '/META-INF/jars/LibBlockAttributes-1.0.0.jar' ]
		Comparator<Path> pathComparator = (a, b) -> {
			if (a.getClass() == b.getClass()) {
				return a.compareTo(b);
			}
			return a.toAbsolutePath().toString().compareTo(b.toAbsolutePath().toString());
		};
		Map<Path, Set<Path>> pathMap = new TreeMap<>(pathComparator);
		Set<Path> rootFsPaths = Collections.newSetFromMap(new TreeMap<>(pathComparator));

		Map<ModLoadOption, List<String>> insideBox = new HashMap<>();

		for (ModLoadOption mod : modProviders.keySet()) {

			Path file = mod.from();

			List<String> text = new ArrayList<>();
			insideBox.put(mod, text);
			text.add("Loaded by  '" + mod.loader().pluginId() + "'");
			text.add("Name     = '" + mod.metadata().name() + "'");
			text.add("ID       = '" + mod.metadata().id() + "'");
			text.add("Version  = '" + mod.metadata().version() + "'");
			text.add("LoadType = " + mod.metadata().loadType());
			boolean first = true;
			for (ProvidedMod provided : mod.metadata().provides()) {
				String start = first ? "Provides   '" : "           '";
				text.add(start + provided.id() + "' " + provided.version());
				first = false;
			}

			for (ModDependency dep : mod.metadata().depends()) {
				first = true;
				for (String line : describeDependency(dep)) {
					if (first) {
						text.add("Depends on " + line);
					} else {
						text.add("           " + line);
					}
					first = false;
				}
			}

			for (ModDependency dep : mod.metadata().breaks()) {
				first = true;
				for (String line : describeDependency(dep)) {
					if (first) {
						text.add("Breaks  on " + line);
					} else {
						text.add("           " + line);
					}
					first = false;
				}
			}

			while (true) {
				Path parentFile = file;
				Path parent = null;
				while ((parentFile = parentFile.getParent()) != null) {
					parent = parentFile;
				}
				Path realParent = parent == null ? null : getParent(parent);
				if (realParent == null) {
					rootFsPaths.add(file);
					break;
				} else {
					pathMap.computeIfAbsent(realParent, p -> Collections.newSetFromMap(new TreeMap<>(pathComparator))).add(file);
					file = realParent;
				}
			}
		}

		for (Path root : rootFsPaths) {

			if (isJoinedPath(root)) {
				Collection<Path> roots = getJoinedPaths(root);
				to.accept("Joined path [" + roots.size() + "]:");
				for (Path in : roots) {
					to.accept(" - '" + QuiltLoaderImpl.prefixPath(absGameDir, absModsDir, in) + "'");
				}
				to.accept("mod:");
			} else {
				to.accept(QuiltLoaderImpl.prefixPath(absGameDir, absModsDir, root) + ":");
			}

			for (String line : processDetail(pathMap, insideBox, root, 0)) {
				to.accept(line);
			}
			to.accept("");
		}
	}

	private static List<String> describeDependency(ModDependency dep) {
		List<String> lines = new ArrayList<>();

		if (dep instanceof ModDependency.Only) {
			ModDependency.Only on = (ModDependency.Only) dep;
			StringBuilder sb = new StringBuilder("'");
			if (!on.id().mavenGroup().isEmpty()) {
				sb.append(on.id().mavenGroup());
				sb.append(":");
			}
			sb.append(on.id().id());
			sb.append("'");
			if (!on.versionRange().isEmpty()) {
				sb.append(" ");
				sb.append(on.versionRange().toString());
			}
			lines.add(sb.toString());
		} else {
			final Collection<? extends ModDependency> collection;
			if (dep instanceof ModDependency.Any) {
				ModDependency.Any any = (ModDependency.Any) dep;
				collection = any;
				lines.add("Any of:");
			} else {
				collection = (ModDependency.All) dep;
				lines.add("All of:");
			}

			for (ModDependency sub : collection) {
				List<String> subLines = describeDependency(sub);
				boolean first = true;
				for (String line : subLines) {
					if (first) {
						lines.add("  - " + line);
					} else {
						lines.add("    " + line);
					}
					first = false;
				}
			}
		}

		return lines;
	}

	/** Packed array of "box lookups", with indices being used according to the following:<br>
	 * 011111111111112<br>
	 * 3_____________4<br>
	 * 3_____________4<br>
	 * 3_____________4<br>
	 * 566666666666667 */
	private static final String[] BOXES = { "########", "+-+||+-+", "...:::.:" };

	private List<String> processDetail(Map<Path, Set<Path>> pathMap, Map<ModLoadOption, List<String>> insideBox, Path path, int depth) {

		List<String> allLines = new ArrayList<>();

		List<String> boxLines = insideBox.get(modPaths.get(path).getCurrentModOption());
		if (boxLines != null) {
			allLines.addAll(boxLines);
		}

		Set<Path> subPaths = pathMap.get(path);
		if (subPaths != null) {

			allLines.add("");
			allLines.add("Contained Jars (" + subPaths.size() + "):");
			allLines.add("");

			for (Path sub : subPaths) {
				allLines.add(sub.toString() + ":");
				allLines.addAll(processDetail(pathMap, insideBox, sub, depth + 1));
				allLines.add("");
			}
		}

		int maxLength = 0;

		for (String line : allLines) {
			maxLength = Math.max(maxLength, line.length());
		}

		String box = BOXES[depth % BOXES.length];

		StringBuilder sb = new StringBuilder();
		sb.append(box.charAt(0));
		for (int i = -4; i < maxLength; i++) {
			sb.append(box.charAt(1));
		}
		sb.append(box.charAt(2));

		allLines.add(0, sb.toString());
		sb.setLength(0);

		for (int i = 1; i < allLines.size(); i++) {
			String inner = allLines.get(i);
			sb.setLength(0);
			sb.append(box.charAt(3));
			sb.append("  ");
			sb.append(inner);
			for (int j = inner.length() - 2; j < maxLength; j++) {
				sb.append(' ');
			}
			sb.append(box.charAt(4));
			allLines.set(i, sb.toString());
		}
		sb.setLength(0);
		sb.append(box.charAt(5));
		for (int i = -4; i < maxLength; i++) {
			sb.append(box.charAt(6));
		}
		sb.append(box.charAt(7));
		allLines.add(sb.toString());

		return allLines;
	}

	private ModSolveResultImpl runInternal(boolean scanClasspath) throws ModResolutionException, TimeoutException {

		theQuiltPluginContext = addBuiltinPlugin(theQuiltPlugin, QUILT_LOADER);
		theFabricPluginContext = addBuiltinPlugin(theFabricPlugin, QUILTED_FABRIC_LOADER);

		if (game != null) {
			theQuiltPlugin.addBuiltinMods(game);
		}

		if (scanClasspath) {
			scanClasspath();
		}

		theQuiltPluginContext.addFolderToScan(modsDir);

		scanAdditionalMods(System.getProperty(SystemProperties.ADD_MODS), "system property");
		scanAdditionalMods(QuiltLoaderImpl.InitHelper.get().getAdditionalModsArgument(), "argument");

		for (int cycle = 0; cycle < 1000; cycle++) {
			this.cycleNumber = cycle + 1;
			ModSolveResultImpl result = runSingleCycle();
			checkForErrors();
			if (result != null) {
				new SourcePathGenerator().generate();
				populateModsGuiTab(result);
				return result;
			}
		}

		throw new ModSolvingError(
			"Too many cycles! 1000 cycles of plugin loading is a lot, since each one *could* take a second..."
		);
	}

	private void scanClasspath() {
		QuiltStatusNode classpathRoot = guiFileRoot.addChild(QuiltLoaderText.translate("gui.text.classpath"));

		ClasspathModCandidateFinder.findCandidatesStatic((paths) -> {
			final Path path;
			if (paths.size() > 1) {
				path = new QuiltJoinedFileSystem("classpath", paths).getRoot();
			} else {
				path = paths.get(0);
			}

			if (FasterFiles.exists(path)) {
				String name = describePath(path);
				QuiltStatusNode clNode = classpathRoot.addChild(QuiltLoaderText.of(name), SortOrder.ALPHABETICAL_ORDER);
				if (FasterFiles.isDirectory(path)) {
					clNode.icon(QuiltLoaderGui.iconFolder());
				}
				scanModFile(path, new ModLocationImpl(true, true), clNode);
			}
		});
	}

	private void scanAdditionalMods(String value, String source) {
		if (value == null) {
			return;
		}

		ArgumentModCandidateFinder.addMods(theQuiltPluginContext, value, source);
	}

	private ModSolveResultImpl runSingleCycle() throws ModResolutionException, TimeoutException {

		PerCycleStep step = PerCycleStep.START;
		this.perCycleStep = step;
		this.pluginIdsChanged = false;

		refreshPlugins();
		checkForErrors();

		ModSolveResultImpl result = null;

		while (true) {
			if (config.singleThreadedLoading) {
				MainThreadTask task;
				while ((task = mainThreadTasks.poll()) != null) {
					task.execute(this);
				}

				// TODO: Also wait for GUI tasks

			} else {
				throw new AbstractMethodError("// TODO: Wait for scheduled tasks while running main thread tasks!");
			}

			switch (step) {
				case START: {
					for (QuiltPluginContext pluginCtx : plugins.values()) {
						pluginCtx.plugin().beforeSolve();
					}
					checkForErrors();
					this.perCycleStep = step = PerCycleStep.SOLVE;
					break;
				}
				case SOLVE: {

					if (pluginIdsChanged) {
						return null;
					}

					if (solver.hasSolution()) {
						ModSolveResultImpl partialResult = getPartialSolution();

						if (processTentatives(partialResult)) {
							this.perCycleStep = step = PerCycleStep.POST_SOLVE_TENTATIVE;
						} else {
							this.perCycleStep = step = PerCycleStep.SUCCESS;
							result = partialResult;
						}

						break;
					} else {
						handleSolverFailure();
						checkForErrors();
						// If we reach this point then a plugin successfully handled the error
						// so we should move on to the next cycle.
						return null;
					}
				}
				case POST_SOLVE_TENTATIVE: {
					// TODO: Deal with tentative load options!
					return null;
				}
				case SUCCESS: {

					cleanup();

					return result;
				}
				default: {
					throw new IllegalStateException("Unknown PerCycleStep " + step);
				}
			}
		}

	}

	private void handleSolverFailure() throws TimeoutException, ModSolvingError {

		SolverErrorHelper helper = new SolverErrorHelper(this);
		boolean failed = false;

		solver_error_iteration: do {
			Collection<Rule> rules = solver.getError();

			for (Entry<QuiltLoaderPlugin, BasePluginContext> entry : plugins.entrySet()) {
				QuiltLoaderPlugin plugin = entry.getKey();
				BasePluginContext ctx = entry.getValue();
				boolean recovered = plugin.handleError(rules);
				Rule blamed = null;
				try {
					ctx.blameableRules = rules;
					recovered = plugin.handleError(rules);
					blamed = ctx.blamedRule;
				} finally {
					ctx.blameableRules = null;
					ctx.blamedRule = null;
				}

				if (recovered) {

					if (blamed != null) {
						// Okay, so plugins aren't meant to do this
						// (Either blame a rule, OR handle it in some other way)
						// Since this is an invalid state we'll report this error
						// and report that the plugin did the wrong thing
						failed = true;
						helper.reportSolverError(rules);
						solver.removeRule(blamed);
						reportError(theQuiltPluginContext, QuiltLoaderText.translate("plugin.illegal_state.recovered_and_blamed", ctx.pluginId));
						return;
					}

					if (blamed == null) {
						// A plugin recovered from an error
						if (failed) {
							// but we already failed, so it's too late
							// since the recovery probably messed something up
							// we'll just exit the loop here and drop any other errors.
							break solver_error_iteration;
						}
						// And it's the first error, so we can just move on to the next cycle
						return;
					}
				} else if (blamed != null) {
					failed = true;
					helper.reportSolverError(rules);
					solver.removeRule(blamed);
					continue solver_error_iteration;
				}
			}

			// No plugin blamed any rules
			// So we'll just pick one of them randomly and remove it.

			failed = true;
			helper.reportSolverError(rules);

			Rule pickedRule = rules.stream().filter(r -> r instanceof QuiltRuleBreak).findAny().orElse(null);

			if (pickedRule == null) {
				pickedRule = rules.stream().filter(r -> r instanceof QuiltRuleDep).findAny().orElse(null);
			}

			if (pickedRule == null) {
				pickedRule = rules.stream().filter(r -> !(r instanceof ModIdDefinition)).findAny().orElse(null);
			}

			if (pickedRule == null) {
				pickedRule = rules.iterator().next();
			}

			solver.removeRule(pickedRule);

		} while (!solver.hasSolution());

		helper.reportErrors();

		if (failed) {
			// Okay, so we failed but we've reached the end of the list of problems
			// Just return here since the cycle handles this
			return;
		} else {
			// This is an odd state where we encountered an error,
			// but then found a solution, and DIDN'T exit from this method
			// so something has gone wrong internally.
			reportError(theQuiltPluginContext, QuiltLoaderText.translate("solver.illegal_state.TODO"));
			return;
		}
	}

	/** Checks for any {@link WarningLevel#FATAL} or {@link WarningLevel#ERROR} gui nodes, and throws an exception if
	 * this is the case. */
	private void checkForErrors() throws TreeContainsModError, QuiltReportedError {

		Iterator<QuiltJsonGuiMessage> iterator = errors.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().isFixed()) {
				iterator.remove();
			}
		}

		if (!errors.isEmpty()) {
			throw new QuiltReportedError(new QuiltReport("Quilt Loader: Failed to load"));
		}

		QuiltWarningLevel maximumLevel = guiFileRoot.maximumLevel();
		if (maximumLevel == QuiltWarningLevel.FATAL || maximumLevel == QuiltWarningLevel.ERROR) {
			throw new TreeContainsModError();
		}
	}

	BasePluginContext getPlugin(String id) {
		switch (id) {
			case QUILT_LOADER: {
				return theQuiltPluginContext;
			}
			case QUILTED_FABRIC_LOADER: {
				return theFabricPluginContext;
			}
			default: {
				return pluginsById.get(id);
			}
		}
	}

	private void refreshPlugins() throws ModSolvingError {
		for (String id : idsWithPlugins) {
			QuiltPluginContextImpl current = pluginsById.get(id);
			PotentialModSet potential = modIds.get(id);
			if (potential == null) {

				if (current == null) {
					continue;
				} else {
					// Okay, so what now?
					// TODO: decide whether to unload plugins if their backing mod vanishes?
					continue;
				}
			}

			// Find the load option with the greatest version
			List<ModLoadOption> options = potential.byVersionAll.lastEntry().getValue();

			if (options.isEmpty()) {
				if (!potential.all.isEmpty()) {
					throw new IllegalStateException(
						"PotentialModSet.byVersionAll contained an empty list? " + potential.byVersionAll
					);
				}
				// Okay, so what now?
				// TODO: decide whether to unload plugins if their backing mod vanishes?
				continue;
			}

			ModLoadOption option = options.get(0);

			if (current != null) {
				if (current.optionFrom == option) {
					continue;
				}

				// TODO: Check both the current plugin and incoming mod to see if we actually need to reload
			}

			loadPlugin(option);
		}
	}

	private void cleanup() {
		// TODO: Cleanup:
		// - zips loaded with "loadZip(Path)" but not claimed or used by a mod
	}

	/** Processes {@link TentativeLoadOption}s.
	 *
	 * @return True if any tentative options were found, false otherwise. */
	private boolean processTentatives(ModSolveResult partialResult) {

		SpecificLoadOptionResult<TentativeLoadOption> tentativeResult = //
			partialResult.getResult(TentativeLoadOption.class);

		List<TentativeLoadOption> tentatives = new ArrayList<>();
		for (TentativeLoadOption option : tentativeResult.getOptions()) {
			if (tentativeResult.isPresent(option)) {
				tentatives.add(option);
			}
		}

		if (tentatives.isEmpty()) {
			return false;
		} else {

			for (TentativeLoadOption option : tentatives) {
				QuiltPluginContext pluginSrc = tentativeLoadOptions.get(option);
				QuiltPluginTask<? extends LoadOption> resolution = pluginSrc.plugin().resolve(option);
			}

			return true;
		}
	}

	ModSolveResultImpl getPartialSolution() throws ModSolvingError, TimeoutException {
		Collection<LoadOption> solution = solver.getSolution();

		Map<String, ModLoadOption> directModsMap = new HashMap<>();
		Map<String, ModLoadOption> providedModsMap = new HashMap<>();
		Map<Class<?>, LoadOptionResult<?>> extraResults;
		final Map<Class<?>, Map<Object, Boolean>> optionMap = new HashMap<>();

		for (LoadOption option : solution) {

			boolean load = true;
			if (LoadOption.isNegated(option)) {
				option = option.negate();
				load = false;
			}

			if (load && option instanceof ModLoadOption) {
				ModLoadOption mod = (ModLoadOption) option;
				putMod(directModsMap, mod.id(), mod);

				for (ProvidedMod provided : mod.metadata().provides()) {
					putMod(providedModsMap, provided.id(), mod);
				}
			}

			putHierarchy(option, load, optionMap);
		}

		extraResults = new HashMap<>();

		for (Map.Entry<Class<?>, Map<Object, Boolean>> entry : optionMap.entrySet()) {
			Class<?> cls = entry.getKey();
			Map<Object, Boolean> map = entry.getValue();
			extraResults.put(cls, createLoadResult(cls, map));
		}

		SortedMap<Path, String> unknownRegularFiles = new TreeMap<>();
		SortedMap<String, String> unknownIrregularFies = new TreeMap<>();

		path_loop: for (PathLoadState loadState : modPaths.values()) {
			String type = loadState.unsupportedType != null ? loadState.unsupportedType.type.type : "unknown";
			for (String plugin : loadState.getPlugins()) {
				if (!loadState.getLoadedBy(plugin).isEmpty()) {
					continue path_loop;
				}
			}
			Path path = loadState.path;
			if (path.getFileSystem() == FileSystems.getDefault()) {
				unknownRegularFiles.put(path, type);
			} else {
				unknownIrregularFies.put(describePath(path), type);
			}
		}

		directModsMap = Collections.unmodifiableMap(directModsMap);
		providedModsMap = Collections.unmodifiableMap(providedModsMap);
		extraResults = Collections.unmodifiableMap(extraResults);
		unknownRegularFiles = Collections.unmodifiableSortedMap(unknownRegularFiles);
		unknownIrregularFies = Collections.unmodifiableSortedMap(unknownIrregularFies);

		return new ModSolveResultImpl(directModsMap, providedModsMap, extraResults, unknownRegularFiles, unknownIrregularFies);
	}

	private static void putMod(Map<String, ModLoadOption> modMap, String id, ModLoadOption mod) throws ModSolvingError {
		ModLoadOption existing = modMap.put(id, mod);
		if (existing != null && existing != mod) {
			throw new ModSolvingError(
				"The mod '" + id + "' is already added by " + existing + " when adding " + mod + "!"
			);
		}
	}

	private static <K, V> void putHierarchy(K key, V value, Map<Class<?>, Map<K, V>> to) {
		Class<?> cls = key.getClass();
		putHierarchy0(cls, key, value, to);
	}

	private static <K, V> void putHierarchy0(Class<?> cls, K key, V value, Map<Class<?>, Map<K, V>> to) {
		if (cls == null) {
			return;
		}

		to.computeIfAbsent(cls, c -> new HashMap<>()).put(key, value);

		putHierarchy0(cls.getSuperclass(), key, value, to);

		for (Class<?> itf : cls.getInterfaces()) {
			putHierarchy0(itf, key, value, to);
		}
	}

	private static <O> LoadOptionResult<O> createLoadResult(Class<O> cls, Map<?, Boolean> map) {

		Map<O, Boolean> resultMap = new HashMap<>();
		for (Entry<?, Boolean> entry : map.entrySet()) {
			resultMap.put(cls.cast(entry.getKey()), entry.getValue());
		}
		return new LoadOptionResult<>(Collections.unmodifiableMap(resultMap));
	}

	private void loadPlugin(ModLoadOption from) throws ModSolvingError {

		ModMetadataExt metadata = from.metadata();

		QuiltPluginContextImpl oldPlugin = pluginsById.remove(metadata.id());
		final Map<String, LoaderValue> data;

		if (oldPlugin != null) {

			WeakReference<QuiltPluginClassLoader> classloaderRef = new WeakReference<>(oldPlugin.classLoader);

			plugins.remove(oldPlugin.plugin);
			data = oldPlugin.unload();
			pluginsByPackage.keySet().removeAll(oldPlugin.classLoader.loadablePackages);
			// TODO: Unload ModLoadOptions and Rules!
			oldPlugin = null;

			// Just for verification
			// TODO: Actually test this properly!
			System.gc();
			if (classloaderRef.get() != null) {
				throw new IllegalStateException("Classloader not collected!");
			}
		} else {
			data = Collections.emptyMap();
		}

		if (metadata.plugin() == null) {
			// No plugin replaces the old
			// TODO: Log this! It's quite worrying...
			return;
		}

		try {
			QuiltPluginContextImpl pluginCtx = new QuiltPluginContextImpl(this, from, data);

			plugins.put(pluginCtx.plugin, pluginCtx);
			pluginsById.put(metadata.id(), pluginCtx);
			for (String pkg : pluginCtx.classLoader.loadablePackages) {
				pluginsByPackage.put(pkg, pluginCtx.classLoader);
			}

			for (Path folder : modFolders.keySet().toArray(new Path[0])) {
				pluginCtx.plugin.onModFolderAdded(folder);
			}

			for (PathLoadState loadState : modPaths.values()) {
				List<ModLoadOption> fromQuilt = loadState.getLoadedBy(theQuiltPluginContext.pluginId);
				if (fromQuilt != null && fromQuilt.size() > 0) {
					continue;
				}

				QuiltStatusNode guiNode = modPathGuiNodes.get(loadState.path);

				if (loadState instanceof PathLoadState.Folder) {
					scanFolderWithPlugin(loadState, pluginCtx, guiNode);
				} else if (loadState instanceof PathLoadState.Zip) {
					scanZipWithPlugin(((PathLoadState.Zip) loadState).insideZipRoot, loadState, pluginCtx, guiNode);
				} else if (loadState instanceof PathLoadState.UnknownFile) {
					scanUnknownFileWithPlugin(loadState, pluginCtx, guiNode);
				}
			}

		} catch (ReflectiveOperationException e) {
			throw new ModSolvingError(
				"Failed to load the plugin '" + metadata.id() + "' from " + describePath(from.from()), e
			);
		}
	}

	private static void forceGcButBadly() {
		// What part of this is badly?
		// Shh, don't check git log
		System.gc();
		System.gc();
		System.gc();
		System.gc();
	}

	// #########
	// # Tasks #
	// #########

	<V> QuiltPluginTask<V> submit(BasePluginContext ctx, Callable<V> task) {
		throw new AbstractMethodError("// TODO: Implement plugin tasks!");
	}

	<V> QuiltPluginTask<V> submitAfter(BasePluginContext ctx, Callable<V> task, QuiltPluginTask<?>... deps) {
		throw new AbstractMethodError("// TODO: Implement plugin tasks!");
	}

	// ########
	// # Mods #
	// ########

	boolean addModFolder(Path path, BasePluginContext ctx) {
		boolean added = modFolders.putIfAbsent(path, ctx.pluginId) == null;
		if (added) {
			scanModFolder(path, ctx.pluginId);
		}
		return added;
	}

	void scanModFolder(Path path, String pluginSrc) {

		QuiltStatusNode folderRoot = guiFileRoot.addChild(QuiltLoaderText.of(describePath(path)));
		folderRoot.icon(QuiltLoaderGui.iconFolder());
		folderRoot.addChild(QuiltLoaderText.translate("gui.text.loaded_by_plugin", pluginSrc)).level(QuiltWarningLevel.DEBUG_ONLY);

		for (QuiltLoaderPlugin plugin : plugins.keySet()) {
			plugin.onModFolderAdded(path);
		}

		if (config.singleThreadedLoading) {
			scanModFolder0(path, folderRoot);
		} else {
			executor.submit(() -> {
				scanModFolder0(path, folderRoot);
			});
		}
	}

	protected boolean isTest() {
		return false;
	}

	private void scanModFolder0(Path path, QuiltStatusNode guiNode) {
		try {
			Map<Path, QuiltStatusNode> guiNodeMap = new HashMap<>();
			guiNodeMap.put(path, guiNode);

			int maxDepth = config.loadSubFolders ? Integer.MAX_VALUE : 2;
			Set<FileVisitOption> fOptions = Collections.singleton(FileVisitOption.FOLLOW_LINKS);
			Files.walkFileTree(path, fOptions, maxDepth, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = path.relativize(dir);
					int count = relative.getNameCount();
					if (isTest() && dir.getFileName().toString().endsWith(".jar")) {
						ModLocationImpl location = new ModLocationImpl(false, true);
						// Tests use mods-as-folders to allow them to be entered into the git history properly
						scanModFile(dir, location, guiNode.addChild(QuiltLoaderText.of(dir.getFileName().toString())));
						return FileVisitResult.SKIP_SUBTREE;
					}
					if (count > 0) {
						String name = relative.getFileName().toString();

						Path parent = dir.getParent();
						final QuiltStatusNode node;
						if (dir == path) {
							node = guiNode;
						} else {
							QuiltStatusNode pNode = guiNodeMap.get(parent);

							if (pNode != null) {
								node = pNode.addChild(QuiltLoaderText.of(name), SortOrder.ALPHABETICAL_ORDER);
							} else {
								node = guiNode.addChild(QuiltLoaderText.translate("gui.prefix.no_parent_dir", name), SortOrder.ALPHABETICAL_ORDER);
							}
						}

						node.icon(QuiltLoaderGui.iconFolder());

						guiNodeMap.put(dir, node);

						if (!dir.equals(path) && !config.loadSubFolders) {
							node.icon(node.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
							node.addChild(QuiltLoaderText.translate("warn.sub_folders_disabled"))//
								.level(QuiltWarningLevel.WARN)
								.icon(QuiltLoaderGui.iconDisabled());
							return FileVisitResult.SKIP_SUBTREE;
						}

						char first = name.isEmpty() ? ' ' : name.charAt(0);
						if ('0' <= first && first <= '9' || V1ModMetadataReader.isConstraintCharacter(first)) {
							// Might be a game version
							if (config.restrictGameVersions && gameVersion != null) {
								// TODO: Support "1.12.x" type version parsing...
								for (String sub : name.split(" ")) {
									if (sub.isEmpty()) {
										continue;
									}

									char c = sub.charAt(0);

									if ('0' <= c && c <= '9') {
										sub = "=" + sub;
									}

									try {
										if (V1ModMetadataReader.readVersionSpecifier(sub).isSatisfiedBy(gameVersion)) {
											node.icon(node.icon().withDecoration(QuiltLoaderGui.iconTick()));
											node.addChild(QuiltLoaderText.translate("gui.text.game_version_match", gameVersion))
												.icon(QuiltLoaderGui.iconTick());
										} else {
											node.icon(node.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
											node.addChild(QuiltLoaderText.translate("gui.text.game_version_mismatch", gameVersion))
												.level(QuiltWarningLevel.INFO)//
												.icon(QuiltLoaderGui.iconDisabled());
											return FileVisitResult.SKIP_SUBTREE;
										}
									} catch (VersionFormatException e) {
										Log.warn(LogCategory.DISCOVERY, "Invalid game version specifier '" + sub + "'", e);
										node.level(QuiltWarningLevel.WARN);
										node.addChild(QuiltLoaderText.translate("warn.invalid_version_specifier", e.getMessage()))
											.level(QuiltWarningLevel.WARN);
										return FileVisitResult.SKIP_SUBTREE;
									}
								}
							} else {
								node.icon(node.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
								node.addChild(QuiltLoaderText.translate("gui.text.game_versions_disabled"))
									.level(QuiltWarningLevel.WARN)//
									.icon(QuiltLoaderGui.iconDisabled());
								return FileVisitResult.SKIP_SUBTREE;
							}
						}

						if (name.endsWith(".disabled")) {
							node.icon(node.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
							return FileVisitResult.SKIP_SUBTREE;
						}

						if (name.startsWith(".")) { // ignore dot-folders
							node.icon(node.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
							return FileVisitResult.SKIP_SUBTREE;
						}

						if (Files.exists(dir.resolve("quilt_loader_ignored"))) {
							node.icon(node.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
							node.addChild(QuiltLoaderText.translate("warn.sub_folder_ignored"))
								.level(QuiltWarningLevel.WARN)//
								.icon(QuiltLoaderGui.iconDisabled());
							return FileVisitResult.SKIP_SUBTREE;
						}
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					Path parent = file.getParent();
					QuiltStatusNode pNode = guiNodeMap.get(parent);
					final QuiltStatusNode node;

					String name = file.getFileName().toString();

					if (pNode != null) {
						node = pNode.addChild(QuiltLoaderText.of(name), SortOrder.ALPHABETICAL_ORDER);
					} else {
						node = guiNode.addChild(QuiltLoaderText.translate("gui.prefix.no_parent_file"), SortOrder.ALPHABETICAL_ORDER);
					}

					scanModFile(file, new ModLocationImpl(false, true), node);
					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException io) {
			throw new RuntimeException(io);
		}
	}

	void scanFolderAsMod(Path folder, ModLocationImpl location, QuiltStatusNode guiNode) {
		PathLoadState loadState = modPaths.computeIfAbsent(folder, f -> new PathLoadState.Folder(folder, location));
		boolean isQuilt = false;

		for (BasePluginContext ctx : plugins.values()) {

			final List<ModLoadOption> list;
			list = scanFolderWithPlugin(loadState, ctx, guiNode);

			if (!list.isEmpty() && ctx == theQuiltPlugin.context()) {
				isQuilt = true;
				break;
			}
		}

		if (!isQuilt) {
			try {
				loadState.unsupportedType = UnsupportedModChecker.checkFolder(folder);
			} catch (IOException e) {
				// TODO: Proper error handling!
				throw new Error("Failed to check " + describePath(folder) + " as an unsupported mod!", e);
			}
		}
	}

	private List<ModLoadOption> scanFolderWithPlugin(PathLoadState loadState, BasePluginContext ctx, QuiltStatusNode guiNode) {
		ModLoadOption[] mods;
		try {
			mods = ctx.plugin().scanFolder(loadState.path, loadState.location, guiNode);
		} catch (IOException e) {
			// FOR NOW
			// TODO: Proper error handling!
			throw new Error("The plugin '" + ctx.pluginId() + "' failed to load " + describePath(loadState.path), e);
		}
		final List<ModLoadOption> list;
		if (mods != null && mods.length > 0) {
			list = Collections.unmodifiableList(Arrays.asList(mods.clone()));
			for (ModLoadOption mod : mods) {
				addSingleModOption0(mod, ctx, mods.length == 1, guiNode);
			}
		} else {
			list = Collections.emptyList();
		}
		loadState.add(this, ctx, list);
		return list;
	}

	void scanModFile(Path file, ModLocationImpl location, QuiltStatusNode guiNode) {

		// We only propose a file as a possible mod in the following scenarios:
		// General: must not end with ".disabled".
		// Some OSes generate metadata so consider the following:
		// - UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`.
		// - MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the form of `.mod.jar`

		// Note that we only perform name-based checks here - the "isHidden" check is in "scanModFile" since it might
		// require opening the file's metadata

		String fileName = file.getFileName().toString();
		guiNode.icon(QuiltLoaderGui.iconUnknownFile());

		if (fileName.startsWith(".")) {
			guiNode.icon(guiNode.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
			guiNode.sortPrefix("disabled");
			guiNode.addChild(QuiltLoaderText.translate("gui.text.file_hidden_dot_prefixed"))// TODO translate
				.icon(QuiltLoaderGui.iconDisabled());
			return;
		} else if (fileName.endsWith(".disabled")) {
			guiNode.icon(guiNode.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
			guiNode.sortPrefix("disabled");
			return;
		}

		if (config.singleThreadedLoading) {
			scanModFile0(file, location, guiNode);
		} else {
			executor.submit(() -> {
				scanModFile0(file, location, guiNode);
			});
		}
	}

	private void scanModFile0(Path file, ModLocationImpl location, QuiltStatusNode guiNode) {

		modPathGuiNodes.put(file, guiNode);

		try {
			if (Files.isHidden(file)) {
				guiNode.sortPrefix("disabled");
				guiNode.icon(guiNode.icon().withDecoration(QuiltLoaderGui.iconDisabled()));
				guiNode.addChild(QuiltLoaderText.translate("gui.text.file_hidden"));// TODO translate
				return;
			}
		} catch (IOException e) {

			QuiltLoaderText title = QuiltLoaderText.translate("gui.text.ioexception_files_hidden", e.getMessage());
			QuiltDisplayedError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Failed to check if " + describePath(file) + " is hidden or not!");
			error.appendDescription(
				QuiltLoaderText.translate("gui.text.ioexception_files_hidden.desc.0", describePath(file))
			);
			error.appendThrowable(e);

			guiNode.addChild(title).level(QuiltWarningLevel.ERROR);
			e.printStackTrace();

			return;
		}

		if (FasterFiles.isDirectory(file)) {
			if (this.config.singleThreadedLoading) {
				scanFolderAsMod(file, location, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanFolderAsModTask(file, location, guiNode));
			}
			return;
		}

		try {
			Path zipRoot = loadZip0(file);
			if (file.getFileName().toString().endsWith(".jar")) {
				guiNode.icon(QuiltLoaderGui.iconJarFile());
			} else {
				guiNode.icon(QuiltLoaderGui.iconZipFile());
			}

			if (this.config.singleThreadedLoading) {
				scanZip(file, zipRoot, location, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanZipTask(file, zipRoot, location, guiNode));
			}

		} catch (ZeroByteFileException e) {

			QuiltLoaderText title = QuiltLoaderText.translate("gui.error.zerobytezip.title");
			QuiltDisplayedError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Encountered zero byte file " + describePath(file) + "!");
			error.appendDescription(QuiltLoaderText.translate("gui.error.zerobytezip.desc.0"));
			error.appendDescription(QuiltLoaderText.of(describePath(file)));
			error.appendThrowable(e);
			getRealContainingFile(file).ifPresent(real -> {
				error.addFileViewButton(real).icon(QuiltLoaderGui.iconZipFile());
			});

			guiNode.addChild(QuiltLoaderText.translate("gui.error.zerobytezip")).level(QuiltWarningLevel.ERROR);

		} catch (ZipException e) {

			// TODO: check for common cases and print those
			// (I.E zero-byte file)

			QuiltLoaderText title = QuiltLoaderText.translate("gui.error.zipexception.title", e.getMessage());
			QuiltDisplayedError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Failed to unzip " + describePath(file) + "!");
			error.appendDescription(QuiltLoaderText.translate("gui.error.zipexception.desc.0", describePath(file)));
			error.appendDescription(QuiltLoaderText.translate("gui.error.zipexception.desc.1"));
			error.appendThrowable(e);
			getRealContainingFile(file).ifPresent(real -> {
				error.addFileViewButton(real).icon(QuiltLoaderGui.iconZipFile());
			});

			guiNode.addChild(QuiltLoaderText.translate("gui.error.zipexception", e.getMessage()))// TODO: translate
				.level(QuiltWarningLevel.ERROR);

		} catch (IOException e) {

			QuiltLoaderText title = QuiltLoaderText.translate("gui.error.ioexception.title", e.getMessage());
			QuiltDisplayedError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Failed to read " + describePath(file) + "!");
			error.appendDescription(QuiltLoaderText.translate("gui.error.ioexception.desc.0", describePath(file)));
			error.appendThrowable(e);
			getRealContainingFile(file).ifPresent(real -> {
				error.addFileViewButton(real).icon(QuiltLoaderGui.iconZipFile());
			});

			guiNode.addChild(QuiltLoaderText.translate("gui.error.ioexception", e.getMessage()))// TODO: translate
				.level(QuiltWarningLevel.ERROR);

		} catch (NonZipException e) {

			guiNode.icon(QuiltLoaderGui.iconUnknownFile());

			if (this.config.singleThreadedLoading) {
				scanUnknownFile(file, location, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanUnknownFileTask(file, location, guiNode));
			}
		}
	}

	/** Called by {@link MainThreadTask.ScanZipTask} */
	void scanZip(Path zipFile, Path zipRoot, ModLocationImpl location, QuiltStatusNode guiNode) {

		try {
			state.push(guiNode);

			PathLoadState loadState = modPaths.computeIfAbsent(zipFile, f -> new PathLoadState.Zip(zipFile, location, zipRoot));

			boolean isQuilt = false;

			for (BasePluginContext ctx : plugins.values()) {
				List<ModLoadOption> list = scanZipWithPlugin(zipRoot, loadState, ctx, guiNode);
				if (!list.isEmpty() && ctx == theQuiltPlugin.context()) {
					isQuilt = true;
					break;
				}
			}

			if (!isQuilt) {
				try {
					loadState.unsupportedType = UnsupportedModChecker.checkZip(zipFile, zipRoot);
				} catch (IOException e) {
					// TODO: Proper error handling!
					throw new Error("Failed to check " + describePath(zipFile) + " as an unsupported mod!", e);
				}
			}

		} finally {
			state.pop();
		}
	}

	private List<ModLoadOption> scanZipWithPlugin(Path zipRoot, PathLoadState loadState, BasePluginContext ctx, QuiltStatusNode guiNode) {
		ModLoadOption[] mods;
		try {
			mods = ctx.plugin().scanZip(zipRoot, loadState.location, guiNode);
		} catch (IOException e) {
			// FOR NOW
			// TODO: Proper error handling!
			throw new Error(
				"The plugin '" + ctx.pluginId() + "' failed to load '" + describePath(loadState.path) + "'", e
			);
		}

		final List<ModLoadOption> list;

		if (mods != null && mods.length > 0) {
			list = Collections.unmodifiableList(Arrays.asList(mods.clone()));
			for (ModLoadOption mod : mods) {
				addSingleModOption0(mod, ctx, mods.length == 1, guiNode);
			}
		} else {
			list = Collections.emptyList();
		}
		loadState.add(this, ctx, list);
		return list;
	}

	/** Called by {@link MainThreadTask.ScanUnknownFileTask} */
	void scanUnknownFile(Path file, ModLocationImpl location, QuiltStatusNode guiNode) {

		try {
			state.push(guiNode);

			PathLoadState loadState = modPaths.computeIfAbsent(file, f -> new PathLoadState.UnknownFile(file, location));
			boolean isQuilt = false;

			for (BasePluginContext ctx : plugins.values()) {
				List<ModLoadOption> list = scanUnknownFileWithPlugin(loadState, ctx, guiNode);
				if (!list.isEmpty() && ctx == theQuiltPlugin.context()) {
					isQuilt = true;
					break;
				}
			}

			if (!isQuilt) {
				try {
					loadState.unsupportedType = UnsupportedModChecker.checkUnknownFile(file);
				} catch (IOException e) {
					// TODO: Proper error handling!
					throw new Error("Failed to check " + describePath(file) + " as an unsupported mod!", e);
				}
			}
		} finally {
			state.pop();
		}
	}

	private List<ModLoadOption> scanUnknownFileWithPlugin(PathLoadState loadState, BasePluginContext ctx, QuiltStatusNode guiNode) {

		Path file = loadState.path;
		ModLoadOption[] mods;
		try {
			mods = ctx.plugin().scanUnknownFile(file, loadState.location, guiNode);
		} catch (IOException e) {
			// FOR NOW
			// TODO: Proper error handling!
			throw new Error("The plugin '" + ctx.pluginId() + "' failed to load " + describePath(file), e);
		}

		final List<ModLoadOption> list;
		if (mods != null && mods.length > 0) {
			list = Collections.unmodifiableList(Arrays.asList(mods.clone()));
			for (ModLoadOption mod : mods) {
				addSingleModOption0(mod, ctx, mods.length == 1, guiNode);
			}
		} else {
			list = Collections.emptyList();
		}
		loadState.add(this, ctx, list);
		return list;
	}

	void addSingleModOption(ModLoadOption mod, BasePluginContext provider, boolean only, QuiltStatusNode guiNode) {
		Path from = mod.from();
		addSingleModOption0(mod, provider, only, guiNode);

		if (mod instanceof AliasedLoadOption) {
			addLoadOption(mod, provider);
		} else {
			PathLoadState loadState = modPaths.computeIfAbsent(from, f -> new PathLoadState.ExtraMod(from));
			loadState.add(this, provider, Collections.singletonList(mod));
		}
	}

	private void addSingleModOption0(ModLoadOption mod, BasePluginContext provider, boolean only, QuiltStatusNode guiNode) {
		String id = mod.id();
		Version version = mod.version();
		Path from = mod.from();

		PotentialModSet set = modIds.computeIfAbsent(id, k -> new PotentialModSet());

		ModLoadOption current = set.byVersionSingles.get(version);
		if (current != null && current.isMandatory() && mod.isMandatory() && current.getClass() == mod.getClass() && QuiltLoader.isDevelopmentEnvironment()) {
			Log.warn(LogCategory.SOLVING, String.format("Ignoring duplicate mod %s of the same version %s loaded from %s", id, version, from));
			return;
		}

		List<ModLoadOption> already = set.byVersionAll.computeIfAbsent(version, v -> new ArrayList<>());
		already.add(mod);
		set.all.add(mod);

		if (already.size() == 1) {
			set.byVersionSingles.put(version, mod);
		} else {
			set.byVersionSingles.remove(version);

			set.extras.addAll(already);
		}

		if (!(mod instanceof TentativeLoadOption) && mod.metadata().plugin() != null) {
			pluginIdsChanged |= idsWithPlugins.add(id);
		}

		modProviders.put(mod, provider.pluginId());
		modGuiNodes.put(mod, guiNode);

		QuiltStatusNode loadedBy = guiNode.addChild(QuiltLoaderText.translate("gui.text.loaded_by_plugin", provider.pluginId()));
		loadedBy.icon(mod.modTypeIcon());

		if (only) {
			guiNode.icon(guiNode.icon().withDecoration(mod.modTypeIcon()));
			loadedBy.level(QuiltWarningLevel.DEBUG_ONLY);
		}

		guiNode.addChild(QuiltLoaderText.translate("gui.text.id", id));
		guiNode.addChild(QuiltLoaderText.translate("gui.text.version", version.raw()));
	}

	// ###############
	// # Sat4j Rules #
	// ###############

	void addLoadOption(LoadOption option, BasePluginContext provider) {
		solver.addOption(option);

		if (option instanceof TentativeLoadOption) {
			tentativeLoadOptions.put((TentativeLoadOption) option, provider);
		}

		for (QuiltLoaderPlugin plugin : plugins.keySet()) {
			plugin.onLoadOptionAdded(option);
		}
	}

	void removeLoadOption(LoadOption option) {
		solver.removeOption(option);

		for (QuiltLoaderPlugin plugin : plugins.keySet()) {
			plugin.onLoadOptionRemoved(option);
		}
	}

	void addRule(Rule rule) {
		solver.addRule(rule);
	}

	void removeRule(Rule rule) {
		solver.removeRule(rule);
	}
}
