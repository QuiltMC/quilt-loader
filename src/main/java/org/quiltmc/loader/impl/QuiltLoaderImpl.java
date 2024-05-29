/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

package org.quiltmc.loader.impl;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.Opcodes;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.ModContainer.BasicSourceType;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.gui.LoaderGuiClosed;
import org.quiltmc.loader.api.gui.LoaderGuiException;
import org.quiltmc.loader.api.gui.QuiltBasicWindow;
import org.quiltmc.loader.api.gui.QuiltDisplayedError;
import org.quiltmc.loader.api.gui.QuiltGuiMessagesTab;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.gui.QuiltWarningLevel;
import org.quiltmc.loader.api.gui.QuiltDisplayedError.QuiltErrorButton;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModEntrypoint;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult.SpecificLoadOptionResult;
import org.quiltmc.loader.impl.discovery.ClasspathModCandidateFinder;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.entrypoint.EntrypointStorage;
import org.quiltmc.loader.impl.entrypoint.EntrypointUtils;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedPath;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.GuiManagerImpl;
import org.quiltmc.loader.impl.gui.QuiltJsonGuiMessage;
import org.quiltmc.loader.impl.launch.common.QuiltCodeSource;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.launch.common.QuiltMixinBootstrap;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.ProvidedModContainer;
import org.quiltmc.loader.impl.metadata.qmj.ProvidedModMetadata;
import org.quiltmc.loader.impl.patch.PatchLoader;
import org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl;
import org.quiltmc.loader.impl.plugin.UnsupportedModChecker.UnsupportedType;
import org.quiltmc.loader.impl.plugin.fabric.FabricModOption;
import org.quiltmc.loader.impl.report.QuiltReport.CrashReportSaveFailed;
import org.quiltmc.loader.impl.report.QuiltReportedError;
import org.quiltmc.loader.impl.solver.ModSolveResultImpl;
import org.quiltmc.loader.impl.transformer.TransformCacheManager;
import org.quiltmc.loader.impl.transformer.TransformCacheResult;
import org.quiltmc.loader.impl.util.Arguments;
import org.quiltmc.loader.impl.util.AsciiTableGenerator;
import org.quiltmc.loader.impl.util.AsciiTableGenerator.AsciiTableColumn;
import org.quiltmc.loader.impl.util.AsciiTableGenerator.AsciiTableRow;
import org.quiltmc.loader.impl.util.DefaultLanguageAdapter;
import org.quiltmc.loader.impl.util.FileHasherImpl;
import org.quiltmc.loader.impl.util.FilePreloadHelper;
import org.quiltmc.loader.impl.util.HashUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.spongepowered.asm.mixin.FabricUtil;

import net.fabricmc.loader.api.ObjectShare;

import net.fabricmc.api.EnvType;

@QuiltLoaderInternal(value = QuiltLoaderInternalType.LEGACY_EXPOSED, replacements = QuiltLoader.class)
public final class QuiltLoaderImpl {
	public static final QuiltLoaderImpl INSTANCE = InitHelper.get();

	public static final int ASM_VERSION = Opcodes.ASM9;

	public static final String VERSION = "0.26.0-beta.4";
	public static final String MOD_ID = "quilt_loader";
	public static final String DEFAULT_MODS_DIR = "mods";
	public static final String DEFAULT_CACHE_DIR = ".cache";
	public static final String DEFAULT_CONFIG_DIR = "config";

	public static final String CACHE_DIR_NAME = "quilt_loader"; // inside global cache dir
	private static final String PROCESSED_MODS_DIR_NAME = "processedMods"; // relative to loader cache dir
	public static final String REMAPPED_JARS_DIR_NAME = "remappedJars"; // relative to loader cache dir
	private static final String TMP_DIR_NAME = "tmp"; // relative to loader cache dir

	// Mod table flags
	public static final char FLAG_DEPS_CHANGED = 'o';
	public static final char FLAG_DEPS_REMOVED = 'R';

	protected final Map<String, ModContainerExt> modMap = new HashMap<>();

	protected final Map<String, String> modOriginHash = new HashMap<>();
	protected final Map<Path, String> pathOriginHash = new HashMap<>();

	protected List<ModContainerExt> mods = new ArrayList<>();

	private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();

	private final ObjectShare objectShare = new ObjectShareImpl();

	private boolean frozen = false;

	private Object gameInstance;

	private MappingResolver mappingResolver;
	private GameProvider provider;
	/** The value of {@link Arguments#ADD_MODS}. This must be stored since we remove it before launching the game. */
	private String argumentModsList;
	private Path gameDir;
	private Path cacheDir;
	private Path configDir;
	private Path modsDir;

	/** Stores every mod which has been copied into a temporary jar file: see {@link #shouldCopyToJar(ModLoadOption)}
	 * and {@link #copyToJar(ModLoadOption, Path)}. */
	private final Map<String, File> copiedToJarMods = new HashMap<>();

	/** Stores the result from running plugins. This is useful if we crash after selecting which mods to load, but
	 * before fully loading those mods. */
	private ModSolveResult temporaryPluginSolveResult;
	private ModLoadOption[] temporaryOrderedModList;
	private Map<Path, List<List<Path>>> temporarySourcePaths;

	protected QuiltLoaderImpl() {
	}

	/**
	 * Freeze the FabricLoader, preventing additional mods from being loaded.
	 */
	public void freeze() {
		if (frozen) {
			throw new IllegalStateException("Already frozen!");
		}

		frozen = true;
		finishModLoading();
	}

	public GameProvider getGameProvider() {
		if (provider == null) throw new IllegalStateException("game provider not set (yet)");

		return provider;
	}

	public GameProvider tryGetGameProvider() {
		return provider;
	}

	public void setGameProvider(GameProvider provider) {
		this.provider = provider;

		setGameDir(provider.getLaunchDirectory());
		argumentModsList = provider.getArguments().remove(Arguments.ADD_MODS);
	}

	public void setGameDir(Path gameDir) {
		this.gameDir = gameDir;

		this.cacheDir = gameDir.resolve(System.getProperty(SystemProperties.CACHE_DIRECTORY, DEFAULT_CACHE_DIR));
		this.configDir = gameDir.resolve(System.getProperty(SystemProperties.CONFIG_DIRECTORY, DEFAULT_CONFIG_DIR));

		initializeModsDir(gameDir);
	}

	private void initializeModsDir(Path gameDir) {
		String modsDir = System.getProperty(SystemProperties.MODS_DIRECTORY);
		this.modsDir = gameDir.resolve((modsDir == null || modsDir.isEmpty()) ? DEFAULT_MODS_DIR : modsDir);
	}

	public String getAdditionalModsArgument() {
		return argumentModsList;
	}

	public Object getGameInstance() {
		return gameInstance;
	}

	public EnvType getEnvironmentType() {
		return QuiltLauncherBase.getLauncher().getEnvironmentType();
	}

	/**
	 * @return The game instance's root directory.
	 */
	public Path getGameDir() {
		return gameDir;
	}

	public static Path ensureDirExists(Path path, String name) {
		if (path == null) {
			// May be null during tests for cache and config directories
			// If this is in production then things are about to go very wrong.
			return null;
		}

		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path);
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to create %s directory at '%s'", name, path), e);
			}
		}

		return path;
	}

	/**
	 * @return The game instance's cache directory.
	 */
	public Path getCacheDir() {
		return ensureDirExists(cacheDir, "cache");
	}

	/**
	 * @return "{@link #getCacheDir()} / {@value #CACHE_DIR_NAME}"
	 */
	public Path getQuiltLoaderCacheDir() {
		return ensureDirExists(getCacheDir().resolve(CACHE_DIR_NAME), "quilt loader cache");
	}

	/**
	 * @return The game instance's configuration directory.
	 */
	public Path getConfigDir() {
		return ensureDirExists(configDir, "config");
	}

	public Path getModsDir() {
		// modsDir should be initialized before this method is ever called, this acts as a very special failsafe
		if (modsDir == null) {
			initializeModsDir(gameDir);
		}

		return ensureDirExists(modsDir, "mods");
	}

	public void load() {
		if (provider == null) throw new IllegalStateException("game provider not set");
		if (frozen) throw new IllegalStateException("Frozen - cannot load additional mods!");

		if (SystemProperties.VALIDATION_LEVEL > 0) {
			Log.info(LogCategory.GENERAL, "Enabled debugging validation level " + SystemProperties.VALIDATION_LEVEL);
		}

		try {
			setup();
		} catch (ModResolutionException exception) {
			throw new FormattedException("Incompatible mod set!", exception);
		}
	}

	private void setup() throws ModResolutionException {

		ModSolveResult result = runPlugins();
		temporaryPluginSolveResult = result;

		SpecificLoadOptionResult<LoadOption> spec = result.getResult(LoadOption.class);

		// Debugging
		if (Boolean.getBoolean(SystemProperties.DEBUG_MOD_SOLVING)) {
			for (LoadOption op : spec.getOptions()) {
				if (spec.isPresent(op)) {
					Log.info(LogCategory.GENERAL, " + " + op);
				}
			}

			for (LoadOption op : spec.getOptions()) {
				if (!spec.isPresent(op)) {
					Log.info(LogCategory.GENERAL, " - " + op);
				}
			}
		}

		List<ModLoadOption> modList = new ArrayList<>(result.directMods().values());
		Set<String> modIds = new HashSet<>();
		for (ModLoadOption mod : modList) {
			modIds.add(mod.id());
		}

		modList.sort(Comparator.comparing(ModLoadOption::id));
		int seed = modIds.hashCode();
		Collections.shuffle(modList, new Random(seed));

		performMixinReordering(modList);
		// TODO: reorder libraries to be first in the classloader order
		// (after we actually transform & load them with chasm)
		performLoadLateReordering(modList);
		temporaryOrderedModList = modList.toArray(new ModLoadOption[0]);

		long zipStart = System.nanoTime();
		String suffix = System.getProperty(SystemProperties.CACHE_SUFFIX, getEnvironmentType().name().toLowerCase(Locale.ROOT));
		FileHasherImpl hasher = new FileHasherImpl(null);

		for (ModLoadOption mod : modList) {
			Path from = mod.from();
			List<List<Path>> srcPaths = temporarySourcePaths.get(from);
			try {
				for (List<Path> paths : srcPaths) {
					Path first = paths.get(0);
					if (first.getFileSystem() != FileSystems.getDefault()) {
						throw new ModResolutionException(
							"The first path as a source for " + from + " (first = " + first
								+ ") is not on the default file system?"
						);
					}
					if (!pathOriginHash.containsKey(first)) {
						pathOriginHash.put(first, HashUtil.hashToString(hasher.computeNormalHash(from)));
					}
				}
				modOriginHash.put(mod.id(), HashUtil.hashToString(mod.computeOriginHash(hasher)));
			} catch (IOException e) {
				throw new ModResolutionException("Failed to compute the hash for mod '" + mod.id() + "'", e);
			}
		}

		Path transformCacheFolder = getCacheDir().resolve(CACHE_DIR_NAME).resolve("transform-cache-" + suffix);
		TransformCacheResult cacheResult = TransformCacheManager.populateTransformBundle(transformCacheFolder, modList, modOriginHash, result);
		QuiltZipPath transformedModBundle = cacheResult.transformCacheRoot;

		long zipEnd = System.nanoTime();

		try {
			QuiltLauncherBase.getLauncher().setTransformCache(transformedModBundle.toUri().toURL());
			QuiltLauncherBase.getLauncher().setHiddenClasses(cacheResult.hiddenClasses);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

		boolean copyAllMods = Boolean.getBoolean(SystemProperties.JAR_COPY_ALL_MODS);
		Set<String> modsToCopy = new HashSet<>();
		String jarCopiedMods = System.getProperty(SystemProperties.JAR_COPIED_MODS);
		if (jarCopiedMods != null) {
			for (String id : jarCopiedMods.split(",")) {
				modsToCopy.add(id);
			}
		}

		long zipSubCopyTotal = 0;
		long jarCopyTotal = 0;

		for (ModLoadOption modOption : modList) {
			Path resourceRoot;

			if (!modOption.needsTransforming() && modOption.namespaceMappingFrom() == null) {
				resourceRoot = modOption.resourceRoot();
			} else {
				String modid = modOption.id();
				Path modTransformed = transformedModBundle.resolve(modid + "/");
				Path excluded = transformedModBundle.resolve(modid + ".removed");

				if (FasterFiles.exists(excluded)) {
					throw new Error("// TODO: Implement pre-transform file removal!");
				} else if (!FasterFiles.isDirectory(modTransformed)) {
					resourceRoot = modOption.resourceRoot();
				} else {
					List<Path> paths = new ArrayList<>();

					long start = System.nanoTime();
					String fsName = modid + "-" + modOption.version();
					paths.add(new QuiltZipFileSystem(fsName, transformedModBundle.resolve(modid)).getRoot());
					if (modOption.couldResourcesChange()) {
						paths.add(modOption.resourceRoot());
					}
					zipSubCopyTotal += System.nanoTime() - start;

					// This cannot pass a java ZipFileSystem directly since URLClassPath can't load
					// from folders inside a zip.

					// Since we're using our own QuiltZipFileSystem this is okay, but if that gets reverted
					// we'll also need to revert this optimisation

					 if (paths.size() == 1) {
						 resourceRoot = paths.get(0);
					 } else {
						 resourceRoot = new QuiltJoinedFileSystem("_" + fsName, paths).getRoot();
					 }
				}
			}

			String modid2 = modOption.id();

			boolean copyThis = false;

			if (resourceRoot.getFileSystem() != FileSystems.getDefault() && !"jar".equals(resourceRoot.getFileSystem().provider().getScheme())) {
				copyThis = copyAllMods || modsToCopy.contains(modid2) || shouldCopyToJar(modOption, modIds);
			}

			if (copyThis) {
				long start = System.nanoTime();
				resourceRoot = copyToJar(transformCacheFolder, modOption, resourceRoot);
				jarCopyTotal += System.nanoTime() - start;
			}

			addMod(modOption.convertToMod(resourceRoot));
		}

		try {
			transformedModBundle.getFileSystem().close();
		} catch (IOException e) {
			// TODO!
			throw new Error(e);
		}

		temporaryPluginSolveResult = null;
		temporaryOrderedModList = null;
		temporarySourcePaths = null;

		long modAddEnd = System.nanoTime();

		System.out.println("transform-cache took " + (zipEnd - zipStart) / 1000_000 + "ms");
		System.out.println("zip sub copy took " + zipSubCopyTotal / 1000_000 + "ms");
		System.out.println("tmp jar copy took " + jarCopyTotal / 1000_000 + "ms");
		System.out.println("mod adding took " + (modAddEnd - zipEnd - zipSubCopyTotal - jarCopyTotal) / 1000_000 + "ms");

		int count = mods.size();
		Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", count, count != 1 ? "s" : "", createModTable());
	}

	@SuppressWarnings("RedundantIfStatement")
	private boolean shouldCopyToJar(ModLoadOption mod, Set<String> modIds) {
		String id = mod.id();
		if (id.equals("minecraft")) {
			if (Version.of("1.17.1").compareTo(mod.version()) > 0) {
				// Versions before 1.17.1 don't work very well if they aren't at the root of their zip file
				return true;
			}
			if (modIds.contains("fabric-resource-loader-v0")) {
				// Fabric API turns minecraft into a resource pack to load from instead of using the classpath,
				// so it also doesn't work very well
				return true;
			}
			if (modIds.contains("polymer")) {
				if (Version.of("1.19.3").compareTo(mod.version()) >= 0) {
					// Versions of polymer prior to 1.19.3 assume they can
					// find the minecraft jar on the default file system
					return true;
				}
			}
		}
		if ("charm".equals(id) /* Add version check here for if/when charm doesn't need this */) {
			// Charm also (currently) requires the mod files are in .jars directly.
			return true;
		}

		if ("charmonium".equals(id)) { // same issue as charm
			return true;
		}

		return false;
	}

	private Path copyToJar(Path transformCacheFolder, ModLoadOption modOption, final Path resourceRoot) throws Error {

		String versionFrom = modOption.version().toString();
		StringBuilder version = new StringBuilder();
		// Ensure it only contains reasonable chars
		for (int i = 0; i < versionFrom.length(); i++) {
			char c = versionFrom.charAt(i);
			if ('0' <= c && c <= '9' || 'a' <= c && c <= 'z' || 'A' <= c && c <= 'Z' || c == '-' || c == '_' || c == '.') {
				version.append(c);
			}
		}

		String fileName = "transformed-mod-" + modOption.id() + "-v" + version + ".jar";
		Path modJarFile = transformCacheFolder.resolve(fileName);
		Path andFinished = transformCacheFolder.resolve(fileName + ".finished");

		if (!Files.exists(modJarFile) || !Files.exists(andFinished)) {
			try {
				Files.deleteIfExists(modJarFile);
				Files.deleteIfExists(andFinished);
			} catch (IOException e) {
				throw new Error("// TODO: Failed to delete the previous partial jar!", e);
			}

			Log.info(LogCategory.GENERAL, "Copying " + modOption.id() + " to a temporary jar file " + modJarFile);
			try {
				try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(modJarFile))) {
					List<Path> list = Files.walk(resourceRoot).collect(Collectors.toList());
					for (Path path : list) {
						String pathStr = path.toString();
						if (pathStr.startsWith("/")) {
							pathStr = pathStr.substring(1);
						}
						if (FasterFiles.isDirectory(path)) {
							zip.putNextEntry(new ZipEntry(pathStr + "/"));
						} else {
							if (pathStr.startsWith("META-INF/") && pathStr.lastIndexOf('/') == 8 && pathStr.endsWith(".SF")) {
								continue;
							}
							byte[] bytes = Files.readAllBytes(path);
							if ("META-INF/MANIFEST.MF".equals(pathStr)) {
								boolean changed = false;
								Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));
								for (Attributes attributes : manifest.getEntries().values()) {
									if (attributes.remove(new Attributes.Name("SHA-256-Digest")) != null) {
										changed = true;
									}
								}
								if (changed) {
									ByteArrayOutputStream baos = new ByteArrayOutputStream();
									manifest.write(baos);
									bytes = baos.toByteArray();
								}
							}
							zip.putNextEntry(new ZipEntry(pathStr));
							zip.write(bytes);
						}
						zip.closeEntry();
					}
				}

				Files.createFile(andFinished);
			} catch (IOException e) {
				throw new Error("// TODO: Failed to copy the jar " + modJarFile, e);
			}
		} else if (!Boolean.getBoolean(SystemProperties.DISABLE_PRELOAD_TRANSFORM_CACHE)) {
			FilePreloadHelper.preLoad(modJarFile);
		}

		try {
			FileSystem fs = FileSystems.newFileSystem(modJarFile, (ClassLoader) null);
			return fs.getPath("/");
		} catch (IOException e) {
			throw new Error("// TODO: Failed to open the jar " + modJarFile, e);
		}
	}

	private ModSolveResult runPlugins() {
		QuiltLoaderConfig config = new QuiltLoaderConfig(getConfigDir().resolve("quilt-loader.txt"));
		QuiltPluginManagerImpl plugins = new QuiltPluginManagerImpl(getGameDir(), getConfigDir(), getModsDir(), getCacheDir(), provider, config);

		Path crashReportFile = null;
		String fullCrashText = null;

		try {
			ModSolveResultImpl result = plugins.run(true);

			boolean displayedMessage = handleUnknownFiles(plugins, result);

			temporarySourcePaths = new HashMap<>();
			for (ModLoadOption mod : result.directMods().values()) {
				temporarySourcePaths.put(mod.from(), plugins.convertToSourcePaths(mod.from()));
			}

			if (displayedMessage) {
				return result;
			}

			if ((provider != null && !provider.canOpenGui()) || GraphicsEnvironment.isHeadless()) {
				return result;
			}

			boolean dev = isDevelopmentEnvironment();
			boolean show = config.alwaysShowModStateWindow;

			if (!dev && !show) {
				return result;
			}

			boolean anyWarnings = false;

			if (plugins.guiFileRoot.maximumLevel().ordinal() <= QuiltWarningLevel.WARN.ordinal()) {
				anyWarnings = true;
			}

			if (plugins.guiModsRoot.maximumLevel().ordinal() <= QuiltWarningLevel.WARN.ordinal()) {
				anyWarnings = true;
			}

			if (!show && dev && !anyWarnings) {
				return result;
			}

			final QuiltLoaderText msg;
			if (anyWarnings) {
				int count = plugins.guiModsRoot.countAtLevel(QuiltWarningLevel.WARN)//
					+ plugins.guiFileRoot.countAtLevel(QuiltWarningLevel.WARN);
				msg = QuiltLoaderText.translate("msg.load_state.warns", count);
			} else {
				msg = QuiltLoaderText.translate("msg.load_state");
			}

			QuiltBasicWindow<Void> window = QuiltLoaderGui.createBasicWindow();
			window.title(QuiltLoaderText.of("Quilt Loader " + VERSION));
			window.mainText(msg);
			window.addTreeTab(QuiltLoaderText.translate("tab.file_list"), plugins.guiFileRoot);
			window.addTreeTab(QuiltLoaderText.translate("tab.mod_list"), plugins.guiModsRoot);
			window.addContinueButton().text(QuiltLoaderText.translate("button.continue_to", getGameProvider().getGameName()));

			// TODO: Look into writing a report!

			try {
				QuiltLoaderGui.open(window);
			} catch (Exception e) {
				e.printStackTrace();
			}

			return result;
		} catch (QuiltReportedError reported) {
			try {
				crashReportFile = reported.report.writeInDirectory(gameDir);
			} catch (CrashReportSaveFailed e) {
				fullCrashText = e.fullReportText;
			}
		}

		if ((provider != null && !provider.canOpenGui()) || GraphicsEnvironment.isHeadless()) {
			if (crashReportFile != null) {
				System.err.println("Game crashed! Saved the crash report to " + crashReportFile);
			}
			if (fullCrashText != null) {
				System.err.println("Game crashed, and also failed to save the crash report!");
				System.err.println(fullCrashText);
			}
			System.exit(1);
			throw new Error("System.exit(1) failed!");
		}

		String msg = "crash.during_setup." + provider.getGameId();
		QuiltBasicWindow<Void> window = QuiltLoaderGui.createBasicWindow();
		window.title(QuiltLoaderText.of("Quilt Loader " + QuiltLoaderImpl.VERSION));
		window.mainText(QuiltLoaderText.translate(msg));

		QuiltGuiMessagesTab messagesTab = window.addMessagesTab(QuiltLoaderText.translate("tab.messages"));

		if (fullCrashText != null) {
			QuiltDisplayedError error = QuiltLoaderGui.createError(QuiltLoaderText.translate("error.failed_to_save_crash_report"));
			error.setIcon(GuiManagerImpl.ICON_LEVEL_ERROR);
			error.appendDescription(QuiltLoaderText.translate("error.failed_to_save_crash_report.desc"));
			error.appendAdditionalInformation(QuiltLoaderText.translate("error.failed_to_save_crash_report.info"));
			error.addCopyTextToClipboardButton(QuiltLoaderText.translate("button.copy_crash_report"), fullCrashText);
			messagesTab.addMessage(error);
		}

		int number = 1;
		List<QuiltJsonGuiMessage> pluginErrors = plugins.getErrors();
		for (QuiltJsonGuiMessage error : pluginErrors) {
			if (number > 200) {
				error = new QuiltJsonGuiMessage(null, MOD_ID, QuiltLoaderText.translate("error.too_many_errors"));
				error.appendDescription(QuiltLoaderText.translate("error.too_many_errors.desc", pluginErrors.size() - 200));
				messagesTab.addMessage(error);
				break;
			}
			messagesTab.addMessage(error);
			number++;
		}

		window.addTreeTab(QuiltLoaderText.translate("tab.file_list"), plugins.guiFileRoot);
		window.addTreeTab(QuiltLoaderText.translate("tab.mod_list"), plugins.guiModsRoot);

		if (crashReportFile != null) {
			window.addFileOpenButton(crashReportFile).text(QuiltLoaderText.translate("button.open_crash_report"));
			window.addCopyFileToClipboardButton(QuiltLoaderText.translate("button.copy_crash_report"), crashReportFile);
		}

		window.addFolderViewButton(QuiltLoaderText.translate("button.open_mods_folder"), getModsDir());
		window.addContinueButton().text(QuiltLoaderText.translate("button.exit")).icon(QuiltLoaderGui.iconLevelError());

		try {
			QuiltLoaderGui.open(window);
			System.exit(1);
			throw new Error("System.exit(1) Failed!");
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private boolean handleUnknownFiles(QuiltPluginManagerImpl plugins, ModSolveResultImpl result) {
		if (plugins.guiUnknownMods.isEmpty()) {
			return false;
		}

		{
			QuiltBasicWindow<Void> window = QuiltLoaderGui.createBasicWindow();
			window.title(QuiltLoaderText.of("Quilt Loader " + QuiltLoaderImpl.VERSION));
			window.addFolderViewButton(QuiltLoaderText.translate("button.open_mods_folder"), getModsDir());
			window.addOpenQuiltSupportButton();
			QuiltErrorButton continueButton = window.addContinueButton();
			continueButton.text(QuiltLoaderText.translate("button.continue_to", getGameProvider().getGameName()));
			continueButton.icon(QuiltLoaderGui.iconContinueIgnoring());
			QuiltGuiMessagesTab unknownTab = window.addMessagesTab(QuiltLoaderText.translate("tab.unknown_mods"));
			window.addTreeTab(QuiltLoaderText.translate("tab.file_list"), plugins.guiFileRoot);
			window.addTreeTab(QuiltLoaderText.translate("tab.mod_list"), plugins.guiModsRoot);

			unknownTab.level(QuiltWarningLevel.WARN);
			plugins.guiUnknownMods.values().forEach(unknownTab::addMessage);

			try {
				QuiltLoaderGui.open(window);
			} catch (LoaderGuiException e) {
				// Ignored
			}
		}

		Path absoluteGameDir = gameDir != null ? gameDir.toAbsolutePath().normalize() : null;
		Path absoluteModsDir = modsDir != null ? modsDir.toAbsolutePath().normalize() : null;

		AsciiTableGenerator table = new AsciiTableGenerator();
		AsciiTableColumn sizeColumn = table.addColumn("Size (Bytes)", true);
		AsciiTableColumn reasonColumn = table.addColumn("Type", false);
		AsciiTableColumn pathColumn = table.addColumn("Path", false);
		int count = 0;
		NumberFormat format = NumberFormat.getIntegerInstance();
		for (Map.Entry<Path, String> entry : result.getUnknownFiles().entrySet()) {
			AsciiTableRow row = table.addRow();
			Path path = entry.getKey();
			if (Files.isRegularFile(path)) {
				try {
					long byteSize = Files.size(path);
					row.put(sizeColumn, format.format(byteSize));
				} catch (IOException e) {
					Log.warn(LogCategory.DISCOVERY, "Failed to read the size of " + path, e);
					row.put(sizeColumn, "<exception>");
				}
			}
			row.put(reasonColumn, entry.getValue());
			row.put(pathColumn, prefixPath(absoluteGameDir, absoluteModsDir, path));
			count++;
		}
		for (Map.Entry<String, String> entry : result.getIrregularUnknownFiles().entrySet()) {
			AsciiTableRow row = table.addRow();
			row.put(reasonColumn, entry.getValue());
			row.put(pathColumn, entry.getKey());
			count++;
		}

		if (!table.isEmpty()) {
			Log.info(LogCategory.DISCOVERY, count + " unknown / unsupported mod files found:\n" + table);
		}

		return true;
	}

	public String createModTable() {
		StringBuilder sb = new StringBuilder();
		appendModTable(line -> {
			sb.append(line);
			sb.append("\n");
		});
		return sb.toString();
	}

	/** Appends each line of {@link #createModTable()} to the given consumer. */
	public void appendModTable(Consumer<String> to) {
		Path absoluteGameDir = gameDir != null ? gameDir.toAbsolutePath().normalize() : null;
		Path absoluteModsDir = modsDir != null ? modsDir.toAbsolutePath().normalize() : null;

		AsciiTableGenerator table = new AsciiTableGenerator();

		AsciiTableColumn index = table.addColumn("Index", true);
		AsciiTableColumn name = table.addColumn("Mod", false);
		AsciiTableColumn id = table.addColumn("ID", false);
		AsciiTableColumn version = table.addColumn("Version", false);
		AsciiTableColumn type = table.addColumn("Type", false);
		AsciiTableColumn hash = table.addColumn("File Hash (SHA-1)", false);
		AsciiTableColumn primaryFile = table.addColumn("File(s)", false);
		// Only add subFiles column if we'll actually use it
		AsciiTableColumn subFile = mods.stream().anyMatch(i -> i.getSourcePaths().stream().anyMatch(paths -> paths.size() > 1)) ? table.addColumn("Sub-File", false) : null;

		/** Map<String, ModContainerExt|ModLoadOption> */
		Map<String, Object> bestModSource = new HashMap<>();
		for (ModContainerExt mod : mods) {
			bestModSource.put(mod.metadata().id(), mod);
		}

		if (temporaryOrderedModList != null) {
			for (ModLoadOption mod : temporaryOrderedModList) {
				bestModSource.putIfAbsent(mod.id(), mod);
			}
		} else if (temporaryPluginSolveResult != null) {
			for (ModLoadOption mod : temporaryPluginSolveResult.directMods().values()) {
				bestModSource.putIfAbsent(mod.id(), mod);
			}
		}

		if (!bestModSource.containsKey(MOD_ID)) {
			AsciiTableRow row = table.addRow();
			row.put(name, "Quilt Loader");
			row.put(id, MOD_ID);
			row.put(version, VERSION);
			row.put(type, "!missing!");
		}

		Comparator<Object> comparator = Comparator.comparing(obj -> {
			if (obj instanceof ModContainerExt) {
				return ((ModContainerExt) obj).metadata().name().toLowerCase(Locale.ROOT);
			} else {
				return ((ModLoadOption) obj).metadata().name().toLowerCase(Locale.ROOT);
			}
		});

		for (Object modRepresent : bestModSource.values().stream().sorted(comparator).toArray()) {
			final int modIndex;
			final String modType;
			final ModMetadataExt metadata;
			final List<List<Path>> sourcePaths;

			if (modRepresent instanceof ModContainerExt) {
				ModContainerExt container = (ModContainerExt) modRepresent;
				modIndex = mods.indexOf(container);
				modType = container.modType();
				metadata = container.metadata();
				sourcePaths = container.getSourcePaths();
			} else {
				ModLoadOption option = (ModLoadOption) modRepresent;
				if (temporaryOrderedModList != null) {
					int idx = -1;
					for (int i = 0; i < temporaryOrderedModList.length; i++) {
						if (temporaryOrderedModList[i] == option) {
							idx = i;
							break;
						}
					}
					modIndex = idx;
				} else {
					modIndex = -1;
				}
				modType = "pl:" + option.loader().pluginId();
				metadata = option.metadata();
				if (temporarySourcePaths != null) {
					sourcePaths = temporarySourcePaths.get(option.from());
				} else {
					sourcePaths = new ArrayList<>();
				}
			}

			AsciiTableRow row = table.addRow();
			row.put(index, Integer.toString(modIndex));
			row.put(name, metadata.name());
			row.put(id, metadata.id());
			row.put(version, metadata.version().toString());
			row.put(type, modType);

			for (int pathsIndex = 0; pathsIndex < sourcePaths.size(); pathsIndex++) {
				List<Path> paths = sourcePaths.get(pathsIndex);

				Path from = paths.get(0);
				if (FasterFiles.isRegularFile(from)) {
					row.put(hash, pathOriginHash.get(from));
				}

				if (pathsIndex != 0) {
					row = table.addRow();
				}

				row.put(primaryFile, prefixPath(absoluteGameDir, absoluteModsDir, paths.get(0)));

				if (subFile != null) {
					StringBuilder subPathStr = new StringBuilder();
					Iterator<Path> pathsIter = paths.iterator();
					pathsIter.next(); // skip first element
					while (pathsIter.hasNext()) {
						subPathStr.append(prefixPath(absoluteGameDir, absoluteModsDir, pathsIter.next()));
						if (pathsIter.hasNext()) {
							subPathStr.append("!");
						}
					}

					row.put(subFile, subPathStr.toString());
				}
			}
		}

		table.appendTable(to);

		HashMap<String, Set<String>> types = new HashMap<>();

		for (ModContainerExt mod : mods) {
			types.computeIfAbsent(mod.pluginId(), k -> new HashSet<>()).add(mod.modType());
		}

		to.accept("Mod Table Version: 2");
		to.accept("Plugin Types: " + types);
	}

	public static String prefixPath(Path gameDir, Path modsDir, Path path) {
		String fsSep = path.getFileSystem().getSeparator();
		path = path.toAbsolutePath().normalize();
		if (modsDir != null && path.startsWith(modsDir)) {
			return "<mods>" + fsSep + modsDir.relativize(path);
		}
		if (gameDir != null && path.startsWith(gameDir)) {
			return "<game>" + fsSep + gameDir.relativize(path);
		}
		String pathStr = path.toString();
		String userHome = System.getProperty("user.home");
		if (userHome.isEmpty()) {
			return pathStr;
		}
		if (!userHome.endsWith(fsSep)) {
			userHome += fsSep;
		}
		if (pathStr.startsWith(userHome)) {
			return "<user>" + fsSep + pathStr.substring(userHome.length());
		}
		return pathStr;
	}

	private static void performMixinReordering(List<ModLoadOption> modList) {

		// Keep Mixin 0.9.2 compatible mods first in the load order, temporary fix for https://github.com/FabricMC/Mixin/issues/89
		List<ModLoadOption> newMixinCompatMods = new ArrayList<>();

		for (Iterator<ModLoadOption> it = modList.iterator(); it.hasNext();) {
			ModLoadOption mod = it.next();
			boolean isFabric = mod instanceof FabricModOption;
			if (QuiltMixinBootstrap.MixinConfigDecorator.getMixinCompat(isFabric, mod.metadata()) != FabricUtil.COMPATIBILITY_0_9_2) {
				it.remove();
				newMixinCompatMods.add(mod);
			}
		}

		modList.addAll(newMixinCompatMods);
	}

	private static void performLoadLateReordering(List<ModLoadOption> modList) {
		String modsToLoadLate = System.getProperty(SystemProperties.DEBUG_LOAD_LATE);

		if (modsToLoadLate != null) {

			List<ModLoadOption> lateMods = new ArrayList<>();

			for (String modId : modsToLoadLate.split(",")) {
				for (Iterator<ModLoadOption> it = modList.iterator(); it.hasNext(); ) {
					ModLoadOption mod = it.next();

					if (mod.id().equals(modId)) {
						it.remove();
						lateMods.add(mod);
						break;
					}
				}
			}

			modList.addAll(lateMods);
		}
	}

	protected void finishModLoading() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainerExt mod : mods) {
			if (mod.metadata().id().equals(MOD_ID)) {
				continue;
			}
			if (mod.shouldAddToQuiltClasspath()) {
				File jarFile = copiedToJarMods.get(mod.metadata().id());
				if (jarFile == null) {
					URL origin = null;//mod.getSourcePaths();
					QuiltLauncherBase.getLauncher().addToClassPath(mod.rootPath(), mod, origin);
				} else {
					QuiltLauncherBase.getLauncher().addToClassPath(jarFile.toPath(), mod, null);
				}
			}
		}

		if (isDevelopmentEnvironment()) {
			// Many development environments will provide classes and resources as separate directories to the classpath.
			// As such, we're adding them to the classpath here and now.
			// To avoid tripping loader-side checks, we also don't add URLs already in modsList.
			// TODO: Perhaps a better solution would be to add the Sources of all parsed entrypoints. But this will do, for now.

			Set<Path> knownModPaths = new HashSet<>();

			for (ModContainerExt mod : mods) {
				for (List<Path> paths : mod.getSourcePaths()) {
					if (paths.size() != 1) {
						continue;
					}
					knownModPaths.add(paths.get(0).toAbsolutePath().normalize());
				}
				if (mod.rootPath() instanceof QuiltJoinedPath) {
					QuiltJoinedPath joined = (QuiltJoinedPath) mod.rootPath();
					for (int i = 0; i < joined.getFileSystem().getBackingPathCount(); i++) {
						knownModPaths.add(joined.getFileSystem().getBackingPath(i, joined));
					}
				} else {
					knownModPaths.add(mod.rootPath());
				}
			}

			// suppress fabric loader explicitly in case its fabric.mod.json is in a different folder from the classes
			Path loaderPath = ClasspathModCandidateFinder.getLoaderPath();
			if (loaderPath != null) knownModPaths.add(loaderPath.toAbsolutePath().normalize());

			Path gameProviderPath = ClasspathModCandidateFinder.getGameProviderPath();
			if (gameProviderPath != null) knownModPaths.add(gameProviderPath.toAbsolutePath().normalize());

			for (String pathName : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
				if (pathName.isEmpty() || pathName.endsWith("*")) continue;

				Path path = Paths.get(pathName).toAbsolutePath().normalize();

				if (FasterFiles.isDirectory(path) && knownModPaths.add(path)) {
					QuiltLauncherBase.getLauncher().addToClassPath(path);
				}
			}
		}

		postprocessModMetadata();
		PatchLoader.load();
		setupLanguageAdapters();
		setupMods();
	}

	public boolean hasEntrypoints(String key) {
		return entrypointStorage.hasEntrypoints(key);
	}

	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		return entrypointStorage.getEntrypoints(key, type);
	}

	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		return entrypointStorage.getEntrypointContainers(key, type);
	}

	public MappingResolver getMappingResolver() {
		if (mappingResolver == null) {
			mappingResolver = new QuiltMappingResolver(
				QuiltLauncherBase.getLauncher().getMappingConfiguration()::getMappings,
				QuiltLauncherBase.getLauncher().getTargetNamespace()
			);
		}

		return mappingResolver;
	}

	public Optional<org.quiltmc.loader.api.ModContainer> getModContainer(String id) {
		return Optional.ofNullable(modMap.get(id));
	}

	public Optional<org.quiltmc.loader.api.ModContainer> getModContainer(Class<?> clazz) {
		ProtectionDomain pd = clazz.getProtectionDomain();
		if (pd != null) {
			CodeSource codeSource = pd.getCodeSource();
			if (codeSource instanceof QuiltCodeSource) {
				return ((QuiltCodeSource) codeSource).getQuiltMod();
			}
		}
		return Optional.empty();
	}

	// TODO: add to QuiltLoader api
	public ObjectShare getObjectShare() {
		return objectShare;
	}

	public Collection<org.quiltmc.loader.api.ModContainer> getAllMods() {
		return Collections.unmodifiableList(mods);
	}

	public Collection<ModContainerExt> getAllModsExt() {
		return Collections.unmodifiableList(mods);
	}

	public boolean isModLoaded(String id) {
		return modMap.containsKey(id);
	}

	public boolean isDevelopmentEnvironment() {
		QuiltLauncher launcher = QuiltLauncherBase.getLauncher();
		if (launcher == null) {
			// Most likely a test
			return true;
		}
		return launcher.isDevelopment();
	}

	protected void addMod(ModContainerExt mod) throws ModResolutionException {
		ModMetadataExt meta = mod.metadata();

		if (modMap.containsKey(meta.id())) {
			throw new ModSolvingError("Duplicate mod ID: " + meta.id() + "!"/* + " (" + modMap.get(meta.id()).getOriginPath().toFile() + ", " + origin + ")"*/);
		}

		mods.add(mod);
		modMap.put(meta.id(), mod);

		for (ProvidedMod provided : meta.provides()) {
			if (modMap.containsKey(provided.id())) {
				throw new ModSolvingError("Duplicate provided alias: " + provided + "!" /*+ " (" + modMap.get(meta.id()).getOriginPath().toFile() + ", " + origin + ")"*/);
			}

			modMap.put(provided.id(), new ProvidedModContainer(new ProvidedModMetadata(provided, meta), mod));
		}
	}

	protected void postprocessModMetadata() {
		// do nothing for now; most warnings have been moved to V1ModMetadataParser
	}

	private void setupLanguageAdapters() {
		adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

		for (ModContainerExt mod : mods) {
			// add language adapters
			for (Map.Entry<String, String> laEntry : mod.metadata().languageAdapters().entrySet()) {
				if (adapterMap.containsKey(laEntry.getKey())) {
					throw new RuntimeException("Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " + adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
				}

				try {
					ClassLoader classLoader = QuiltLauncherBase.getLauncher().getClassLoader(mod);
					Class<?> adapterClass = Class.forName(laEntry.getValue(), true, classLoader);
					adapterMap.put(laEntry.getKey(), (LanguageAdapter) adapterClass.getDeclaredConstructor().newInstance());
				} catch (Exception e) {
					throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
				}
			}
		}
	}

	private void setupMods() {
		for (ModContainerExt mod : mods) {
			try {
				if (mod.getSourceType() == BasicSourceType.NORMAL_FABRIC) {
					FabricLoaderModMetadata fabricMeta = ((InternalModMetadata) mod.metadata()).asFabricModMetadata(mod);
					for (String in : fabricMeta.getOldInitializers()) {
						String adapter = fabricMeta.getOldStyleLanguageAdapter();
						entrypointStorage.addDeprecated(mod, adapter, in);
					}
				}

				for (Map.Entry<String, Collection<ModEntrypoint>> entry : mod.metadata().getEntrypoints().entrySet()) {
					for (ModEntrypoint e : entry.getValue()) {
						entrypointStorage.add(mod, entry.getKey(), e, adapterMap);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.metadata().name(), mod.rootPath()), e);
			}
		}
	}



	public void prepareModInit(Path newRunDir, Object gameInstance) {
		if (!frozen) {
			throw new RuntimeException("Cannot instantiate mods when not frozen!");
		}

		if (gameInstance != null) {
			QuiltLauncherBase.getLauncher().validateGameClassLoader(gameInstance);
		}

		this.gameInstance = gameInstance;

		if (gameDir != null) {
			try {
				if (!gameDir.toRealPath().equals(newRunDir.toRealPath())) {
					Log.warn(LogCategory.GENERAL, "Inconsistent game execution directories: engine says %s, while initializer says %s...",
							newRunDir.toRealPath(), gameDir.toRealPath());
					setGameDir(newRunDir);
				}
			} catch (IOException e) {
				Log.warn(LogCategory.GENERAL, "Exception while checking game execution directory consistency!", e);
			}
		} else {
			setGameDir(newRunDir);
		}
	}

	public void invokePreLaunch() {
		try {
			EntrypointUtils.invoke("pre_launch", org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint.class, org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint::onPreLaunch);
			EntrypointUtils.invoke("preLaunch", net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint.class, net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint::onPreLaunch);
		} catch (RuntimeException e) {
			throw new FormattedException("A mod crashed on startup!", e);
		}
	}

	/**
	 * Sets the game instance. This is only used in 20w22a+ by the dedicated server and should not be called by anything else.
	 */
	@Deprecated
	public void setGameInstance(Object gameInstance) {
		if (this.getEnvironmentType() != EnvType.SERVER) {
			throw new UnsupportedOperationException("Cannot set game instance on a client!");
		}

		if (this.gameInstance != null) {
			throw new UnsupportedOperationException("Cannot overwrite current game instance!");
		}

		this.gameInstance = gameInstance;
	}

	public String[] getLaunchArguments(boolean sanitize) {
		return getGameProvider().getLaunchArguments(sanitize);
	}

	/**
	 * Provides singleton for static init assignment regardless of load order.
	 */
	public static class InitHelper {
		private static QuiltLoaderImpl instance;

		public static QuiltLoaderImpl get() {
			if (instance == null) instance = new QuiltLoaderImpl();

			return instance;
		}
	}
}
