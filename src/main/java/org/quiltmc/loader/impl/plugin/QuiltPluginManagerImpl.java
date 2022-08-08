package org.quiltmc.loader.impl.plugin;

import java.io.IOException;
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
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipException;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
import org.quiltmc.loader.api.plugin.NonZipException;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.QuiltPluginTask;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.SortOrder;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
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
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedPath;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.plugin.fabric.StandardFabricPlugin;
import org.quiltmc.loader.impl.plugin.gui.TempQuilt2OldStatusNode;
import org.quiltmc.loader.impl.plugin.quilt.StandardQuiltPlugin;
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

	final Map<Path, Path> pathParents = new HashMap<>();
	final Map<Path, String> customPathNames = new HashMap<>();
	final Map<String, Integer> allocatedFileSystemIndices = new HashMap<>();

	final Map<Path, String> modFolders = new LinkedHashMap<>();
	final Map<Path, ModLoadOption> modPaths = new LinkedHashMap<>();
	final Map<ModLoadOption, String> modProviders = new HashMap<>();
	final Map<String, PotentialModSet> modIds = new LinkedHashMap<>();

	final Map<TentativeLoadOption, QuiltPluginContext> tentativeLoadOptions = new LinkedHashMap<>();

	private final StandardQuiltPlugin theQuiltPlugin;
	final Map<QuiltLoaderPlugin, QuiltPluginContext> plugins = new LinkedHashMap<>();
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

		this.executor = config.singleThreadedLoading ? null : Executors.newCachedThreadPool();
		this.mainThreadTasks = config.singleThreadedLoading ? new ArrayDeque<>() : new ConcurrentLinkedQueue<>();

		customPathNames.put(modsDir, "<mods>");

		mainThreadTasks.add(new MainThreadTask.ScanFolderTask(modsDir, QUILT_ID));
		theQuiltPlugin = new StandardQuiltPlugin();
		addBuiltinPlugin(theQuiltPlugin);
		addBuiltinPlugin(new StandardFabricPlugin());
	}

	private void addBuiltinPlugin(BuiltinQuiltPlugin plugin) {
		BuiltinPluginContext ctx = new BuiltinPluginContext(this, QUILT_ID, plugin);
		plugin.load(ctx, Collections.emptyMap());
		plugins.put(plugin, ctx);
		plugin.addModFolders(ctx.modFolderSet);
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

	// ############
	// # Internal #
	// ############

	public PluginGuiTreeNode getModsFromPluginsGuiNode() {
		if (guiNodeModsFromPlugins == null) {
			guiNodeModsFromPlugins = guiFileRoot.addChild("TODO:TRANSLATE('floating-mods-from-plugins')");
		}
		return guiNodeModsFromPlugins;
	}

	Class<?> findClass(String name, String pkg) throws ClassNotFoundException {
		if (pkg == null) {
			return null;
		}
		QuiltPluginClassLoader cl = pluginsByPackage.get(pkg);
		return cl == null ? null : cl.loadClass(name);
	}

	// Internal (Running)

	public ModSolveResultImpl run(boolean scanClasspath) throws ModSolvingError, TimeoutException {

		if (game != null) {
			theQuiltPlugin.addBuiltinMods(game);
		}

		if (scanClasspath) {
			scanClasspath();
		}

		for (int cycle = 0; cycle < 1000; cycle++) {
			ModSolveResultImpl result = runSingleCycle();
			if (result != null) {
				return result;
			}
		}

		throw new ModSolvingError(
			"Too many cycles! 1000 cycles of plugin loading is a lot, since each one *could* take a second..."
		);
	}

	private void scanClasspath() {
		PluginGuiTreeNode classpathRoot = guiFileRoot.addChild("TODO:TRANSLATE('classpath')");

		ClasspathModCandidateFinder.findCandidatesStatic((paths, ignored) -> {
			final Path path;
			if (paths.size() > 1) {
				path = new QuiltJoinedFileSystem(QuiltJoinedFileSystem.uniqueOf("classpath"), paths).getRoot();
			} else {
				path = paths.get(0);
			}

			if (Files.exists(path)) {
				String name = describePath(path);
				PluginGuiTreeNode clNode = classpathRoot.addChild(name, SortOrder.ALPHABETICAL_ORDER);
				if (Files.isDirectory(path)) {
					clNode.mainIcon(clNode.manager().iconFolder());
					scanClasspathFolder(path, clNode);
				} else {
					scanModFile(path, clNode);
				}
			}
		});
	}

	private ModSolveResultImpl runSingleCycle() throws ModSolvingError, TimeoutException {

		refreshPlugins();

		PerCycleStep step = PerCycleStep.START;
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
					step = PerCycleStep.SOLVE;
					break;
				}
				case SOLVE: {

					if (solver.hasSolution()) {
						ModSolveResultImpl partialResult = getPartialSolution();

						if (processTentatives(partialResult)) {
							step = PerCycleStep.POST_SOLVE_TENTATIVE;
						} else {
							step = PerCycleStep.SUCCESS;
							result = partialResult;
						}

						break;
					} else {
						Collection<Rule> parts = solver.getError();

						StringBuilder sb = new StringBuilder();
						for (Rule rule : parts) {
						    sb.append("\n");
						    rule.fallbackErrorDescription(sb);
						}

						throw new AbstractMethodError("// TODO: error handling!" + sb);
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
			forceGcButBadly();
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
		try {
			Object[] objs = new Object[1];

			while (true) {
				Object old = objs;
				objs = new Object[1024];
				objs[0] = old;
			}

		} catch (OutOfMemoryError oome) {
			// Ignored
		}
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

		PluginGuiTreeNode folderRoot = guiFileRoot.addChild(describePath(path));
		folderRoot.mainIcon(guiFileRoot.manager().iconFolder());
		folderRoot.addChild("TODO:TRANSLATE('loaded by plugin %s', " + pluginSrc + ")").debug();

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
								node = pNode.addChild(name, SortOrder.ALPHABETICAL_ORDER);
							} else {
								node = guiNode.addChild("*no-parent-dir* " + name, SortOrder.ALPHABETICAL_ORDER);
							}
						}

						node.mainIcon(node.manager().iconFolder());

						guiNodeMap.put(dir, node);

						if (!dir.equals(path) && !config.loadSubFolders) {
							node.subIcon(node.manager().iconDisabled());
							node.addChild("TODO:TRANSLATE('sub-folders-disabled')")//
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
										node.addChild("TODO:TRANSLATE('game-version-mismatch')")//
											.setDirectLevel(WarningLevel.INFO)//
											.subIcon(node.manager().iconDisabled());
										return FileVisitResult.SKIP_SUBTREE;
									}
								}
							} else {
								node.subIcon(node.manager().iconDisabled());
								node.addChild("TODO:TRANSLATE('game-versions-disabled')")//
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
						node = pNode.addChild(name, SortOrder.ALPHABETICAL_ORDER);
					} else {
						node = guiNode.addChild("*no-parent-file* " + name, SortOrder.ALPHABETICAL_ORDER);
					}

					scanModFile(file, node);
					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException io) {
			throw new RuntimeException(io);
		}
	}

	void scanClasspathFolder(Path folder, PluginGuiTreeNode guiNode) {
		Map<ModLoadOption, QuiltPluginContext> map = new HashMap<>();

		for (QuiltPluginContext ctx : plugins.values()) {
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

	void scanModFile(Path file, PluginGuiTreeNode guiNode) {

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
			guiNode.addChild("TODO:TRANSLATE('file-hidden-dot-prefixed')")//
				.subIcon(guiNode.manager().iconDisabled());
			return;
		} else if (fileName.endsWith(".disabled")) {
			guiNode.subIcon(guiNode.manager().iconDisabled());
			guiNode.sortPrefix("disabled");
			return;
		}

		if (config.singleThreadedLoading) {
			scanModFile0(file, guiNode);
		} else {
			executor.submit(() -> {
				scanModFile0(file, guiNode);
			});
		}
	}

	private void scanModFile0(Path file, PluginGuiTreeNode guiNode) {
		try {
			if (Files.isHidden(file)) {
				guiNode.sortPrefix("disabled");
				guiNode.subIcon(guiNode.manager().iconDisabled());
				guiNode.addChild("TODO:TRANSLATE('file-hidden')");
				return;
			}
		} catch (IOException e) {

			guiNode.addChild("TODO:TRANSLATE('io-exception during Files.isHidden %s', " + e.getMessage() + ")")//
				.setError(e);
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
				scanZip(file, zipRoot, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanZipTask(file, zipRoot, guiNode));
			}

		} catch (ZipException e) {

			// TODO: check for common cases and print those
			// (I.E zero-byte file)

			guiNode.addChild("TODO:TRANSLATE('zip-exception %s', " + e.getMessage() + ")")//
				.setError(e);
			new Error("Failed to load " + describePath(file), e).printStackTrace();

		} catch (IOException e) {

			guiNode.addChild("TODO:TRANSLATE('io-exception %s', " + e.getMessage() + ")")//
				.setError(e);

			new Error("Failed to load " + describePath(file), e).printStackTrace();

		} catch (NonZipException e) {

			guiNode.mainIcon(guiNode.manager().iconUnknownFile());

			if (this.config.singleThreadedLoading) {
				scanUnknownFile(file, guiNode);
			} else {
				mainThreadTasks.add(new MainThreadTask.ScanUnknownFileTask(file, guiNode));
			}
		}
	}

	/** Called by {@link MainThreadTask.ScanZipTask} */
	void scanZip(Path zipFile, Path zipRoot, PluginGuiTreeNode guiNode) {

		try {
			state.push(guiNode);

			Map<ModLoadOption, QuiltPluginContext> map = new HashMap<>();

			for (QuiltPluginContext ctx : plugins.values()) {
				ModLoadOption[] mods;
				try {
					mods = ctx.plugin().scanZip(zipRoot, guiNode);
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
	void scanUnknownFile(Path file, PluginGuiTreeNode guiNode) {

		try {
			state.push(guiNode);

			Map<ModLoadOption, QuiltPluginContext> map = new HashMap<>();

			for (QuiltPluginContext ctx : plugins.values()) {
				ModLoadOption[] mods;
				try {
					mods = ctx.plugin().scanUnknownFile(file, guiNode);
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

	private void addModOption(Map<ModLoadOption, QuiltPluginContext> map, PluginGuiTreeNode guiNode) {
		if (map == null || map.isEmpty()) {
			guiNode.addChild("TODO:TRANSLATE('no-plugin-could-load')")//
				.setDirectLevel(WarningLevel.WARN);
		} else if (map.size() == 1) {
			ModLoadOption option = map.keySet().iterator().next();
			QuiltPluginContext plugin = map.values().iterator().next();
			addSingleModOption(option, plugin, true, guiNode);
		} else {
			guiNode.addChild("TODO:TRANSLATE('overloaded-mod')");
			// TODO: Report the mod as being "overloaded"?
			// or just add all, and let the solver figure out which is which.
			for (Map.Entry<ModLoadOption, QuiltPluginContext> entry : map.entrySet()) {
				addSingleModOption(entry.getKey(), entry.getValue(), false, guiNode);
			}
		}
	}

	void addSingleModOption(ModLoadOption mod, QuiltPluginContext provider, boolean only, PluginGuiTreeNode guiNode) {

		PluginGuiTreeNode loadedBy = guiNode.addChild("TODO:TRANSLATE('mod-loaded-by %s', " + provider.pluginId() + ")")//
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

		guiNode.addChild("id: '" + id + "'");
		guiNode.addChild("version: '" + version.raw() + "'");

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

	void addLoadOption(LoadOption option, QuiltPluginContext provider) {
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
