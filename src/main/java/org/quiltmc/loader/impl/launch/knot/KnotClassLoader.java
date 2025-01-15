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

package org.quiltmc.loader.impl.launch.knot;

import net.fabricmc.api.EnvType;

import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.filesystem.QuiltClassPath;
import org.quiltmc.loader.impl.filesystem.QuiltClassPath.PathResult;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.ZipHandling;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.game.GameProvider;
import org.quiltmc.loader.impl.util.DeferredInputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
class KnotClassLoader extends SecureClassLoader implements KnotClassLoaderInterface {
	private static class DynamicURLClassLoader extends URLClassLoader {
		private DynamicURLClassLoader(URL[] urls) {
			super(urls, new DummyClassLoader());
		}

		@Override
		public void addURL(URL url) {
			super.addURL(url);
		}

		static {
			registerAsParallelCapable();
		}
	}

	private final QuiltClassPath<PathCustomUrl> paths = new QuiltClassPath<>(PathCustomUrl.class);
	private final DynamicURLClassLoader fakeLoader;
	private final DynamicURLClassLoader minimalLoader;
	private final ClassLoader originalLoader;
	private final KnotClassDelegate delegate;

	KnotClassLoader(boolean isDevelopment, EnvType envType, GameProvider provider) {
		super(new DynamicURLClassLoader(new URL[0]));
		this.originalLoader = getClass().getClassLoader();
		// For compatibility we send all URLs to the fake loader
		// but never ask it for resources
		this.fakeLoader = (DynamicURLClassLoader) getParent();
		this.minimalLoader = new DynamicURLClassLoader(new URL[0]);
		this.delegate = new KnotClassDelegate(isDevelopment, envType, this, provider);
	}

	@Override
	public KnotClassDelegate getDelegate() {
		return delegate;
	}

	@Override
	public ClassLoader getOriginalLoader() {
		return originalLoader;
	}

	@Override
	public boolean isClassLoaded(String name) {
		synchronized (getClassLoadingLock(name)) {
			return findLoadedClass(name) != null;
		}
	}

	@Override
	public URL getResource(String name) {
		return getResource(name, true);
	}

	@Override
	public URL getResource(String name, boolean allowFromParent) {
		Objects.requireNonNull(name);

		URL url = findResource(name);

		if (url == null && allowFromParent) {
			url = originalLoader.getResource(name);
		}

		return url;
	}

	@Override
	public URL findResource(String name) {
		Objects.requireNonNull(name);

		PathResult<PathCustomUrl> path = paths.findResourceData(name);
		if (path != null) {
			return toUrl(path);
		}

		return minimalLoader.getResource(name);
	}

	private static URL toUrl(PathResult<PathCustomUrl> path) {
		try {
			if (path.data != null) {
				return path.data.constructUrl(path.path, path.root);
			}
			return UrlUtil.asUrl(path.path);
		} catch (MalformedURLException e) {
			throw new Error("Failed to turn the path " + path.path.getClass() + " " + path + " into a url!", e);
		}
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		Objects.requireNonNull(name);

		try {
			return DeferredInputStream.deferIfNeeded(() -> {
				return getResourceAsStream0(name);
			});
		} catch (IOException e) {
			throw new RuntimeException("Failed to fetch the input stream", e);
		}
	}

	private InputStream getResourceAsStream0(String name) {
		Path path = paths.findResource(name);
		if (path != null) {
			try {
				return Files.newInputStream(path);
			} catch (IOException e) {
				// Okay so that's *really* not good
				e.printStackTrace();
			}
		}

		InputStream inputStream = minimalLoader.getResourceAsStream(name);

		if (inputStream == null) {
			inputStream = originalLoader.getResourceAsStream(name);
		}

		return inputStream;
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		Objects.requireNonNull(name);

		List<PathResult<PathCustomUrl>> fromPaths = paths.getAllResourceData(name);
		Enumeration<URL> first = minimalLoader.getResources(name);
		Enumeration<URL> second = originalLoader.getResources(name);
		return new Enumeration<URL>() {
			Iterator<PathResult<PathCustomUrl>> iterator = fromPaths.iterator();
			Enumeration<URL> current = first;

			@Override
			public boolean hasMoreElements() {
				if (iterator.hasNext()) {
					return true;
				}

				if (current == null) {
					return false;
				}

				if (current.hasMoreElements()) {
					return true;
				}

				if (current == first && second.hasMoreElements()) {
					return true;
				}

				return false;
			}

			@Override
			public URL nextElement() {
				if (iterator.hasNext()) {
					return toUrl(iterator.next());
				}
				if (current == null) {
					return null;
				}

				if (!current.hasMoreElements()) {
					if (current == first) {
						current = second;
					} else {
						current = null;
						return null;
					}
				}

				return current.nextElement();
			}
		};
	}

	@Override
	public void resolveClassFwd(Class<?> c) {
		resolveClass(c);
	}

	@Override
	public Class<?> findLoadedClassFwd(String name) {
		return findLoadedClass(name);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			return delegate.loadClass(name, originalLoader, resolve);
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return delegate.tryLoadClass(name, false);
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);

			if (c == null) {
				c = delegate.tryLoadClass(name, true);

				if (c == null) {
					throw new ClassNotFoundException("can't find class "+name);
				}

				((KnotBaseClassLoader) c.getClassLoader()).resolveClassFwd(c);
			} else {
				resolveClass(c);
			}

			return c;
		}
	}

	@Override
	public void addURL(URL url) {
		fakeLoader.addURL(url);
		minimalLoader.addURL(url);
	}

	@Override
	public void addPath(Path root, ModContainer mod, URL origin) {
		URL asUrl;
		try {
			asUrl = UrlUtil.asUrl(root);
		} catch (MalformedURLException e) {
			throw new Error(e);
		}
		delegate.setMod(root, asUrl, mod);
		fakeLoader.addURL(asUrl);
		if (root.getFileName() != null && root.getFileName().toString().endsWith(".jar")) {
			Path zipRoot = null;
			try {
				String fsName = "classpath-" + root.getFileName().toString();
				zipRoot = new QuiltZipFileSystem(fsName, root, "", ZipHandling.JAR).getRoot();
			} catch (IOException io) {
				Log.warn(LogCategory.GENERAL, "Failed to open the file " + root + ", adding it via the slow method instead!", io);
			}
			if (zipRoot != null) {
				String urlBase = "jar:" + asUrl + "!/";
				paths.addRoot(zipRoot, new PathCustomUrl(urlBase));
			} else {
				minimalLoader.addURL(asUrl);
			}
		} else {
			paths.addRoot(root);
		}
	}

	@Override
	public InputStream getResourceAsStream(String classFile, boolean allowFromParent) throws IOException {
		Path path = paths.findResource(classFile);
		if (path != null) {
			return Files.newInputStream(path);
		}
		InputStream inputStream = minimalLoader.getResourceAsStream(classFile);

		if (inputStream == null && allowFromParent) {
			inputStream = originalLoader.getResourceAsStream(classFile);
		}

		return inputStream;
	}

	@Override
	public Package getPackage(String name) {
		return super.getPackage(name);
	}

	@Override
	public Package definePackage(String name, String specTitle, String specVersion, String specVendor,
			String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
		return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
	}

	@Override
	public Class<?> defineClassFwd(String name, byte[] b, int off, int len, CodeSource cs) {
		return super.defineClass(name, b, off, len, cs);
	}

	@Override
	public String toString() {
		return "KnotClassLoader";
	}

	static {
		registerAsParallelCapable();
	}

	private static final class PathCustomUrl {
		final String urlBase;

		PathCustomUrl(String urlBase) {
			this.urlBase = urlBase;
		}

		URL constructUrl(Path path, Path root) throws MalformedURLException {
			Path realPath = null;
			if (ExtendedFiles.isMountedFile(path)) {
				// Used for multi-release jars
				// We 'mount' multi-versioned classes, so they will really be somewhere else
				try {
					realPath = ExtendedFiles.readMountTarget(path);
					if (realPath.getFileSystem() == root.getFileSystem()) {
						path = realPath;
					}
				} catch (IOException e) {
					// In theory this should never happen?
					// At least for quilt-loader's own filesystems
					// Which this must be (as we constructed a QuiltZipFileSystem)
					throw new Error("Failed to read mount target? ", e);
				}
			}

			return new URL(urlBase + root.relativize(path));
		}
	}
}
