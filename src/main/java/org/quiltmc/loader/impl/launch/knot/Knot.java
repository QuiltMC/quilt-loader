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

package org.quiltmc.loader.impl.launch.knot;

import net.fabricmc.api.EnvType;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModContainer.BasicSourceType;
import org.quiltmc.loader.impl.FormattedException;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.config.QuiltConfigImpl;
import org.quiltmc.loader.impl.entrypoint.EntrypointUtils;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.launch.common.QuiltMixinBootstrap;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.spongepowered.asm.launch.MixinBootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class Knot extends QuiltLauncherBase {
	protected Map<String, Object> properties = new HashMap<>();

	private KnotClassLoaderInterface classLoader;
	private boolean isDevelopment;
	private EnvType envType;
	private final List<Path> classPath = new ArrayList<>();
	private GameProvider provider;
	private boolean unlocked;

	public static void launch(String[] args, EnvType type) {
		setupUncaughtExceptionHandler();

		try {
			Knot knot = new Knot(type);
			ClassLoader cl = knot.init(args);

			if (knot.provider == null) {
				throw new IllegalStateException("Game provider was not initialized! (Knot#init(String[]))");
			}

			knot.provider.launch(cl);
		} catch (FormattedException e) {
			handleFormattedException(e);
		}
	}

	public Knot(EnvType type) {
		this.envType = type;
	}

	public ClassLoader init(String[] args) {
		setProperties(properties);

		// configure fabric vars
		if (envType == null) {
			String side = System.getProperty(SystemProperties.SIDE);
			if (side == null) throw new RuntimeException("Please specify side or use a dedicated Knot!");

			switch (side.toLowerCase(Locale.ROOT)) {
			case "client":
				envType = EnvType.CLIENT;
				break;
			case "server":
				envType = EnvType.SERVER;
				break;
			default:
				throw new RuntimeException("Invalid side provided: must be \"client\" or \"server\"!");
			}
		}

		classPath.clear();

		for (String cpEntry : System.getProperty("java.class.path").split(File.pathSeparator)) {
			if (cpEntry.equals("*") || cpEntry.endsWith(File.separator + "*")) {
				Log.warn(LogCategory.KNOT, "Knot does not support wildcard classpath entries: %s - the game may not load properly!", cpEntry);
				continue;
			}

			Path path = Paths.get(cpEntry);

			if (!Files.exists(path)) {
				Log.warn(LogCategory.KNOT, "Class path entry %s doesn't exist!", cpEntry);
				continue;
			}

			classPath.add(path);
		}

		provider = createGameProvider(args);
		Log.info(LogCategory.GAME_PROVIDER, "Loading %s %s with Quilt Loader %s", provider.getGameName(), provider.getRawGameVersion(), QuiltLoaderImpl.VERSION);

		isDevelopment = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));

		// Setup classloader
		// TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
		boolean useCompatibility = provider.requiresUrlClassLoader() || Boolean.parseBoolean(System.getProperty("fabric.loader.useCompatibilityClassLoader", "false"));
		classLoader = useCompatibility ? new KnotCompatibilityClassLoader(isDevelopment(), envType, provider) : new KnotClassLoader(isDevelopment(), envType, provider);
		ClassLoader cl = (ClassLoader) classLoader;

		provider.initialize(this);

		Thread.currentThread().setContextClassLoader(cl);

		QuiltLoaderImpl loader = QuiltLoaderImpl.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		loader.freeze();

		QuiltLoaderImpl.INSTANCE.loadAccessWideners();

		MixinBootstrap.init();
		QuiltMixinBootstrap.init(getEnvironmentType(), loader);
		QuiltLauncherBase.finishMixinBootstrapping();

		classLoader.getDelegate().initializeTransformers();

		provider.unlockClassPath(this);
		unlocked = true;

		QuiltConfigImpl.init();

		try {
			// If the very first class transformed by mixin is also referenced by a mixin config
			// then we'll crash due to an "attempted duplicate class definition"
			// Since this target class is *very unlikely* to be referenced by mixin we forcibly load it.
			classLoader.loadIntoTarget("org.quiltmc.loader.impl.launch.knot.UnusedEmptyTargetClass");
		} catch (ClassNotFoundException cnfe) {
			Log.warn(LogCategory.KNOT, "Early non-mixin-config related class failed to load!");
			Log.warn(LogCategory.KNOT, "If you get a 'LinkageError' of 'attempted duplicated * definition' after this then this error is the cause!", cnfe);
		}

		classLoader.getDelegate().afterMixinIntiializeFinished();

		loader.invokePreLaunch();

		return cl;
	}

	private GameProvider createGameProvider(String[] args) {
		// fast path with direct lookup

		GameProvider embeddedGameProvider = findEmbedddedGameProvider();

		if (embeddedGameProvider != null
				&& embeddedGameProvider.isEnabled()
				&& embeddedGameProvider.locateGame(this, args)) {
			return embeddedGameProvider;
		}

		// slow path with service loader

		List<GameProvider> failedProviders = new ArrayList<>();

		for (GameProvider provider : ServiceLoader.load(GameProvider.class)) {
			if (!provider.isEnabled()) continue; // don't attempt disabled providers and don't include them in the error report

			if (provider != embeddedGameProvider // don't retry already failed provider
					&& provider.locateGame(this, args)) {
				return provider;
			}

			failedProviders.add(provider);
		}

		// nothing found

		String msg;

		if (failedProviders.isEmpty()) {
			msg = "No game providers present on the class path!";
		} else if (failedProviders.size() == 1) {
			msg = String.format("%s game provider couldn't locate the game! "
					+ "The game may be absent from the class path, lacks some expected files, suffers from jar "
					+ "corruption or is of an unsupported variety/version.",
					failedProviders.get(0).getGameName());
		} else {
			msg = String.format("None of the game providers (%s) were able to locate their game!",
					failedProviders.stream().map(GameProvider::getGameName).collect(Collectors.joining(", ")));
		}

		Log.error(LogCategory.GAME_PROVIDER, msg);

		throw new RuntimeException(msg);
	}

	/**
	 * Find game provider embedded into the Fabric Loader jar, best effort.
	 *
	 * <p>This is faster than going through service loader because it only looks at a single jar.
	 */
	private static GameProvider findEmbedddedGameProvider() {
		try {
			Path flPath = UrlUtil.asPath(Knot.class.getProtectionDomain().getCodeSource().getLocation());
			if (!flPath.getFileName().toString().endsWith(".jar")) return null; // not a jar

			try (ZipFile zf = new ZipFile(flPath.toFile())) {
				ZipEntry entry = zf.getEntry("META-INF/services/org.quiltmc.loader.impl.game.GameProvider"); // same file as used by service loader
				if (entry == null) return null;

				try (InputStream is = zf.getInputStream(entry)) {
					byte[] buffer = FileUtil.readAllBytes(is);

					String content = new String(buffer, 0, buffer.length, StandardCharsets.UTF_8).trim();
					if (content.indexOf('\n') >= 0) return null; // potentially more than one entry -> bail out

					int pos = content.indexOf('#');
					if (pos >= 0) content = content.substring(0, pos).trim();

					if (!content.isEmpty()) {
						return (GameProvider) Class.forName(content).getConstructor().newInstance();
					}
				}
			}

			return null;
		} catch (IOException | ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getTargetNamespace() {
		// TODO: Won't work outside of Yarn
		return isDevelopment ? "named" : "intermediary";
	}

	@Override
	public List<Path> getClassPath() {
		return classPath;
	}

	@Override
	public void addToClassPath(Path path, String... allowedPrefixes) {
		addToClassPath(path, null, null, allowedPrefixes);
	}

	@Override
	public void addToClassPath(Path path, ModContainer mod, URL origin, String... allowedPrefixes) {
		Log.debug(LogCategory.KNOT, "Adding " + path + " to classpath.");

		try {
			URL url = UrlUtil.asUrl(path);
			classLoader.getDelegate().setAllowedPrefixes(url, allowedPrefixes);
			classLoader.addPath(path, mod, origin); // TODO: Create a method which passes the actual origin!
			classLoader.getDelegate().hideParentUrl(url);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setAllowedPrefixes(Path path, String... prefixes) {
		try {
			classLoader.getDelegate().setAllowedPrefixes(UrlUtil.asUrl(path), prefixes);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setTransformCache(URL insideTransformCache) {
		classLoader.getDelegate().setTransformCache(insideTransformCache);
	}

	@Override
	public void hideParentUrl(URL parent) {
		classLoader.getDelegate().hideParentUrl(parent);
	}

	@Override
	public void hideParentPath(Path obf) {
		try {
			classLoader.getDelegate().hideParentUrl(UrlUtil.asUrl(obf));
		} catch (MalformedURLException e) {
			Log.warn(LogCategory.GENERAL, "Unable to convert " + obf + " to a URL in Knot.hideParentPath");
		}
	}

	@Override
	public void validateGameClassLoader(Object gameInstance) {
		ClassLoader gameClassLoader = gameInstance.getClass().getClassLoader();
		ClassLoader targetClassLoader = QuiltLauncherBase.getLauncher().getTargetClassLoader();
		boolean matchesKnot = isMatchingClassLoader(targetClassLoader, gameClassLoader);
		boolean containsKnot = false;

		if (matchesKnot) {
			containsKnot = true;
		} else {
			ClassLoader parentClassLoader = gameClassLoader.getParent();

			while (parentClassLoader != null && parentClassLoader.getParent() != parentClassLoader) {
				if (isMatchingClassLoader(targetClassLoader, parentClassLoader)) {
					containsKnot = true;
					break;
				}

				parentClassLoader = parentClassLoader.getParent();
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

	private static boolean isMatchingClassLoader(ClassLoader expected, ClassLoader actual) {
//		if (actual instanceof KnotSeparateClassLoader) {
//			return ((KnotSeparateClassLoader) actual).getBaseClassLoader() == expected;
//		}
		return expected == actual;
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return classLoader.isClassLoaded(name);
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		return classLoader.loadIntoTarget(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			return classLoader.getResourceAsStream(name, false);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file '" + name + "'!", e);
		}
	}

	@Override
	public URL getResourceURL(String name) {
		return classLoader.getResource(name, false);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return (ClassLoader) classLoader;
	}

	@Override
	public ClassLoader getClassLoader(ModContainer mod) {
		if (mod.getSourceType() == BasicSourceType.BUILTIN) {
			return null;
		}
		return getTargetClassLoader();
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		if (!unlocked) throw new IllegalStateException("early getClassByteArray access");

		if (runTransformers) {
			return classLoader.getDelegate().getPreMixinClassByteArray(name, true);
		} else {
			return classLoader.getDelegate().getRawClassByteArray(name, true);
		}
	}

	@Override
	public Manifest getManifest(Path originPath) {
		try {
			return classLoader.getDelegate().getMetadata(UrlUtil.asUrl(originPath)).manifest;
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}

	@Override
	public String getEntrypoint() {
		return provider.getEntrypoint();
	}

	public static void main(String[] args) {
		new Knot(null).init(args);
	}
}
