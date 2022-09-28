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

package org.quiltmc.loader.impl.plugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
import org.quiltmc.loader.api.plugin.NonZipException;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginError;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.QuiltPluginTask;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
import org.quiltmc.loader.api.plugin.gui.Text;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult.SpecificLoadOptionResult;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.TentativeLoadOption;
import org.quiltmc.loader.impl.QuiltLoaderConfig;
import org.quiltmc.loader.impl.QuiltLoaderConfig.ZipLoadType;
import org.quiltmc.loader.impl.VersionConstraintImpl;
import org.quiltmc.loader.impl.discovery.ClasspathModCandidateFinder;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedPath;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.plugin.base.InternalModContainerBase;
import org.quiltmc.loader.impl.plugin.fabric.StandardFabricPlugin;
import org.quiltmc.loader.impl.plugin.gui.TempQuilt2OldStatusNode;
import org.quiltmc.loader.impl.plugin.gui.TextImpl;
import org.quiltmc.loader.impl.plugin.quilt.MandatoryModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.ModIdDefinition;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleBreak;
import org.quiltmc.loader.impl.plugin.quilt.QuiltRuleDep;
import org.quiltmc.loader.impl.plugin.quilt.StandardQuiltPlugin;
import org.quiltmc.loader.impl.report.QuiltReport;
import org.quiltmc.loader.impl.report.QuiltReportedError;
import org.quiltmc.loader.impl.report.QuiltStringSection;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl.LoadOptionResult;
import org.quiltmc.loader.impl.solver.Sat4jWrapper;
import org.quiltmc.loader.util.sat4j.specs.TimeoutException;

/** The main manager for loader plugins, and the mod finding process in general.
 * <p>
 * Unlike {@link QuiltLoader} itself, it does make sense to have multiple of these at once: one for loading plugins that
 * will be used, and many more for "simulating" mod loading. */
public class QuiltPluginManagerImpl implements QuiltPluginManager {

	private static final String QUILT_ID = "quilt_loader";

	public final boolean simulationOnly;
	public final QuiltLoaderConfig config;
	final GameProvider game;
	final Version gameVersion;

	private final Path modsDir;
	final Map<Path, Path> pathParents = new HashMap<>();
	final Map<Path, String> customPathNames = new HashMap<>();
	final Map<String, Integer> allocatedFileSystemIndices = new HashMap<>();

	final Map<Path, String> modFolders = new LinkedHashMap<>();
	final Map<Path, ModLoadOption> modPaths = new LinkedHashMap<>();
	final Map<ModLoadOption, String> modProviders = new HashMap<>();
	final Map<String, PotentialModSet> modIds = new LinkedHashMap<>();

	final Map<TentativeLoadOption, BasePluginContext> tentativeLoadOptions = new LinkedHashMap<>();

	private final StandardQuiltPlugin theQuiltPlugin;
	final BuiltinPluginContext theQuiltPluginContext;
	private final StandardFabricPlugin theFabricPlugin;
	private final BuiltinPluginContext theFabricPluginContext;

	final Map<QuiltLoaderPlugin, BasePluginContext> plugins = new LinkedHashMap<>();
	final Map<String, QuiltPluginContextImpl> pluginsById = new HashMap<>();
	final Map<String, QuiltPluginClassLoader> pluginsByPackage = new HashMap<>();

	/** Every mod id that contained a plugin, at any point. Used to scan for plugins at the start of each cycle. */
	final Set<String> idsWithPlugins = new HashSet<>();

	final Sat4jWrapper solver = new Sat4jWrapper();

	/** Set to null if {@link QuiltLoaderConfig#singleThreadedLoading} is true, otherwise this will be a useful
	 * value. */
	private final ExecutorService executor;

	final Queue<MainThreadTask> mainThreadTasks;

	/** The root tree node for the "files" tab. */
	public final TempQuilt2OldStatusNode guiFileRoot = new TempQuilt2OldStatusNode(null);
	private PluginGuiTreeNode guiNodeModsFromPlugins;
	final Map<ModLoadOption, PluginGuiTreeNode> modGuiNodes = new HashMap<>();
	final List<QuiltPluginErrorImpl> errors = new ArrayList<>();

	/** Only written by {@link #runSingleCycle()}, only read during crash report generation. */
	private PerCycleStep perCycleStep;

	/** Only written by {@link #runInternal(boolean)}, only read during crash report generation. */
	private int cycleNumber = 0;

	// TEMP
	final Deque<PluginGuiTreeNode> state = new ArrayDeque<>();

	public QuiltPluginManagerImpl(Path modsDir, GameProvider game, QuiltLoaderConfig options) {
		this(modsDir, game, false, options);
	}

	public QuiltPluginManagerImpl(Path modsDir, GameProvider game, boolean simulationOnly, QuiltLoaderConfig config) {
		this.simulationOnly = simulationOnly;
		this.game = game;
		gameVersion = game == null ? null : Version.of(game.getNormalizedGameVersion());
		this.config = config;
		this.modsDir = modsDir;

		this.executor = config.singleThreadedLoading ? null : Executors.newCachedThreadPool();
		this.mainThreadTasks = config.singleThreadedLoading ? new ArrayDeque<>() : new ConcurrentLinkedQueue<>();

		customPathNames.put(modsDir, "<mods>");

		mainThreadTasks.add(new MainThreadTask.ScanFolderTask(modsDir, QUILT_ID));

		theQuiltPlugin = new StandardQuiltPlugin();
		theQuiltPluginContext = addBuiltinPlugin(theQuiltPlugin, QUILT_ID);
		theFabricPlugin = new StandardFabricPlugin();
		theFabricPluginContext = addBuiltinPlugin(theFabricPlugin, "quilted_fabric_loader");
	}

	private BuiltinPluginContext addBuiltinPlugin(BuiltinQuiltPlugin plugin, String id) {
		BuiltinPluginContext ctx = new BuiltinPluginContext(this, id, plugin);
		plugin.load(ctx, Collections.emptyMap());
		plugins.put(plugin, ctx);
		plugin.addModFolders(ctx.modFolderSet);
		return ctx;
	}

	// #######
	// Loading
	// #######

	@Override
	public QuiltPluginTask<Path> loadZip(Path zip) {
		return submit(null, () -> loadZip0(zip));
	}

	private Path loadZip0(Path zip) throws IOException, NonZipException {
		try {
			// Cast to ClassLoader since newer versions of java added a conflicting method
			// Java 8 - just "newFileSystem(Path, ClassLoader)"
			// Java 13 - added "newFileSystem(Path, Map)"
			FileSystem fileSystem = FileSystems.newFileSystem(zip, (ClassLoader) null);

			for (Path root : fileSystem.getRootDirectories()) {
				// FIXME: find out if this is an inner or outer zip!
				ZipLoadType loadType = config.innerZipLoadType;
				switch (loadType) {
					case COPY_TO_MEMORY: {
						String name = allocateFileSystemName(zip);
						Path qRoot = new QuiltMemoryFileSystem.ReadOnly(name, root).getRoot();
						pathParents.put(qRoot, zip);
						return qRoot;
					}
					case COPY_ZIP:
						throw new UnsupportedOperationException();
					case READ_ZIP: {
						pathParents.put(root, zip);
						return root;
					}
					default: {
						throw new IllegalStateException("Unknown ZipLoadType " + loadType);
					}
				}
			}

			throw new IOException("No root directories found in " + describePath(zip));

		} catch (ProviderNotFoundException e) {
			throw new NonZipException(e);
		}
	}

	private synchronized String allocateFileSystemName(Path from) {
		String rawName = from.getFileName().toString();
		Integer current = allocatedFileSystemIndices.get(rawName);
		if (current == null) {
			current = 1;
		} else {
			current++;
		}
		allocatedFileSystemIndices.put(rawName, current);
		return rawName + ".i" + current;

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
	public Path getRealContainingFile(Path file) {
		Path next = file;
		while (next.getFileSystem() != FileSystems.getDefault()) {
			next = getParent(next);
			if (next == null) {
				return null;
			}
		}
		return next;
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
		return new QuiltJoinedFileSystem(QuiltJoinedFileSystem.uniqueOf(name), copiedPaths).getRoot();
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
	public @Nullable String getModProvider(Path mod) {
		return modProviders.get(getModLoadOption(mod));
	}

	@Override
	public @Nullable ModLoadOption getModLoadOption(Path file) {
		return modPaths.get(file);
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

	// #######
	// # Gui #
	// #######

	@Override
	public PluginGuiTreeNode getGuiNode(ModLoadOption mod) {
		return modGuiNodes.get(mod);
	}

	public QuiltPluginError reportError(BasePluginContext reporter, Text title) {
		QuiltPluginErrorImpl error = new QuiltPluginErrorImpl(reporter.pluginId, title);
		errors.add(error);
		return error;
	}

	// ############
	// # Internal #
	// ############

	public PluginGuiTreeNode getModsFromPluginsGuiNode() {
		if (guiNodeModsFromPlugins == null) {
			guiNodeModsFromPlugins = guiFileRoot.addChild(Text.translate("gui.text.floating_mods_from_plugins"));
		}
		return guiNodeModsFromPlugins;
	}

	public List<QuiltPluginErrorImpl> getErrors() {
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
			report.addStacktraceSection("ModSolvingError", -100, e);

			break outer;
		} catch (TreeContainsModError e) {
			report = new QuiltReport("Quilt Loader: Load Error Report");
			break outer;
		} catch (TimeoutException e) {
			e.printStackTrace();
			report = new QuiltReport("Quilt Loader: Load Failed Report");
			report.addStacktraceSection("TimeoutException", -100, e);
			break outer;
		} catch (QuiltReportedError e) {
			report = e.report;
			break outer;
		} catch (Throwable t) {
			t.printStackTrace();
			report = new QuiltReport("Quilt Loader: Crash Report");
			report.addStacktraceSection("Unhandled Exception", -100, t);
			break outer;
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

		QuiltStringSection modTable = report.addStringSection("Mod Table", 100);
		appendModTable(modTable::lines);
		modTable.setShowInLogs(false);

		QuiltStringSection modDetails = report.addStringSection("Mod Details", 100);
		appendModDetails(modDetails::lines);
		modDetails.setShowInLogs(false);

		throw new QuiltReportedError(report);
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

		int maxNameLength = "Mod".length();
		int maxIdLength = "ID".length();
		int maxVersionLength = "Version".length();
		int maxPluginLength = "Plugin".length();
		List<Integer> maxSourcePathLengths = new ArrayList<>();

		List<ModLoadOption> mods = new ArrayList<>();
		Map<ModLoadOption, List<List<Path>>> sourcePathMap = new HashMap<>();

		for (PotentialModSet set : this.modIds.values()) {
			mods.addAll(set.all);
		}

		for (ModLoadOption mod : mods) {
			maxNameLength = Math.max(maxNameLength, mod.metadata().name().length());
			maxIdLength = Math.max(maxIdLength, mod.metadata().id().length());
			maxVersionLength = Math.max(maxVersionLength, mod.metadata().version().toString().length());
			maxPluginLength = Math.max(maxPluginLength, mod.loader().pluginId().length());

			List<List<Path>> sourcePaths = InternalModContainerBase.walkSourcePaths(this, mod.from());
			sourcePathMap.put(mod, sourcePaths);

			for (List<Path> paths : sourcePaths) {
				for (int i = 0; i < paths.size(); i++) {
					Path path = paths.get(i);
					String pathStr = path.startsWith(modsDir) ? "<mods>/" + modsDir.relativize(path).toString() : path.toString();
					if (maxSourcePathLengths.size() <= i) {
						int old = (i == 0 ? "File(s)" : "Sub-Files").length();
						maxSourcePathLengths.add(Math.max(old, pathStr.length() + 1));
					} else {
						Integer old = maxSourcePathLengths.get(i);
						maxSourcePathLengths.set(i, Math.max(old, pathStr.length() + 1));
					}
				}
			}
		}

		maxIdLength++;
		maxVersionLength++;
		maxPluginLength++;

		StringBuilder sbTab = new StringBuilder();
		StringBuilder sbSep = new StringBuilder();

		// Table header
		sbTab.append("| Mod ");
		sbSep.append("|-----");
		for (int i = "Mod".length(); i < maxNameLength; i++) {
			sbTab.append(" ");
			sbSep.append("-");
		}
		sbTab.append("| ID ");
		sbSep.append("|----");
		for (int i = "ID".length(); i < maxIdLength; i++) {
			sbTab.append(" ");
			sbSep.append("-");
		}
		sbTab.append("| Version ");
		sbSep.append("|---------");
		for (int i = "Version".length(); i < maxVersionLength; i++) {
			sbTab.append(" ");
			sbSep.append("-");
		}
		sbTab.append("| Plugin ");
		sbSep.append("|--------");
		for (int i = "Plugin".length(); i < maxPluginLength; i++) {
			sbTab.append(" ");
			sbSep.append("-");
		}
		sbTab.append("|");
		sbSep.append("|");

		String start = "File(s)";

		for (int len : maxSourcePathLengths) {
			sbTab.append(" ").append(start);
			for (int i = start.length(); i <= len; i++) {
				sbTab.append(" ");
			}
			for (int i = -1; i <= len; i++) {
				sbSep.append("-");
			}
			sbTab.append("|");
			sbSep.append("|");
			start = "Sub-Files";
		}

		to.accept(sbTab.toString());
		sbTab.setLength(0);
		to.accept(sbSep.toString());

		for (ModLoadOption mod : mods.stream().sorted(Comparator.comparing(i -> i.metadata().name())).collect(Collectors.toList())) {
			// - Index
			// - Name
			// - ID
			// - version
			// - loader plugin
			// - source path(s)
			sbTab.append("| ").append(mod.metadata().name());
			for (int i = mod.metadata().name().length(); i < maxNameLength; i++) {
				sbTab.append(" ");
			}
			sbTab.append(" | ").append(mod.metadata().id());
			for (int i = mod.metadata().id().length(); i < maxIdLength; i++) {
				sbTab.append(" ");
			}
			sbTab.append(" | ").append(mod.metadata().version());
			for (int i = mod.metadata().version().toString().length(); i < maxVersionLength; i++) {
				sbTab.append(" ");
			}
			sbTab.append(" | ").append(mod.loader().pluginId());
			for (int i = mod.loader().pluginId().length(); i < maxPluginLength; i++) {
				sbTab.append(" ");
			}

			List<List<Path>> allPaths = sourcePathMap.get(mod);

			for (int pathsIndex = 0; pathsIndex < allPaths.size(); pathsIndex++) {
				List<Path> paths = allPaths.get(pathsIndex);

				if (pathsIndex != 0) {
					to.accept(sbTab.toString());
					sbTab.setLength(0);
					sbTab.append("| ");
					for (int i = 0; i < "Index".length(); i++) {
						sbTab.append(" ");
					}
					sbTab.append(" | ");
					for (int i = 0; i < maxIdLength; i++) {
						sbTab.append(" ");
					}
					sbTab.append(" | ");
					for (int i = 0; i < maxVersionLength; i++) {
						sbTab.append(" ");
					}
					sbTab.append(" | ");
					for (int i = 0; i < maxPluginLength; i++) {
						sbTab.append(" ");
					}
				}

				for (int pathIndex = 0; pathIndex < maxSourcePathLengths.size(); pathIndex++) {
					sbTab.append(" | ");
					final String pathStr;
					if (pathIndex < paths.size()) {
						Path path = paths.get(pathIndex);
						pathStr = path.startsWith(modsDir) ? "<mods>/" + modsDir.relativize(path) : path.toString();
					} else {
						pathStr = "";
					}
					sbTab.append(pathStr);
					for (int i = pathStr.length(); i < maxSourcePathLengths.get(pathIndex); i++) {
						sbTab.append(" ");
					}
				}
				sbTab.append(" |");
			}
			to.accept(sbTab.toString());
			sbTab.setLength(0);
		}

		to.accept(sbSep.toString());
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
		Set<Path> rootFsPaths = Collections.newSetFromMap(new TreeMap<>());

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
				Path realParent = getParent(parent);
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
			to.accept(root.toString() + ": ");
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
			if (!on.versions().isEmpty()) {
				sb.append(" ");
				sb.append(on.versions().toString());
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

		List<String> boxLines = insideBox.get(modPaths.get(path));
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

		if (game != null) {
			theQuiltPlugin.addBuiltinMods(game);
		}

		if (scanClasspath) {
			scanClasspath();
		}

		for (int cycle = 0; cycle < 1000; cycle++) {
			this.cycleNumber = cycle + 1;
			ModSolveResultImpl result = runSingleCycle();
			checkForErrors();
			if (result != null) {
				return result;
			}
		}

		throw new ModSolvingError(
			"Too many cycles! 1000 cycles of plugin loading is a lot, since each one *could* take a second..."
		);
	}

	private void scanClasspath() {
		PluginGuiTreeNode classpathRoot = guiFileRoot.addChild(Text.translate("gui.text.classpath"));

		ClasspathModCandidateFinder.findCandidatesStatic((paths, ignored) -> {
			final Path path;
			if (paths.size() > 1) {
				path = new QuiltJoinedFileSystem(QuiltJoinedFileSystem.uniqueOf("classpath"), paths).getRoot();
			} else {
				path = paths.get(0);
			}

			if (Files.exists(path)) {
				String name = describePath(path);
				PluginGuiTreeNode clNode = classpathRoot.addChild(Text.of(name), SortOrder.ALPHABETICAL_ORDER);
				if (Files.isDirectory(path)) {
					clNode.mainIcon(clNode.manager().iconFolder());
					scanClasspathFolder(path, clNode);
				} else {
					scanModFile(path, true, clNode);
				}
			}
		});
	}

	private ModSolveResultImpl runSingleCycle() throws ModResolutionException, TimeoutException {

		PerCycleStep step = PerCycleStep.START;
		this.perCycleStep = step;

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

	private void handleSolverFailure() throws TimeoutException {

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
						reportSolverError(rules);
						solver.removeRule(blamed);
						reportError(theQuiltPluginContext, Text.translate("plugin.illegal_state.recovered_and_blambed", ctx.pluginId));
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
					reportSolverError(rules);
					solver.removeRule(blamed);
					continue solver_error_iteration;
				}
			}

			// No plugin blamed any rules
			// So we'll just pick one of them randomly and remove it.

			failed = true;
			reportSolverError(rules);

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

		if (failed) {
			// Okay, so we failed but we've reached the end of the list of problems
			// Just return here since the cycle handles this
			return;
		} else {
			// This is an odd state where we encountered an error,
			// but then found a solution, and DIDN'T exit from this method
			// so something has gone wrong internally.
			reportError(theQuiltPluginContext, Text.translate("solver.illegal_state.TODO"));
			return;
		}
	}

	private void reportSolverError(Collection<Rule> rules) {
		SolverErrorHelper.reportSolverError(this, rules);
	}

	/** Checks for any {@link WarningLevel#FATAL} or {@link WarningLevel#ERROR} gui nodes, and throws an exception if
	 * this is the case. */
	private void checkForErrors() throws TreeContainsModError, QuiltReportedError {

		if (!errors.isEmpty()) {
			QuiltReport report = new QuiltReport("Quilt Loader: Failed to load");

			int number = 1;

			for (QuiltPluginErrorImpl error : errors) {
				List<String> lines = new ArrayList<>();
				lines.addAll(error.reportLines);

				if (lines.isEmpty()) {
					lines.add("The plugin that created this error (" + error.reportingPlugin + ") forgot to call 'appendReportText'!");
					lines.add("The next stacktrace is where the plugin created the error, not the actual error.'");
					error.exceptions.add(0, error.reportTrace);
				}

				for (Throwable ex : error.exceptions) {
					lines.add("");
					StringWriter writer = new StringWriter();
					ex.printStackTrace(new PrintWriter(writer));
					Collections.addAll(lines, writer.toString().split("\n"));
				}

				report.addStringSection("Error " + number, -100, lines.toArray(new String[0]));
				number++;
			}

			throw new QuiltReportedError(report);
		}

		WarningLevel maximumLevel = guiFileRoot.getMaximumLevel();
		if (maximumLevel == WarningLevel.FATAL || maximumLevel == WarningLevel.ERROR) {
			throw new TreeContainsModError();
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
		List<LoadOption> solution = solver.getSolution();

		Map<String, ModLoadOption> directModsMap = new HashMap<>();
		Map<String, ModLoadOption> providedModsMap = new HashMap<>();
		Map<Class<?>, LoadOptionResult<?>> extraResults;
		final Map<Class<?>, Map<Object, Boolean>> optionMap = new HashMap<>();

		for (LoadOption option : solution) {

			boolean load = true;
			if (solver.isNegated(option)) {
				option = solver.negate(option);
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

		directModsMap = Collections.unmodifiableMap(directModsMap);
		providedModsMap = Collections.unmodifiableMap(providedModsMap);
		extraResults = Collections.unmodifiableMap(extraResults);

		return new ModSolveResultImpl(directModsMap, providedModsMap, extraResults);
	}

	private static void putMod(Map<String, ModLoadOption> modMap, String id, ModLoadOption mod) throws ModSolvingError {
		ModLoadOption existing = modMap.put(id, mod);
		if (existing != null) {
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

			pluginCtx.plugin.addModFolders(pluginCtx.modFolderSet);

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

		PluginGuiTreeNode folderRoot = guiFileRoot.addChild(Text.of(describePath(path)));
		folderRoot.mainIcon(guiFileRoot.manager().iconFolder());
		folderRoot.addChild(Text.translate("gui.text.loaded_by_plugin", pluginSrc)).debug();

		if (config.singleThreadedLoading) {
			scanModFolder0(path, folderRoot);
		} else {
			executor.submit(() -> {
				scanModFolder0(path, folderRoot);
			});
		}
	}

	private void scanModFolder0(Path path, PluginGuiTreeNode guiNode) {
		try {
			Map<Path, PluginGuiTreeNode> guiNodeMap = new HashMap<>();
			guiNodeMap.put(path, guiNode);

			int maxDepth = config.loadSubFolders ? Integer.MAX_VALUE : 2;
			Set<FileVisitOption> fOptions = Collections.singleton(FileVisitOption.FOLLOW_LINKS);
			Files.walkFileTree(path, fOptions, maxDepth, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = path.relativize(dir);
					int count = relative.getNameCount();
					System.out.println("preVisitDirectory " + describePath(dir));
					if (count > 0) {
						String name = relative.getFileName().toString();

						Path parent = dir.getParent();
						final PluginGuiTreeNode node;
						if (dir == path) {
							node = guiNode;
						} else {
							PluginGuiTreeNode pNode = guiNodeMap.get(parent);

							if (pNode != null) {
								node = pNode.addChild(Text.of(name), SortOrder.ALPHABETICAL_ORDER);
							} else {
								node = guiNode.addChild(Text.translate("gui.prefix.no_parent_dir", name), SortOrder.ALPHABETICAL_ORDER);
							}
						}

						node.mainIcon(node.manager().iconFolder());

						guiNodeMap.put(dir, node);

						if (!dir.equals(path) && !config.loadSubFolders) {
							node.subIcon(node.manager().iconDisabled());
							node.addChild(Text.translate("gui.text.sub_folders_disabled"))//
								.setDirectLevel(WarningLevel.WARN)//
								.subIcon(node.manager().iconDisabled());
							return FileVisitResult.SKIP_SUBTREE;
						}

						char first = name.isEmpty() ? ' ' : name.charAt(0);
						if (('0' <= first && first <= '9') || first == '>' || first == '<' || first == '=') {
							// Might be a game version
							if (config.restrictGameVersions && gameVersion != null) {
								// TODO: Support "1.12.x" type version parsing...
								for (String sub : name.split(" ")) {
									if (sub.isEmpty()) {
										continue;
									}

									if (!VersionConstraintImpl.parse(sub).matches(gameVersion)) {
										node.subIcon(node.manager().iconDisabled());
										node.addChild(Text.translate("gui.text.game_version_mismatch"))// TODO translation
											.setDirectLevel(WarningLevel.INFO)//
											.subIcon(node.manager().iconDisabled());
										return FileVisitResult.SKIP_SUBTREE;
									}
								}
							} else {
								node.subIcon(node.manager().iconDisabled());
								node.addChild(Text.translate("gui.text.game_versions_disabled"))//TODO translate
									.setDirectLevel(WarningLevel.WARN)//
									.subIcon(node.manager().iconDisabled());
								return FileVisitResult.SKIP_SUBTREE;
							}
						}
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					Path parent = file.getParent();
					PluginGuiTreeNode pNode = guiNodeMap.get(parent);
					final PluginGuiTreeNode node;

					String name = file.getFileName().toString();

					if (pNode != null) {
						node = pNode.addChild(Text.of(name), SortOrder.ALPHABETICAL_ORDER);
					} else {
						node = guiNode.addChild(Text.translate("gui.prefix.no_parent_file"), SortOrder.ALPHABETICAL_ORDER);
					}

					scanModFile(file, false, node);
					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException io) {
			throw new RuntimeException(io);
		}
	}

	void scanClasspathFolder(Path folder, PluginGuiTreeNode guiNode) {
		Map<ModLoadOption, BasePluginContext> map = new HashMap<>();

		for (BasePluginContext ctx : plugins.values()) {
			ModLoadOption[] mods;
			try {
				mods = ctx.plugin().scanClasspathFolder(folder, guiNode);
			} catch (IOException e) {
				// FOR NOW
				// TODO: Proper error handling!
				throw new Error("The plugin '" + ctx.pluginId() + "' failed to load " + describePath(folder), e);
			}

			if (mods != null && mods.length > 0) {
				for (ModLoadOption mod : mods) {
					map.put(mod, ctx);
				}

				if (ctx == theQuiltPlugin.context()) {
					break;
				}
			}
		}

		addModOption(map, guiNode);
	}

	void scanModFile(Path file, boolean fromClasspath, PluginGuiTreeNode guiNode) {

		// We only propose a file as a possible mod in the following scenarios:
		// General: must not end with ".disabled".
		// Some OSes generate metadata so consider the following:
		// - UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`.
		// - MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the form of `.mod.jar`

		// Note that we only perform name-based checks here - the "isHidden" check is in "scanModFile" since it might
		// require opening the file's metadata

		String fileName = file.getFileName().toString();
		guiNode.mainIcon(guiNode.manager().iconUnknownFile());

		if (fileName.startsWith(".")) {
			guiNode.subIcon(guiNode.manager().iconDisabled());
			guiNode.sortPrefix("disabled");
			guiNode.addChild(Text.translate("gui.text.file_hidden_dot_prefixed"))//TODO translate
				.subIcon(guiNode.manager().iconDisabled());
			return;
		} else if (fileName.endsWith(".disabled")) {
			guiNode.subIcon(guiNode.manager().iconDisabled());
			guiNode.sortPrefix("disabled");
			return;
		}

		if (config.singleThreadedLoading) {
			scanModFile0(file, fromClasspath, guiNode);
		} else {
			executor.submit(() -> {
				scanModFile0(file, fromClasspath, guiNode);
			});
		}
	}

	private void scanModFile0(Path file, boolean fromClasspath, PluginGuiTreeNode guiNode) {
		try {
			if (Files.isHidden(file)) {
				guiNode.sortPrefix("disabled");
				guiNode.subIcon(guiNode.manager().iconDisabled());
				guiNode.addChild(Text.translate("gui.text.file_hidden"));// TODO translate
				return;
			}
		} catch (IOException e) {

			Text title = Text.translate("gui.text.ioexception_files_hidden", e.getMessage());
			QuiltPluginError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Failed to check if " + describePath(file) + " is hidden or not!");
			error.appendDescription(Text.translate("gui.text.ioexception_files_hidden.desc.0", describePath(file)));
			error.appendThrowable(e);

			guiNode.addChild(title).setError(e, error);
			e.printStackTrace();

			return;
		}

		try {
			Path zipRoot = loadZip0(file);
			if (file.getFileName().toString().endsWith(".jar")) {
				guiNode.mainIcon(guiNode.manager().iconJarFile());
			} else {
				guiNode.mainIcon(guiNode.manager().iconZipFile());
			}

			if (this.config.singleThreadedLoading) {
				scanZip(file, zipRoot, fromClasspath, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanZipTask(file, zipRoot, fromClasspath, guiNode));
			}

		} catch (ZipException e) {

			// TODO: check for common cases and print those
			// (I.E zero-byte file)

			Text title = Text.translate("gui.error.zipexception.title", e.getMessage());
			QuiltPluginError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Failed to unzip " + describePath(file) + "!");
			error.appendDescription(Text.translate("gui.error.zipexception.desc.0", describePath(file)));
			error.appendDescription(Text.translate("gui.error.zipexception.desc.1"));
			error.appendThrowable(e);
			error.addFileViewButton(Text.translate("gui.view_file"), getRealContainingFile(file));

			guiNode.addChild(Text.translate("gui.error.zipexception", e.getMessage()))//TODO: translate
				.setError(e, error);

		} catch (IOException e) {

			Text title = Text.translate("gui.error.ioexception.title", e.getMessage());
			QuiltPluginError error = reportError(theQuiltPluginContext, title);
			error.appendReportText("Failed to read " + describePath(file) + "!");
			error.appendDescription(Text.translate("gui.error.ioexception.desc.0", describePath(file)));
			error.appendThrowable(e);
			error.addFileViewButton(Text.translate("gui.view_file"), getRealContainingFile(file));

			guiNode.addChild(Text.translate("gui.error.ioexception", e.getMessage()))//TODO: translate
				.setError(e, error);

		} catch (NonZipException e) {

			guiNode.mainIcon(guiNode.manager().iconUnknownFile());

			if (this.config.singleThreadedLoading) {
				scanUnknownFile(file, fromClasspath, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanUnknownFileTask(file, fromClasspath, guiNode));
			}
		}
	}

	/** Called by {@link MainThreadTask.ScanZipTask} */
	void scanZip(Path zipFile, Path zipRoot, boolean fromClasspath, PluginGuiTreeNode guiNode) {

		try {
			state.push(guiNode);

			Map<ModLoadOption, BasePluginContext> map = new HashMap<>();

			for (BasePluginContext ctx : plugins.values()) {
				ModLoadOption[] mods;
				try {
					mods = ctx.plugin().scanZip(zipRoot, fromClasspath, guiNode);
				} catch (IOException e) {
					// FOR NOW
					// TODO: Proper error handling!
					throw new Error(
						"The plugin '" + ctx.pluginId() + "' failed to load '" + describePath(zipFile) + "'", e
					);
				}

				if (mods != null && mods.length > 0) {
					for (ModLoadOption mod : mods) {
						map.put(mod, ctx);
					}

					if (ctx == theQuiltPlugin.context()) {
						break;
					}
				}
			}

			addModOption(map, guiNode);

		} finally {
			state.pop();
		}
	}

	/** Called by {@link MainThreadTask.ScanUnknownFileTask} */
	void scanUnknownFile(Path file, boolean fromClasspath, PluginGuiTreeNode guiNode) {

		try {
			state.push(guiNode);

			Map<ModLoadOption, BasePluginContext> map = new HashMap<>();

			for (BasePluginContext ctx : plugins.values()) {
				ModLoadOption[] mods;
				try {
					mods = ctx.plugin().scanUnknownFile(file, fromClasspath, guiNode);
				} catch (IOException e) {
					// FOR NOW
					// TODO: Proper error handling!
					throw new Error("The plugin '" + ctx.pluginId() + "' failed to load " + describePath(file), e);
				}

				if (mods != null && mods.length > 0) {
					for (ModLoadOption mod : mods) {
						map.put(mod, ctx);
					}

					if (ctx == theQuiltPlugin.context()) {
						break;
					}
				}
			}

			addModOption(map, guiNode);
		} finally {
			state.pop();
		}
	}

	private void addModOption(Map<ModLoadOption, BasePluginContext> map, PluginGuiTreeNode guiNode) {
		if (map == null || map.isEmpty()) {
			guiNode.addChild(Text.translate("gui.warn.no_plugin_could_load"))//TODO: translate
				.setDirectLevel(WarningLevel.WARN);
		} else if (map.size() == 1) {
			ModLoadOption option = map.keySet().iterator().next();
			BasePluginContext plugin = map.values().iterator().next();
			addSingleModOption(option, plugin, true, guiNode);
		} else {
			guiNode.addChild(Text.translate("gui.warn.overloaded"));//TODO:translate
			// TODO: Report the mod as being "overloaded"?
			// or just add all, and let the solver figure out which is which.
			for (Map.Entry<ModLoadOption, BasePluginContext> entry : map.entrySet()) {
				addSingleModOption(entry.getKey(), entry.getValue(), false, guiNode);
			}
		}
	}

	void addSingleModOption(ModLoadOption mod, BasePluginContext provider, boolean only, PluginGuiTreeNode guiNode) {

		PluginGuiTreeNode loadedBy = guiNode.addChild(Text.translate("gui.text.mod_loaded_by", provider.pluginId()))//
			.mainIcon(mod.modTypeIcon());

		if (only) {
			guiNode.subIcon(mod.modTypeIcon());
			loadedBy.debug();
		}

		String id = mod.id();
		Version version = mod.version();
		Path from = mod.from();
		modPaths.put(from, mod);
		modProviders.put(mod, provider.pluginId());
		modGuiNodes.put(mod, guiNode);

		guiNode.addChild(Text.translate("gui.text.id", id));
		guiNode.addChild(Text.translate("gui.text.version", version.raw()));

		System.out.println("added " + describePath(from) + " as " + mod);

		PotentialModSet set = modIds.computeIfAbsent(id, k -> new PotentialModSet());
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
			idsWithPlugins.add(id);
		}

		addLoadOption(mod, provider);
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
