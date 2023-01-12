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

package org.quiltmc.loader.impl.launch.knot;

import net.fabricmc.api.EnvType;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.objectweb.asm.ClassReader;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.launch.common.QuiltCodeSource;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.transformer.PackageEnvironmentStrippingData;
import org.quiltmc.loader.impl.transformer.QuiltTransformer;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.ManifestUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
class KnotClassDelegate {
	static class Metadata {
		static final Metadata EMPTY = new Metadata(null, null);

		final Manifest manifest;
		final CodeSourceImpl codeSource;

		Metadata(Manifest manifest, CodeSourceImpl codeSource) {
			this.manifest = manifest;
			this.codeSource = codeSource;
		}
	}

	static class CodeSourceImpl extends CodeSource implements QuiltCodeSource {
		final ModContainer mod;

		public CodeSourceImpl(URL url, Certificate[] certs, ModContainer mod) {
			super(url, certs);
			this.mod = mod;
		}

		@Override
		public Optional<ModContainer> getQuiltMod() {
			return Optional.ofNullable(mod);
		}
	}

	private static final boolean LOG_EARLY_CLASS_LOADS = Boolean.getBoolean(SystemProperties.LOG_EARLY_CLASS_LOADS);

	private final Map<String, Metadata> metadataCache = new ConcurrentHashMap<>();
	private final KnotClassLoaderInterface itf;
	private final GameProvider provider;
	private final boolean isDevelopment;
	private final EnvType envType;
	private IMixinTransformer mixinTransformer;
	private boolean transformInitialized = false;
	private boolean transformFinishedLoading = false;
	private final Map<String, String[]> allowedPrefixes = new ConcurrentHashMap<>();
	private final Set<String> parentSourcedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/** Map of package to whether we can load it in this environment. */
	private final Map<String, Boolean> packageSideCache = new ConcurrentHashMap<>();

	KnotClassDelegate(boolean isDevelopment, EnvType envType, KnotClassLoaderInterface itf, GameProvider provider) {
		this.isDevelopment = isDevelopment;
		this.envType = envType;
		this.itf = itf;
		this.provider = provider;
	}

	public void initializeTransformers() {
		if (transformInitialized) throw new IllegalStateException("Cannot initialize KnotClassDelegate twice!");

		mixinTransformer = MixinServiceKnot.getTransformer();

		if (mixinTransformer == null) {
			try { // reflective instantiation for older mixin versions
				@SuppressWarnings("unchecked")
				Constructor<IMixinTransformer> ctor = (Constructor<IMixinTransformer>) Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getConstructor();
				ctor.setAccessible(true);
				mixinTransformer = ctor.newInstance();
			} catch (ReflectiveOperationException e) {
				Log.debug(LogCategory.KNOT, "Can't create Mixin transformer through reflection (only applicable for 0.8-0.8.2): %s", e);

				// both lookups failed (not received through IMixinService.offer and not found through reflection)
				throw new IllegalStateException("mixin transformer unavailable?");
			}
		}

		transformInitialized = true;
	}

	private IMixinTransformer getMixinTransformer() {
		assert mixinTransformer != null;
		return mixinTransformer;
	}

	Class<?> loadClass(String name, ClassLoader parent, boolean resolve) throws ClassNotFoundException {
		Class<?> c = loadClassOnly(name, parent);
		if (resolve) {
			itf.resolveClassFwd(c);
		}
		return c;
	}

	private Class<?> loadClassOnly(String name, ClassLoader parent) throws ClassNotFoundException {
		Class<?> c = itf.findLoadedClassFwd(name);
		if (c != null) {
			return c;
		}

		try {
			c = tryLoadClass(name, false);
		} catch (IllegalQuiltInternalAccessError e) {
			// This happens when loading a class that needs a quilt-loader class to load:
			// for example if it extends or implements one,
			// or if the verifier needs to load one to check up-casts.
			IllegalQuiltInternalAccessError e2 = new IllegalQuiltInternalAccessError("Failed to load the class " + name + "!");
			e2.initCause(e);
			throw e2;
		}

		if (c != null) {
			return c;
		}

		c = parent.loadClass(name);

		if (c != null && false /* Temporarily removed, since it doesn't work with Log4J. */) {
			QuiltLoaderInternal internal = c.getAnnotation(QuiltLoaderInternal.class);
			QuiltLoaderInternalType type;
			Class<?>[] replacements = {};
			if (internal != null) {
				type = internal.value();
				replacements = internal.replacements();
			} else if (name.startsWith("org.quiltmc.loader.impl")) {
				type = QuiltLoaderInternalType.LEGACY_EXPOSED;
				Log.warn(LogCategory.GENERAL, c + " isn't annotated with @QuiltLoaderInternal!");
			} else if (name.startsWith("org.quiltmc.loader.api.plugin")) {
				type = QuiltLoaderInternalType.PLUGIN_API;
				Log.warn(LogCategory.GENERAL, c + " isn't annotated with @QuiltLoaderInternal!");
			} else {
				return c;
			}

			if (type != QuiltLoaderInternalType.LEGACY_NO_WARN) {
				String msg = generateInternalClassWarning(c, type, replacements);

				switch (type) {
					case LEGACY_EXPOSED: {
						// TODO: Disable this when we can generate a report with this information!
						Log.warn(LogCategory.GENERAL, msg, new Throwable());
						break;
					}
					case NEW_INTERNAL:
					case PLUGIN_API:
					default: {
						throw new IllegalQuiltInternalAccessError(msg);
					}
				}
			}
		}

		return c;
	}

	private static String generateInternalClassWarning(Class<?> target, QuiltLoaderInternalType type, Class<?>[] replacements) {
		StringBuilder sb = new StringBuilder();
		switch (type) {
			case LEGACY_EXPOSED: {
				sb.append("Found access to quilt-loader internal " + target + " - ");
				break;
			}
			case NEW_INTERNAL: {
				sb.append("! Quilt-loader internal " + target + "\n");
				break;
			}
			case PLUGIN_API: {
				sb.append("! Quilt-loader plugin-only internal api " + target + "\n");
				break;
			}
			default: {
				sb.append("! UNKNOWN TYPE " + type + "\n");
				break;
			}
		}

		if (replacements.length == 0) {
			sb.append("Please don't use this, instead ask us to declare a new public API that we can guarantee backwards compatibility for!");

			return sb.toString();
		} else if (replacements.length == 1) {
			sb.append("Please don't use this, instead try using the public api " + replacements[0] + " instead - that way we can guarantee backwards compatibility when using it!");
		} else {
			sb.append("Please don't use this, instead try one of the following public api classes since those have guaranteed backwards compatibility:");
			for (Class<?> repl : replacements) {
				sb.append("\n - " + repl);
			}
		}

		return sb.toString();
	}

	Class<?> tryLoadClass(String name, boolean allowFromParent) throws ClassNotFoundException {
		if (name.startsWith("java.")) {
			return null;
		}

		if (!allowedPrefixes.isEmpty()) {
			URL url = itf.getResource(LoaderUtil.getClassFileName(name));
			String[] prefixes;

			if (url != null
					&& (prefixes = allowedPrefixes.get(url.toString())) != null) {
				assert prefixes.length > 0;
				boolean found = false;

				for (String prefix : prefixes) {
					if (name.startsWith(prefix)) {
						found = true;
						break;
					}
				}

				if (!found) {
					throw new ClassNotFoundException("class "+name+" is currently restricted from being loaded");
				}
			}
		}

		if (!allowFromParent && !parentSourcedClasses.isEmpty()) {
			int pos = name.length();

			while ((pos = name.lastIndexOf('$', pos - 1)) > 0) {
				if (parentSourcedClasses.contains(name.substring(0, pos))) {
					allowFromParent = true;
					break;
				}
			}
		}

		byte[] input = getPostMixinClassByteArray(name, allowFromParent);
		if (input == null) return null;

		if (allowFromParent) {
			parentSourcedClasses.add(name);
		}

		KnotClassDelegate.Metadata metadata = getMetadata(name, itf.getResource(LoaderUtil.getClassFileName(name)));

		int pkgDelimiterPos = name.lastIndexOf('.');

		if (pkgDelimiterPos > 0) {
			// TODO: package definition stub
			String pkgString = name.substring(0, pkgDelimiterPos);

			final boolean allowFromParentFinal = allowFromParent;
			Boolean permitted = packageSideCache.computeIfAbsent(pkgString, pkgName -> {
				return computeCanLoadPackage(pkgName, allowFromParentFinal);
			});

			if (permitted != null && !permitted) {
				throw new RuntimeException("Cannot load package " + pkgString + " in environment type " + envType);
			}

			Package pkg = itf.getPackage(pkgString);

			if (pkg == null) {
				try {
					pkg = itf.definePackage(pkgString, null, null, null, null, null, null, null);
				} catch (IllegalArgumentException e) { // presumably concurrent package definition
					pkg = itf.getPackage(pkgString);
					if (pkg == null) throw e; // still not defined?
				}
			}
		}

		return itf.defineClassFwd(name, input, 0, input.length, metadata.codeSource);
	}

	boolean computeCanLoadPackage(String pkgName, boolean allowFromParent) {
		String fileName = pkgName + ".package-info";
		try {
			byte[] bytes = getRawClassByteArray(fileName, allowFromParent);
			if (bytes == null) {
				// No package-info class file
				return true;
			}
			PackageEnvironmentStrippingData data = new PackageEnvironmentStrippingData(QuiltLoaderImpl.ASM_VERSION, envType);
			new ClassReader(bytes).accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			return !data.stripEntirePackage;
		} catch (IOException e) {
			throw new RuntimeException("Unable to load " + fileName, e);
		}
	}

	Metadata getMetadata(String name, URL resourceURL) {
		if (resourceURL == null) return Metadata.EMPTY;

		URL codeSourceUrl = null;

		try {
			codeSourceUrl = UrlUtil.getSource(LoaderUtil.getClassFileName(name), resourceURL);
		} catch (UrlConversionException e) {
			System.err.println("Could not find code source for " + resourceURL + ": " + e.getMessage());
		}

		if (codeSourceUrl == null) return Metadata.EMPTY;

		return getMetadata(codeSourceUrl);
	}

	public void setMod(Path loadFrom, URL codeSourceUrl, ModContainer mod) {
		metadataCache.computeIfAbsent(codeSourceUrl.toString(), str -> {
			Manifest manifest = null;

			try {
				manifest = ManifestUtil.readManifest(loadFrom);
			} catch (IOException io) {
				if (QuiltLauncherBase.getLauncher().isDevelopment()) {
					Log.warn(LogCategory.KNOT, "Failed to load manifest", io);
				}
			}

			return new Metadata(manifest, new CodeSourceImpl(codeSourceUrl, null, mod));
		});
	}

	Metadata getMetadata(URL codeSourceUrl) {
		return metadataCache.computeIfAbsent(codeSourceUrl.toString(), (codeSourceStr) -> {
			Manifest manifest = null;
			CodeSourceImpl codeSource = null;
			Certificate[] certificates = null;

			try {
				Path path = UrlUtil.asPath(codeSourceUrl);

				if (Files.isDirectory(path)) {
					manifest = ManifestUtil.readManifest(path);
				} else {
					URLConnection connection = new URL("jar:" + codeSourceStr + "!/").openConnection();

					if (connection instanceof JarURLConnection) {
						manifest = ((JarURLConnection) connection).getManifest();
						certificates = ((JarURLConnection) connection).getCertificates();
					}

					if (manifest == null) {
						try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(path, false)) {
							manifest = ManifestUtil.readManifest(jarFs.get().getRootDirectories().iterator().next());
						}
					}

					// TODO
					/* JarEntry codeEntry = codeSourceJar.getJarEntry(filename);

					if (codeEntry != null) {
						codeSource = new CodeSource(codeSourceURL, codeEntry.getCodeSigners());
					} */
				}
			} catch (IOException | FileSystemNotFoundException e) {
				if (QuiltLauncherBase.getLauncher().isDevelopment()) {
					Log.warn(LogCategory.KNOT, "Failed to load manifest", e);
				}
			}

			if (codeSource == null) {
				codeSource = new CodeSourceImpl(codeSourceUrl, certificates, null);
			}

			return new Metadata(manifest, codeSource);
		});
	}

	public byte[] getPostMixinClassByteArray(String name, boolean allowFromParent) {
		byte[] transformedClassArray = getPreMixinClassByteArray(name, allowFromParent);

		if (!transformInitialized || !canTransformClass(name)) {
			return transformedClassArray;
		}

		try {
			return getMixinTransformer().transformClassBytes(name, name, transformedClassArray);
		} catch (Throwable t) {
			String msg = String.format("Mixin transformation of %s failed", name);
			Log.warn(LogCategory.KNOT, msg, t);

			throw new RuntimeException(msg, t);
		}
	}

	public void afterMixinIntiializeFinished() {
		transformFinishedLoading = true;
	}

	/**
	 * Runs all the class transformers except mixin.
	 */
	public byte[] getPreMixinClassByteArray(String name, boolean allowFromParent) {
		// some of the transformers rely on dot notation
		name = name.replace('/', '.');

		if (!transformFinishedLoading && LOG_EARLY_CLASS_LOADS) {
			Log.info(LogCategory.GENERAL, "Loading " + name + " early", new Throwable());
		}

		if (!transformInitialized || !canTransformClass(name)) {
			try {
				return getRawClassByteArray(name, allowFromParent);
			} catch (IOException e) {
				throw new RuntimeException("Failed to load class file for '" + name + "'!", e);
			}
		}

		byte[] input = provider.getEntrypointTransformer().transform(name);

		if (input == null) {
			try {
				input = getRawClassByteArray(name, allowFromParent);
			} catch (IOException e) {
				throw new RuntimeException("Failed to load class file for '" + name + "'!", e);
			}
		}

		if (input != null) {
			return QuiltTransformer.transform(isDevelopment, envType, name, input);
		}

		return null;
	}

	private static boolean canTransformClass(String name) {
		name = name.replace('/', '.');
		// Blocking Fabric Loader classes is no longer necessary here as they don't exist on the modding class loader
		return /* !"net.fabricmc.api.EnvType".equals(name) && !name.startsWith("net.fabricmc.loader.") && */ !name.startsWith("org.apache.logging.log4j");
	}

	public byte[] getRawClassByteArray(String name, boolean allowFromParent) throws IOException {
		InputStream inputStream = itf.getResourceAsStream(LoaderUtil.getClassFileName(name), allowFromParent);
		if (inputStream == null) return null;

		int a = inputStream.available();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(a < 32 ? 32768 : a);
		byte[] buffer = new byte[8192];
		int len;

		while ((len = inputStream.read(buffer)) > 0) {
			outputStream.write(buffer, 0, len);
		}

		inputStream.close();
		return outputStream.toByteArray();
	}

	void setAllowedPrefixes(URL url, String... prefixes) {
		if (prefixes.length == 0) {
			allowedPrefixes.remove(url.toString());
		} else {
			allowedPrefixes.put(url.toString(), prefixes);
		}
	}
}
