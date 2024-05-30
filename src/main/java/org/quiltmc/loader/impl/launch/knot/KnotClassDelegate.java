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

import org.quiltmc.loader.impl.transformer.PackageStrippingData;
import org.quiltmc.loader.impl.util.LoaderUtil;
import org.objectweb.asm.ClassReader;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.launch.common.QuiltCodeSource;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.patch.PatchLoader;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.ManifestUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class KnotClassDelegate {
	public static class Metadata {
		static final Metadata EMPTY = new Metadata(null, null);

		public final Manifest manifest;
		final CodeSourceImpl codeSource;

		Metadata(Manifest manifest, CodeSourceImpl codeSource) {
			this.manifest = manifest;
			this.codeSource = codeSource;
		}

		boolean isPackageSealed(String pkgName) {
			String value = getPackageValue(pkgName, Attributes.Name.SEALED);
			return value != null && "true".equalsIgnoreCase(value);
		}

		String getPackageValue(String pkgName, Attributes.Name name) {
			if (manifest == null) {
				return null;
			}

			String value = null;
			Attributes attributes = manifest.getAttributes(pkgName);

			if (attributes != null) {
				value = attributes.getValue(name);
			}

			if (value == null) {
				value = manifest.getMainAttributes().getValue(name);
			}

			return value;
		}
	}

	static class CodeSourceImpl extends CodeSource implements QuiltCodeSource {
		final String modId;

		public CodeSourceImpl(URL url, Certificate[] certs, String modId) {
			super(url, certs);
			this.modId = modId;
		}

		@Override
		public Optional<ModContainer> getQuiltMod() {
			return QuiltLoader.getModContainer(modId);
		}
	}

	private static final boolean LOG_EARLY_CLASS_LOADS = Boolean.getBoolean(SystemProperties.LOG_EARLY_CLASS_LOADS);

	private final Map<String, Metadata> metadataCache = new ConcurrentHashMap<>();
	private final Map<String, String> modCodeSourceMap = new ConcurrentHashMap<>();
	private final KnotClassLoaderInterface itf;
	private final GameProvider provider;
	private final boolean isDevelopment;
	private final EnvType envType;
	private IMixinTransformer mixinTransformer;
	private boolean transformInitialized = false;
	private boolean transformFinishedLoading = false;
	private Map<String, String> hiddenClasses = Collections.emptyMap();
	private String transformCacheUrl;
	private final Map<String, String[]> allowedPrefixes = new ConcurrentHashMap<>();
	private final Set<String> parentSourcedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/** Set of {@link URL}s which should not be loaded from the parent, because they have been replaced by URLs/paths
	 * in this loader. */
	private final Set<String> parentHiddenUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/** Map of package to the reason why it cannot be loaded. If the package can be loaded then the value is the empty string. */
	private final Map<String, String> packageLoadDenyCache = new ConcurrentHashMap<>();

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

		return parent.loadClass(name);
	}

	Class<?> tryLoadClass(String name, boolean allowFromParent) throws ClassNotFoundException {
		if (name.startsWith("java.")) {
			return null;
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

		URL url = getClassUrl(name, allowFromParent);

		if (!allowFromParent && shouldRerouteToParent(name)) {
			// Force slf4j itself to be loaded on a single classloader
			// FIXME DISABLED
			// TODO: Change this into a report, rather than being printed on each overlap.
			// Check to see if the class actually exists in the parent
			// and it hasn't been "hidden"
			String classFileName = LoaderUtil.getClassFileName(name);
			URL originalURL = itf.getOriginalLoader().getResource(classFileName);
			if (originalURL != null) {
				try {
					URL codeSource = UrlUtil.getSource(classFileName, originalURL);
					if (codeSource != null && !parentHiddenUrls.contains(codeSource.toString())) {
						// Exists in parent, not hidden
						if (url != null) {
							Log.warn(LogCategory.GENERAL, "Rerouting classloading to the parent classloader instead of " + url);
						}
						return null;
					}
				} catch (UrlConversionException e) {
					Log.warn(LogCategory.GENERAL, "Failed to get the code source URL for " + originalURL);
				}
			}
		}

		if (!allowedPrefixes.isEmpty() && url != null) {
			String fileName = LoaderUtil.getClassFileName(name);
			URL codeSource = null;

			try {
				codeSource = UrlUtil.getSource(fileName, url);
			} catch (UrlConversionException e) {
				Log.warn(LogCategory.GENERAL, "Failed to get the code source URL for " + url);
			}

			String[] prefixes;

			if (codeSource != null && (prefixes = allowedPrefixes.get(codeSource.toString())) != null) {
				assert prefixes.length > 0;
				boolean found = false;

				for (String prefix : prefixes) {
					if (name.startsWith(prefix)) {
						found = true;
						break;
					}
				}

				if (!found) {
					throw new ClassNotFoundException("class " + name + " is currently restricted from being loaded");
				}
			}
		}

		int pkgDelimiterPos = name.lastIndexOf('.');
		String pkgString = pkgDelimiterPos > 0 ? name.substring(0, pkgDelimiterPos) : null;

		if (pkgString != null) {
			final boolean allowFromParentFinal = allowFromParent;
			String denyReason = packageLoadDenyCache.computeIfAbsent(pkgString, pkgName -> {
				return computePackageDenyLoadReason(pkgName, allowFromParentFinal);
			});

			if (denyReason != null && !denyReason.isEmpty()) {
				throw new RuntimeException("Cannot load package " + pkgString + " " + denyReason);
			}
		}

		String hideReason = hiddenClasses.get(name);
		if (hideReason != null) {
			throw new RuntimeException("Cannot load " + name + " " + hideReason);
		}

		byte[] input = getPostMixinClassByteArray(url, name);
		if (input == null) return null;

		if (allowFromParent) {
			parentSourcedClasses.add(name);
		}

		KnotClassDelegate.Metadata metadata = getMetadata(name, url);

		final String modId;

		if (metadata.codeSource == null) {
			modId = null;
		} else {
			modId = metadata.codeSource.modId;
		}

		Class<?> c = itf.findLoadedClassFwd(name);

		if (c != null) {
			// Workaround for an issue where the act of loading a class causes it to be loaded by the parent classloader,
			// or where it causes a re-entrant classloading of itself
			Log.warn(LogCategory.GENERAL, "Tried to define " + c + " but it was already loaded!");
			Log.warn(LogCategory.GENERAL, "  - Already loaded source: " + UrlUtil.getCodeSource(c));
			Log.warn(LogCategory.GENERAL, "  - Rejected (new) source: " + url);
			return c;
		}

		if (pkgString != null) {
			Package pkg = itf.getPackage(pkgString);

			if (pkg == null) {
				String specTitle = metadata.getPackageValue(pkgString, Attributes.Name.SPECIFICATION_TITLE);
				String specVersion = metadata.getPackageValue(pkgString, Attributes.Name.SPECIFICATION_VERSION);
				String specVendor = metadata.getPackageValue(pkgString, Attributes.Name.SPECIFICATION_VENDOR);
				String implTitle = metadata.getPackageValue(pkgString, Attributes.Name.IMPLEMENTATION_TITLE);
				String implVersion = metadata.getPackageValue(pkgString, Attributes.Name.IMPLEMENTATION_VERSION);
				String implVendor = metadata.getPackageValue(pkgString, Attributes.Name.IMPLEMENTATION_VENDOR);
				URL sealBase = null; // TODO: Implement sealing!

				try {
					pkg = itf.definePackage(pkgString, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
				} catch (IllegalArgumentException e) { // presumably concurrent package definition
					pkg = itf.getPackage(pkgString);
					if (pkg == null) throw e; // still not defined?
				}
			}
		}

		c = itf.defineClassFwd(name, input, 0, input.length, metadata.codeSource);

		if (Boolean.getBoolean(SystemProperties.DEBUG_CLASS_TO_MOD)) {
			StringBuilder text = new StringBuilder(name);
			while (text.length() < 100) {
				text.append(" ");
			}
			text.append(modId != null ? modId : "?");
			while (text.length() < 140) {
				text.append(" ");
			}
			text.append("\n");
			System.out.print(text.toString());
		}
		return c;
	}

	private boolean shouldRerouteToParent(String name) {
		return name.startsWith("org.slf4j.") || name.startsWith("org.apache.logging.log4j.");
	}

	private String computePackageDenyLoadReason(String pkgName, boolean allowFromParent) {
		String fileName = pkgName + ".package-info";
		String hideReason = hiddenClasses.get(fileName);
		return hideReason != null ? hideReason : "";
	}

	public Metadata getMetadata(String name, URL resourceURL) {
		if (resourceURL == null) return Metadata.EMPTY;

		String classFileName = LoaderUtil.getClassFileName(name);

		if (transformCacheUrl != null) {
			String resourceUrlString = resourceURL.toString();
			if (resourceUrlString.startsWith(transformCacheUrl) && resourceUrlString.endsWith(classFileName)) {
				String middle = resourceUrlString.substring(transformCacheUrl.length(), resourceUrlString.length() - classFileName.length());
				if (middle.startsWith("/")) {
					middle = middle.substring(1);
				}
				if (middle.endsWith("/")) {
					middle = middle.substring(0, middle.length() - 1);
				}
				String modUrl = modCodeSourceMap.get(middle);
				if (modUrl != null) {
					Metadata metadata = metadataCache.get(modUrl);
					if (metadata != null) {
						return metadata;
					}
				}
			}
		}

		URL codeSourceUrl = null;

		try {
			codeSourceUrl = UrlUtil.getSource(classFileName, resourceURL);
		} catch (UrlConversionException e) {
			Log.warn(LogCategory.KNOT, "Could not find code source for " + resourceURL + ": " + e.getMessage());
		}

		if (codeSourceUrl == null) return Metadata.EMPTY;

		return getMetadata(codeSourceUrl);
	}

	public void setMod(Path loadFrom, URL codeSourceUrl, ModContainer mod) {
		String urlStr = codeSourceUrl.toString();
		if (mod != null) {
			modCodeSourceMap.put(mod.metadata().id(), urlStr);
		}
		metadataCache.computeIfAbsent(urlStr, str -> {
			Manifest manifest = null;

			try {
				manifest = ManifestUtil.readManifest(loadFrom);
			} catch (IOException io) {
				if (QuiltLauncherBase.getLauncher().isDevelopment()) {
					Log.warn(LogCategory.KNOT, "Failed to load manifest", io);
				}
			}

			String modId = mod == null ? null : mod.metadata().id();
			return new Metadata(manifest, new CodeSourceImpl(codeSourceUrl, null, modId));
		});
	}

	public Metadata getMetadata(URL codeSourceUrl) {
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

	private URL getClassUrl(String name, boolean allowFromParent) {
		return itf.getResource(LoaderUtil.getClassFileName(name), allowFromParent);
	}

	public byte[] getPostMixinClassByteArray(String name, boolean allowFromParent) {
		return getPostMixinClassByteArray(getClassUrl(name, allowFromParent), name);
	}

	public byte[] getPostMixinClassByteArray(URL url, String name) {
		byte[] transformedClassArray = getPreMixinClassByteArray(url, name);

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
		return getPreMixinClassByteArray(getClassUrl(name, allowFromParent), name);
	}

	/**
	 * Runs all the class transformers except mixin.
	 */
	public byte[] getPreMixinClassByteArray(URL classFileURL, String name) {
		// some of the transformers rely on dot notation
		name = name.replace('/', '.');

		if (!transformFinishedLoading && LOG_EARLY_CLASS_LOADS) {
			Log.info(LogCategory.GENERAL, "Loading " + name + " early", new Throwable());
		}

		if (name.startsWith("org.quiltmc.loader.impl.patch.")) {
			return PatchLoader.getNewPatchedClass(name);
		}

		try {
			return getRawClassByteArray(classFileURL, name);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load class file for '" + name + "'!", e);
		}
	}

	private static boolean canTransformClass(String name) {
		name = name.replace('/', '.');
		// Blocking Fabric Loader classes is no longer necessary here as they don't exist on the modding class loader
		return /* !"net.fabricmc.api.EnvType".equals(name) && !name.startsWith("net.fabricmc.loader.") && */ !name.startsWith("org.apache.logging.log4j");
	}

	public byte[] getRawClassByteArray(String name, boolean allowFromParent) throws IOException {
		return getRawClassByteArray(getClassUrl(name, allowFromParent), name);
	}

	public byte[] getRawClassByteArray(URL url, String name) throws IOException {
		try (InputStream inputStream = (url != null ? url.openStream() : null)) {
			if (inputStream == null) {
				return null;
			}
			return FileUtil.readAllBytes(inputStream);
		}
	}

	public void setAllowedPrefixes(URL url, String... prefixes) {
		if (prefixes.length == 0) {
			allowedPrefixes.remove(url.toString());
		} else {
			allowedPrefixes.put(url.toString(), prefixes);
		}
	}

	public void setTransformCache(URL insideTransformCache) {
		transformCacheUrl = insideTransformCache.toString();
	}

	public void setHiddenClasses(Set<String> hiddenClasses) {
		Map<String, String> map = new HashMap<>();
		for (String cl : hiddenClasses) {
			map.put(cl, "unknown reason");
		}
		setHiddenClasses(map);
	}

	public void setHiddenClasses(Map<String, String> hiddenClasses) {
		this.hiddenClasses = hiddenClasses;
	}

	public void hideParentUrl(URL parentPath) {
		parentHiddenUrls.add(parentPath.toString());
	}
}
