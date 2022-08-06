/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.ObjectShare;

import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.ModContainer.BasicSourceType;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult.SpecificLoadOptionResult;
import org.quiltmc.loader.impl.discovery.ClasspathModCandidateFinder;
import org.quiltmc.loader.impl.discovery.ModCandidate;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.entrypoint.EntrypointStorage;
import org.quiltmc.loader.impl.entrypoint.EntrypointUtils;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltJoinedPath;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.gui.QuiltGuiEntry;
import org.quiltmc.loader.impl.gui.QuiltStatusTree;
import org.quiltmc.loader.impl.launch.common.QuiltLauncher;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.launch.common.QuiltMixinBootstrap;
import org.quiltmc.loader.impl.launch.knot.Knot;

import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.plugin.QuiltPluginManagerImpl;
import org.quiltmc.loader.impl.plugin.fabric.FabricModOption;
import org.quiltmc.loader.impl.transformer.TransformCache;
import org.quiltmc.loader.impl.util.DefaultLanguageAdapter;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.ModLanguageAdapter;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.util.sat4j.specs.TimeoutException;
import org.spongepowered.asm.mixin.FabricUtil;

public final class QuiltLoaderImpl {
	public static final QuiltLoaderImpl INSTANCE = InitHelper.get();

	public static final int ASM_VERSION = Opcodes.ASM9;

	public static final String VERSION = "0.17.2-beta.1";
	public static final String MOD_ID = "quilt_loader";
	public static final String DEFAULT_MODS_DIR = "mods";
	public static final String DEFAULT_CONFIG_DIR = "config";

	public static final String CACHE_DIR_NAME = ".quilt"; // relative to game dir
	private static final String PROCESSED_MODS_DIR_NAME = "processedMods"; // relative to cache dir
	public static final String REMAPPED_JARS_DIR_NAME = "remappedJars"; // relative to cache dir
	private static final String TMP_DIR_NAME = "tmp"; // relative to cache dir

	protected final Map<String, ModContainerExt> modMap = new HashMap<>();

	@Deprecated
	private List<ModCandidate> modCandidates;
	protected List<ModContainerExt> mods = new ArrayList<>();

	private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();
	private final AccessWidener accessWidener = new AccessWidener();

	private final ObjectShare objectShare = new ObjectShareImpl();

	private boolean frozen = false;

	private Object gameInstance;

	private MappingResolver mappingResolver;
	private GameProvider provider;
	private Path gameDir;
	private Path configDir;
	private Path modsDir;

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
	}

	private void setGameDir(Path gameDir) {
		this.gameDir = gameDir;
		String configDir = System.getProperty(SystemProperties.CONFIG_DIRECTORY);
		this.configDir = gameDir.resolve((configDir == null || configDir.isEmpty()) ? DEFAULT_CONFIG_DIR : configDir);
		initializeModsDir(gameDir);
	}

	private void initializeModsDir(Path gameDir) {
		String modsDir = System.getProperty(SystemProperties.MODS_DIRECTORY);
		this.modsDir = gameDir.resolve((modsDir == null || modsDir.isEmpty()) ? DEFAULT_MODS_DIR : modsDir);
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

	/**
	 * @return The game instance's configuration directory.
	 */
	public Path getConfigDir() {
		if (configDir == null) {
			// May be null during tests
			// If this is in production then things are about to go very wrong.
			return null;
		}

		if (!Files.exists(configDir)) {
			try {
				Files.createDirectories(configDir);
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to create config directory at '%s'", configDir), e);
			}
		}

		return configDir;
	}

	public Path getModsDir() {
		// modsDir should be initialized before this method is ever called, this acts as a very special failsafe
		if (modsDir == null) {
			initializeModsDir(gameDir);
		}

		if (!Files.exists(modsDir)) {
			try {
				Files.createDirectories(modsDir);
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to create mods directory at '%s'", modsDir), e);
			}
		}
		return modsDir;
	}

	public void load() {
		if (provider == null) throw new IllegalStateException("game provider not set");
		if (frozen) throw new IllegalStateException("Frozen - cannot load additional mods!");

		try {
			setup();
		} catch (ModResolutionException exception) {
			throw new FormattedException("Incompatible mod set!", exception);
		}
	}

	private void setup() throws ModResolutionException {

		QuiltPluginManagerImpl plugins = new QuiltPluginManagerImpl(getModsDir(), provider, new QuiltLoaderConfig());

		ModSolveResult result;
		try {
			result = plugins.run(true);
		} catch (TimeoutException e) {
			throw new ModSolvingError("Timeout", e);
		}

		QuiltStatusTree tree = new QuiltStatusTree("Quilt Loader", "test");
		QuiltStatusTree.QuiltStatusTab tab = tree.addTab("Plugins Test");
		plugins.guiFileRoot.toNode(tab.node, false);
		try {
			QuiltGuiEntry.open(tree, null, true);
		} catch (Exception e) {
			throw new Error(e);
		}

		SpecificLoadOptionResult<LoadOption> spec = result.getResult(LoadOption.class);

		// Debugging
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

		List<ModLoadOption> modList = new ArrayList<>(result.directMods().values());

		performMixinReordering(modList);
		performLoadLateReordering(modList);

		Path transformCacheFile = getGameDir().resolve(CACHE_DIR_NAME).resolve("transform-cache.zip");
		TransformCache.populateTransformBundle(transformCacheFile, modList, result);
		Path transformedModBundle;
		try {
			transformedModBundle = FileSystemUtil.getJarFileSystem(transformCacheFile, false).get().getPath("/");
		} catch (IOException e) {
			throw new RuntimeException(e); // TODO
		}
		for (ModLoadOption modOption : modList) {

			final Path resourceRoot;

			if (!modOption.needsChasmTransforming() && modOption.namespaceMappingFrom() == null) {
				resourceRoot = modOption.resourceRoot();
			} else {
				Path modTransformed = transformedModBundle.resolve(modOption.id() + "/");
				Path excluded = transformedModBundle.resolve(modOption.id() + ".removed");

				Path from = modOption.resourceRoot();

				if (Files.exists(excluded)) {
					throw new Error("// TODO: Implement pre-transform file removal!");
				} else if (!Files.isDirectory(modTransformed)) {
					resourceRoot = modOption.resourceRoot();
				} else {
					try {
						modTransformed = new QuiltMemoryFileSystem.ReadOnly("transformed-stuff-" + modOption.id(), modTransformed).getRoot();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					List<Path> paths = new ArrayList<>();

					paths.add(modTransformed);
					paths.add(modOption.resourceRoot());

					String fsName = QuiltJoinedFileSystem.uniqueOf("final-mod-" + modOption.id());
					resourceRoot = new QuiltJoinedFileSystem(fsName, paths).getRoot();
				}
			}

			addMod(modOption.convertToMod(resourceRoot));
		}
		// TODO (in no particular order):
		// - turn ModLoadOptions into real mods, and pass them into addMod()
		// - - which does:
		// - - Double-checks for duplicates
		// - - Rejects non-environment-matching mods (I'm less sure about this now)
		// - - Creates the container
		// - put the mod containing in the mod list & map
		// - print mod list

		StringBuilder modListText = new StringBuilder();

		for (ModLoadOption option : modList) {
			if (modListText.length() > 0) modListText.append('\n');
			modListText.append(option.id()).append(' ').append(option.version());
			modListText.append(" from plugin (").append(option.loader().pluginId()).append(')');

		}
//		for (ModContainerExt mod : mods.stream().sorted(Comparator.comparing(i -> i.metadata().id())).collect(Collectors.toList())) {
//			if (modListText.length() > 0) modListText.append('\n');
//
//			modListText.append("\t- ");
//			modListText.append(mod.metadata().id());
//			modListText.append(' ');
//			modListText.append(mod.metadata().version());
//			mod
//		}

		int count = modList.size();
		Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", count, count != 1 ? "s" : "", modListText);
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

//	private void oldSetup() throws ModResolutionException {
//		ModResolver resolver = new ModResolver(this);
//		resolver.addCandidateFinder(new ClasspathModCandidateFinder());
//		resolver.addCandidateFinder(new ArgumentModCandidateFinder(isDevelopmentEnvironment()));
//		resolver.addCandidateFinder(new DirectoryModCandidateFinder(getModsDir(), isDevelopmentEnvironment()));
//		ModSolveResult result = resolver.resolve(this);
//		Map<String, ModCandidate> candidateMap = (Map<String, ModCandidate>) (Object) "nope";//result.modMap;
//		modCandidates = new ArrayList<>(candidateMap.values());
//		// dump mod list
//
//		StringBuilder modListText = new StringBuilder();
//
//		for (ModCandidate mod : modCandidates.stream().sorted(Comparator.comparing(ModCandidate::getId)).collect(Collectors.toList())) {
//			if (modListText.length() > 0) modListText.append('\n');
//
//			modListText.append("\t- ");
//			modListText.append(mod.getId());
//			modListText.append(' ');
//			modListText.append(mod.getVersion().raw());
//// TODO
////			if (!mod.getParentMods().isEmpty()) {
////				modListText.append(" via ");
////				modListText.append(mod.getParentMods().iterator().next().getId());
////			}
//		}
//
//		int count = modCandidates.size();
//		Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", count, count != 1 ? "s" : "", modListText);
//
//		// TODO
////		if (DependencyOverrides.INSTANCE.getDependencyOverrides().size() > 0) {
////			Log.info(LogCategory.GENERAL, "Dependencies overridden for \"%s\"", String.join(", ", DependencyOverrides.INSTANCE.getDependencyOverrides()));
////		}
//
//		Path cacheDir = gameDir.resolve(CACHE_DIR_NAME);
//		Path outputdir = cacheDir.resolve(PROCESSED_MODS_DIR_NAME);
//
//		// runtime mod remapping
//
//		if (isDevelopmentEnvironment()) {
//			if (System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
//				Log.warn(LogCategory.MOD_REMAP, "Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified. You may need to update loom.");
//			} else {
//				RuntimeModRemapper.remap(modCandidates, ModResolver.getInMemoryFs());
//			}
//		}
//
//		// Keep Mixin 0.9.2 compatible mods first in the load order, temporary fix for https://github.com/FabricMC/Mixin/issues/89
//		List<ModCandidate> newMixinCompatMods = new ArrayList<>();
//		for (Iterator<ModCandidate> it = modCandidates.iterator(); it.hasNext();) {
//			ModCandidate mod = it.next();
//			ModContainerImpl tempModContainer = new ModContainerImpl(mod);
//			if (QuiltMixinBootstrap.MixinConfigDecorator.getMixinCompat(tempModContainer) != FabricUtil.COMPATIBILITY_0_9_2) {
//				it.remove();
//				newMixinCompatMods.add(mod);
//			}
//		}
//
//		modCandidates.addAll(newMixinCompatMods);
//
//		String modsToLoadLate = System.getProperty(SystemProperties.DEBUG_LOAD_LATE);
//
//		if (modsToLoadLate != null) {
//			for (String modId : modsToLoadLate.split(",")) {
//				for (Iterator<ModCandidate> it = modCandidates.iterator(); it.hasNext(); ) {
//					ModCandidate mod = it.next();
//
//					if (mod.getId().equals(modId)) {
//						it.remove();
//						modCandidates.add(mod);
//						break;
//					}
//				}
//			}
//		}
//
//		// add mods
//
//		for (ModCandidate mod : modCandidates) {
////			if (!mod.hasPath() && !mod.isBuiltin()) {
////				try {
////					mod.setPaths(Collections.singletonList(mod.copyToDir(outputdir, false)));
////				} catch (IOException e) {
////					throw new RuntimeException("Error extracting mod "+mod, e);
////				}
////			}
//
//			addMod(mod);
//		}
//
//		//modCandidates = null;
//	}

	protected void finishModLoading() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainerExt mod : mods) {
			if (!mod.metadata().id().equals(MOD_ID) && mod.getSourceType() != BasicSourceType.BUILTIN) {
				QuiltLauncherBase.getLauncher().addToClassPath(mod.rootPath());
			}
		}

		if (isDevelopmentEnvironment()) {
			// Many development environments will provide classes and resources as separate directories to the classpath.
			// As such, we're adding them to the classpath here and now.
			// To avoid tripping loader-side checks, we also don't add URLs already in modsList.
			// TODO: Perhaps a better solution would be to add the Sources of all parsed entrypoints. But this will do, for now.

			Set<Path> knownModPaths = new HashSet<>();

			for (ModContainerExt mod : mods) {
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

			for (String pathName : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
				if (pathName.isEmpty() || pathName.endsWith("*")) continue;

				Path path = Paths.get(pathName).toAbsolutePath().normalize();

				if (Files.isDirectory(path) && knownModPaths.add(path)) {
					QuiltLauncherBase.getLauncher().addToClassPath(path);
				}
			}
		}

		postprocessModMetadata();
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

	// TODO: add to QuiltLoader api
	public ObjectShare getObjectShare() {
		return objectShare;
	}

	public ModCandidate getModCandidate(String id) {
		if (modCandidates == null) return null;

		for (ModCandidate mod : modCandidates) {
			if (mod.getId().equals(id)) return mod;
		}

		return null;
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

			modMap.put(provided.id(), mod);
		}
	}

//	@Deprecated
//	protected void addMod(ModCandidate candidate) throws ModResolutionException {
//		InternalModMetadata meta = candidate.getMetadata();
//		if (modMap.containsKey(meta.id())) {
//			throw new ModSolvingError("Duplicate mod ID: " + meta.id() + "!"/* + " (" + modMap.get(meta.id()).getOriginPath().toFile() + ", " + origin + ")"*/);
//		}
//
//		if (!meta.environment().matches(getEnvironmentType())) {
//			if (candidate.getDepth() < 1) {
//				Log.warn(LogCategory.DISCOVERY, "Not loading mod " + meta.id()
//						+ " because its environment is " + meta.environment().name().toLowerCase() + "!");
//			} else {
//
//				Log.debug(LogCategory.DISCOVERY, "Not loading mod " + meta.id() + "(from " + ModResolver.getReadablePath(this, candidate) + ") "
//						+ " because its environment is " + meta.environment().name().toLowerCase() + "!");
//
//			}
//			return;
//		}
//
//		ModContainerImpl container = new ModContainerImpl(candidate);
//		mods.add(container);
//		modMap.put(meta.id(), container);
//
//		for (ProvidedMod provided : meta.provides()) {
//			if (modMap.containsKey(provided.id())) {
//				throw new ModSolvingError("Duplicate provided alias: " + provided + "!" /*+ " (" + modMap.get(meta.id()).getOriginPath().toFile() + ", " + origin + ")"*/);
//			}
//
//			modMap.put(provided.id(), container);
//		}
//	}

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
					adapterMap.put(laEntry.getKey(), new ModLanguageAdapter(mod, laEntry.getKey(), laEntry.getValue()));
//					adapterMap.put(laEntry.getKey(), (LanguageAdapter) Class.forName(laEntry.getValue(), true, QuiltLauncherBase.getLauncher().getTargetClassLoader()).getDeclaredConstructor().newInstance());
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
					FabricLoaderModMetadata fabricMeta = ((InternalModMetadata) mod.metadata()).asFabricModMetadata();
					for (String in : fabricMeta.getOldInitializers()) {
						String adapter = fabricMeta.getOldStyleLanguageAdapter();
						entrypointStorage.addDeprecated(mod, adapter, in);
					}
				}

				for (Map.Entry<String, Collection<AdapterLoadableClassEntry>> entry : mod.metadata().getEntrypoints().entrySet()) {
					for (AdapterLoadableClassEntry e : entry.getValue()) {
						entrypointStorage.add(mod, entry.getKey(), e, adapterMap);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.metadata().name(), mod.rootPath()), e);
			}
		}
	}

	public void loadAccessWideners() {
		AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);

		for (ModContainerExt mod : mods) {
			for (String accessWidener : mod.metadata().accessWideners()) {

				Path path = mod.getPath(accessWidener);

				if (!Files.isRegularFile(path)) {
					throw new RuntimeException("Failed to find accessWidener file from mod " + mod.metadata().id() + " '" + accessWidener + "'");
				}

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					accessWidenerReader.read(reader, getMappingResolver().getCurrentRuntimeNamespace());
				} catch (Exception e) {
					throw new RuntimeException("Failed to read accessWidener file from mod " + mod.metadata().id(), e);
				}
			}
		}
	}

	public void prepareModInit(Path newRunDir, Object gameInstance) {
		if (!frozen) {
			throw new RuntimeException("Cannot instantiate mods when not frozen!");
		}

		if (gameInstance != null && QuiltLauncherBase.getLauncher() instanceof Knot) {
			ClassLoader gameClassLoader = gameInstance.getClass().getClassLoader();
			ClassLoader targetClassLoader = QuiltLauncherBase.getLauncher().getTargetClassLoader();
			boolean matchesKnot = (gameClassLoader == targetClassLoader);
			boolean containsKnot = false;

			if (matchesKnot) {
				containsKnot = true;
			} else {
				gameClassLoader = gameClassLoader.getParent();

				while (gameClassLoader != null && gameClassLoader.getParent() != gameClassLoader) {
					if (gameClassLoader == targetClassLoader) {
						containsKnot = true;
					}

					gameClassLoader = gameClassLoader.getParent();
				}
			}

			if (!matchesKnot) {
				if (containsKnot) {
					Log.info(LogCategory.KNOT, "Environment: Target class loader is parent of game class loader.");
				} else {
					Log.warn(LogCategory.KNOT, "\n\n* CLASS LOADER MISMATCH! THIS IS VERY BAD AND WILL PROBABLY CAUSE WEIRD ISSUES! *\n"
							+ " - Expected game class loader: %s\n"
							+ " - Actual game class loader: %s\n"
							+ "Could not find the expected class loader in game class loader parents!\n",
							QuiltLauncherBase.getLauncher().getTargetClassLoader(), gameClassLoader);
				}
			}
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

		for (LanguageAdapter adapter : adapterMap.values()) {
			if (adapter instanceof ModLanguageAdapter) {
				((ModLanguageAdapter) adapter).init();
			}
		}
	}

	public AccessWidener getAccessWidener() {
		return accessWidener;
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
