package org.quiltmc.loader.impl.plugin;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.FullModMetadata;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.impl.QuiltLoaderOptions;
import org.quiltmc.loader.impl.VersionConstraintImpl;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl;
import org.quiltmc.loader.impl.solver.Sat4jWrapper;

/** The main manager for loader plugins, and the mod finding process in general.
 * <p>
 * Unlike {@link QuiltLoader} itself, it does make sense to have multiple of these at once: one for loading plugins that
 * will be used, and many more for "simulating" mod loading. */
public class QuiltPluginManagerImpl implements QuiltPluginManager {

	public final boolean simulationOnly;
	public final QuiltLoaderOptions options;
	final GameProvider game;
	final Version gameVersion;

	final Map<Path, String> modFolders = new LinkedHashMap<>();

	final List<QuiltLoaderPlugin> plugins = new ArrayList<>();
	final Map<String, QuiltPluginContextImpl> pluginsById = new HashMap<>();
	final Map<String, QuiltPluginClassLoader> pluginsByPackage = new HashMap<>();

	final Sat4jWrapper solver = new Sat4jWrapper(logger);

	/** Set to null if {@link QuiltLoaderOptions#singleThreadedLoading} is true, otherwise this will be a useful
	 * value. */
	private final ExecutorService executor;

	public QuiltPluginManagerImpl(Path modsDir, GameProvider game, QuiltLoaderOptions options) {
		this(modsDir, game, false, options);
	}

	public QuiltPluginManagerImpl(Path modsDir, GameProvider game, boolean simulationOnly, QuiltLoaderOptions options) {
		this.simulationOnly = simulationOnly;
		this.game = game;
		gameVersion = Version.of(game.getNormalizedGameVersion());
		this.options = options;
		this.executor = options.singleThreadedLoading ? null : Executors.newCachedThreadPool();
		modFolders.put(modsDir, "quilt_loader");
		addBuiltinPlugin(new StandardQuiltPlugin());
	}

	private void addBuiltinPlugin(BuiltinQuiltPlugin plugin) {
		BuiltinPluginContext ctx = new BuiltinPluginContext(this);
		plugin.load(ctx, Collections.emptyMap());
		plugins.add(plugin);
		plugin.addModFolders(ctx.modFolderSet);
	}

	@Override
	public Path loadZip(Path zip) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Set<Path> getModFolders() {
		return Collections.unmodifiableSet(modFolders.keySet());
	}

	@Override
	public @Nullable String getFolderProvider(Path modFolder) {
		return modFolders.get(modFolder);
	}

	@Override
	public String describePath(Path path) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public Path getParent(Path path) {
		// TODO: Implement the JIMFS part of this!
		return path.getParent();
	}

	Class<?> findClass(String name, String pkg) throws ClassNotFoundException {
		if (pkg == null) {
			return null;
		}
		QuiltPluginClassLoader cl = pluginsByPackage.get(pkg);
		return cl == null ? null : cl.loadClass(name);
	}

	// Internal (Running)

	public ModSolveResultImpl run() throws ModSolvingError {

		for (int cycle = 0; cycle < 1000; cycle++) {

		}

		throw new ModSolvingError("Too many cycles! 1000 cycles of plugin loading is a huge number!");
	}

	private void loadPlugin(FullModMetadata metadata, Path from) throws ModSolvingError {
		QuiltPluginContextImpl oldPlugin = pluginsById.remove(metadata.id());
		final Map<String, LoaderValue> data;

		if (oldPlugin != null) {

			WeakReference<QuiltPluginClassLoader> classloaderRef = new WeakReference<>(oldPlugin.classLoader);

			plugins.remove(oldPlugin.plugin);
			data = oldPlugin.unload();
			pluginsByPackage.keySet().removeAll(oldPlugin.classLoader.loadablePackages);
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
			QuiltPluginContextImpl pluginCtx = new QuiltPluginContextImpl(this, from, metadata.plugin(), data);

			plugins.add(pluginCtx.plugin);
			pluginsById.put(metadata.id(), pluginCtx);
			for (String pkg : pluginCtx.classLoader.loadablePackages) {
				pluginsByPackage.put(pkg, pluginCtx.classLoader);
			}

			pluginCtx.plugin.addModFolders(pluginCtx.modFolderSet);

		} catch (ReflectiveOperationException e) {
			throw new ModSolvingError(
				"Failed to load the plugin '" + metadata.id() + "' from " + describePath(from), e
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

	boolean addModFolder(Path path, BasePluginContext ctx) {
		boolean added = modFolders.putIfAbsent(path, ctx.pluginId) == null;
		if (added) {
			scanModFolder(path);
		}
		return added;
	}

	private void scanModFolder(Path path) {
		if (options.singleThreadedLoading) {
			scanModFolder0(path);
		} else {
			executor.submit(() -> {
				scanModFolder0(path);
			});
		}
	}

	private void scanModFolder0(Path path) {
		try {
			int maxDepth = options.loadSubFolders ? Integer.MAX_VALUE : 1;
			Set<FileVisitOption> fOptions = Collections.singleton(FileVisitOption.FOLLOW_LINKS);
			Files.walkFileTree(path, fOptions, maxDepth, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = path.relativize(dir);
					int count = relative.getNameCount();
					if (count > 0) {
						String name = relative.getFileName().toString();
						char first = name.charAt(0);
						if (('0' <= first && first <= '9') || first == '>' || first == '<' || first == '=') {
							// Might be a game version
							if (options.restrictGameVersions) {
								// TODO: Support "1.12.x" type version parsing...
								for (String sub : name.split(" ")) {
									if (sub.isEmpty()) {
										continue;
									}

									if (!VersionConstraintImpl.parse(sub).matches(gameVersion)) {
										// FIXME: Turn this into a gui element!
										// (don't log it - it's too debug-level)
										System.out.println(
											"QuiltPluginManagerImpl " + sub + " doesn't match " + gameVersion
												+ ", skipping"
										);
										return FileVisitResult.SKIP_SUBTREE;
									}
								}
							} else {
								return FileVisitResult.SKIP_SUBTREE;
							}
						}
					}
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					/* We only propose a file as a possible mod in the following scenarios: General: Must no end with
					 * ".disabled" Some OSes Generate metadata so consider the following because of OSes: UNIX: Exclude
					 * if file is hidden; this occurs when starting a file name with `.` MacOS: Exclude hidden +
					 * startsWith "." since Mac OS names their metadata files in the form of `.mod.jar` */

					String fileName = file.getFileName().toString();

					if (!fileName.startsWith(".") && !fileName.endsWith(".disabled") && !Files.isHidden(file)) {
						// TODO: Scan/read/check the file!
					}

					return FileVisitResult.CONTINUE;
				}
			});

		} catch (IOException io) {
			throw new RuntimeException(io);
		}
	}

	void scanModFile(Path path) {

	}
}
