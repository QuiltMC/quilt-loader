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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.MountOption;
import org.quiltmc.loader.api.QuiltFileSystems;
import org.quiltmc.loader.api.QuiltFileSystems.ExtendedFileSystemRef;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.filesystem.QuiltClassPath;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.ZipHandling;
import org.quiltmc.loader.impl.transformer.InternalsHiderTransform;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.UrlUtil;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class QuiltPluginClassLoader extends ClassLoader {

	final QuiltPluginContextImpl context;
	final Path from;
	final Set<String> loadablePackages;
	final QuiltClassPath<Void> additionalClassPath;

	public QuiltPluginClassLoader(QuiltPluginContextImpl context, ClassLoader parent, Path from,
		ModMetadataExt.ModPlugin plugin) {

		super(parent);
		this.context = context;
		this.from = from;
		this.loadablePackages = new HashSet<>(plugin.packages());
		this.additionalClassPath = new QuiltClassPath<>();
	}

	void addToPluginClassPath(String pluginId, Path path) throws IOException {
		if (FasterFiles.isDirectory(path)) {
			additionalClassPath.addRoot(path);
		} else if (FasterFiles.isRegularFile(path)) {
			additionalClassPath.addRoot(
				new QuiltZipFileSystem("plugin-classpath-" + pluginId, path, "", ZipHandling.JAR).getRoot()
			);
		}
	}

	void addSubfolder(String pluginId, Path root, Path[] subfolders) throws IOException {

		// Minor optimisation
		if (subfolders.length == 0) {
			addToPluginClassPath(pluginId, root);
			return;
		}

		ExtendedFileSystemRef ext = QuiltFileSystems.createExtendedFileSystem("plugin-classpath-" + pluginId);

		for (Path sub : subfolders) {
			if (sub.getFileSystem() != root.getFileSystem()) {
				throw new IllegalArgumentException(
					"The subfolder '" + sub + "' has a different filesystem (" + sub.getFileSystem()
						+ ") then the root! '" + root + "' (" + root.getFileSystem() + ")"
				);
			}
			if (!sub.startsWith(root)) {
				throw new IllegalArgumentException("The given subfolder '" + sub + "' is not inside the root! " + root);
			}

			Files.walkFileTree(sub, new SimpleFileVisitor<Path>() {

				Path getDestination(Path original) {
					Path relative = root.relativize(original);
					Path dst = ext.root;
					for (Path element : relative) {
						dst = dst.resolve(element.toString());
					}
					return dst;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					ext.quiltFileSystem.createDirectories(getDestination(dir));
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					ext.quiltFileSystem.mount(file, getDestination(file), MountOption.READ_ONLY);
					return FileVisitResult.CONTINUE;
				}
			});
		}

		ext.markAsReadOnly();
		additionalClassPath.addRoot(ext.root);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (name.startsWith("java.") || name.startsWith("javax.")) {
			return super.loadClass(name, resolve);
		}
		Class<?> c = findLoadedClass(name);
		if (c != null) {
			return c;
		}
		c = loadClassInner(name);
		if (c == null) {
			return super.loadClass(name, resolve);
		}
		if (resolve) {
			resolveClass(c);
		}
		return c;
	}

	Class<?> loadClassDirectly(String name, boolean resolve) throws ClassNotFoundException {
		Class<?> c = loadClassInner(name);
		if (c == null) {
			throw new ClassNotFoundException(name);
		}
		if (resolve) {
			resolveClass(c);
		}
		return c;
	}

	private Class<?> loadClassInner(String name) throws ClassNotFoundException {

		String pkg = null;

		load_from_plugin: {

			int lastDot = name.lastIndexOf('.');
			if (lastDot < 0) {
				break load_from_plugin;
			}

			pkg = name.substring(0, lastDot);

			byte[] src;

			try {
				if (loadablePackages.contains(pkg)) {
					String path = name.replace(".", from.getFileSystem().getSeparator()).concat(".class");
					src = Files.readAllBytes(from.resolve(path));
				} else {
					Path path = additionalClassPath.findResource(name.replace(".", "/").concat(".class"));
					if (path == null) {
						break load_from_plugin;
					}
					src = Files.readAllBytes(path);
				}
			} catch (IOException io) {
				throw new ClassNotFoundException("Unable to load the file", io);
			}

			InternalsHiderTransform transform = new InternalsHiderTransform(InternalsHiderTransform.Target.PLUGIN);

			src = transform.run(context.optionFrom, src);

			try {
				definePackage(pkg, null, null, null, null, null, null, null);
			} catch (IllegalArgumentException e) {
				// Ignored
				// we do it this way since (apparently) this can be called from multiple threads at once
			}

			return defineClass(name, src, 0, src.length);
		}

		return context.manager.findClass(name, pkg);
	}

	private Path findPath(String name) {
		Path inPlugin = from.getRoot().resolve(from);
		if (FasterFiles.exists(inPlugin)) {
			return inPlugin;
		}
		return additionalClassPath.findResource(name);
	}

	@Override
	protected URL findResource(String name) {
		Path path = findPath(name);
		if (path != null) {
			return asUrl(path);
		}
		return super.findResource(name);
	}

	private static URL asUrl(Path path) {
		try {
			return UrlUtil.asUrl(path);
		} catch (MalformedURLException e) {
			throw new Error("Failed to convert path '" + path + "' to a URL!", e);
		}
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		List<Path> list = new ArrayList<>();
		Path inPlugin = from.getRoot().resolve(name);
		if (FasterFiles.exists(inPlugin)) {
			list.add(inPlugin);
		}
		list.addAll(additionalClassPath.getResources(name));

		final Enumeration<URL> urls = super.getResources(name);
		return new Enumeration<URL>() {
			final Iterator<Path> paths = list.iterator();

			@Override
			public boolean hasMoreElements() {
				return paths.hasNext() || urls.hasMoreElements();
			}

			@Override
			public URL nextElement() {
				if (paths.hasNext()) {
					return asUrl(paths.next());
				} else {
					return urls.nextElement();
				}
			}
		};
	}
}
