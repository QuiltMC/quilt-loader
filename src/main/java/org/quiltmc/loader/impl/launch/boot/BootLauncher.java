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

package org.quiltmc.loader.impl.launch.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.function.Consumer;

import org.quiltmc.loader.impl.filesystem.QuiltJoinedFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedFileSystemProvider;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystemProvider;
import org.quiltmc.loader.impl.launch.boot.QuiltInstallerJson.QuiltInstallerLibrary;
import org.quiltmc.loader.impl.launch.knot.Knot;
import org.quiltmc.loader.impl.launch.knot.KnotClient;
import org.quiltmc.loader.impl.launch.knot.KnotServer;
import org.quiltmc.loader.impl.launch.server.QuiltServerLauncher;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class BootLauncher {
	/*
	 * IMPORTANT:
	 * 
	 * This class is loaded BEFORE all of loaders libraries are on the class path.
	 * This means we can only use:
	 * - Classes from the standard java libraries
	 * - Classes in libraries which loader shades
	 * - Classes in loader itself which only use the above two classes.
	 */

	public Path run(Object context, String[] args) {

		// TODO: Implement update mechanism!
		// As in, this loader version should read a file with the *new* loader version,
		// verify that it exists, and relaunch with it (return that path)

		final String env;

		// void #(char, FileSystemProvider, URLStreamHandler)
		final FileSystemInit putFileSystemProvider;

		// void #(URL)
		final Consumer<URL> addToClassPath;

		try {
			Class<?> ctxClass = context.getClass();
			env = (String) ctxClass.getMethod("environment").invoke(context);
			Lookup lookup = MethodHandles.lookup();
			MethodHandle putFSP = lookup(
				context, "putFileSystemProvider", void.class, char.class, FileSystemProvider.class,
				URLStreamHandler.class
			);

			putFileSystemProvider = (letter, fsp, handler) -> {
				try {
					putFSP.invokeWithArguments(letter, fsp, handler);
				} catch (Throwable e) {
					throw rethrow(e);
				}
			};

			MethodHandle addToClassPathMethod = lookup(context, "addToClassPath", void.class, URL.class);

			addToClassPath = url -> {
				try {
					addToClassPathMethod.invokeWithArguments(url);
				} catch (Throwable e) {
					throw rethrow(e);
				}
			};

		} catch (ReflectiveOperationException e) {
			throw new Error("Failed to read some methods from the bootstrap!", e);
		}

		addLoaderLibraries(addToClassPath);
		putFileSystems(putFileSystemProvider);

		switch (env) {
			case "NONE": {
				Knot.main(args);
				break;
			}
			case "CLIENT": {
				KnotClient.main(args);
				break;
			}
			case "SERVER": {
				KnotServer.main(args);
				break;
			}
			case "SERVER_LAUNCHER": {
				QuiltServerLauncher.main(args);
				break;
			}
			default: {
				throw new IllegalArgumentException("Unknown environment '" + env + "'");
			}
		}

		// Relaunch not currently used.
		return null;
	}

	private static RuntimeException rethrow(Throwable e) throws Error {
		if (e instanceof Error) {
			throw (Error) e;
		}
		if (e instanceof RuntimeException) {
			throw (RuntimeException) e;
		}
		throw new RuntimeException(e);
	}

	private static MethodHandle lookup(Object target, String methodName, Class<?> returnType, Class<?>... argTypes) {
		try {
			MethodType type = MethodType.methodType(returnType, argTypes);
			return MethodHandles.publicLookup().findVirtual(target.getClass(), methodName, type).bindTo(target);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new IllegalStateException("Unable to find " + methodName + "!", e);
		}
	}

	private void addLoaderLibraries(Consumer<URL> to) {
		QuiltInstallerJson installerInfo;

		try (InputStream stream = getClass().getResourceAsStream("/quilt_installer.json")) {
			if (stream == null) {
				throw new Error("Unable to find 'quilt_installer.json'");
			}
			installerInfo = new QuiltInstallerJson(stream);
		} catch (IOException e) {
			throw new Error("Unable to read 'quilt_installer.json'!", e);
		}

		String mavenRoot = System.getProperty(SystemProperties.BOOT_LIBRARY_ROOT);
		if (mavenRoot == null) {
			throw new Error("Missing maven root system property '" + SystemProperties.BOOT_LIBRARY_ROOT + "'");
		}
		Path root = Paths.get(mavenRoot);
		if (!Files.isDirectory(root)) {
			throw new Error("Cannot bootstrap - missing maven root folder " + root);
		}

		for (QuiltInstallerLibrary lib : installerInfo.libraries) {
			String name = lib.name;
			String[] sections = name.split(":");
			if (sections.length != 3) {
				throw new Error("Bad maven name: " + name + ", resulting in " + Arrays.toString(sections));
			}
			String group = sections[0].replace(".", root.getFileSystem().getSeparator());
			String artifact = sections[1];
			String version = sections[2];

			Path jar = root.resolve(group).resolve(artifact).resolve(version)//
				.resolve(artifact + "-" + version + ".jar");

			if (!Files.exists(jar)) {
				throw new Error("Missing required library: " + jar);
			}

			try {
				to.accept(jar.toUri().toURL());
			} catch (MalformedURLException e) {
				throw new Error("Failed to convert " + jar + " to a URL!", e);
			}
		}
	}

	@FunctionalInterface
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	interface FileSystemInit {
		void putFileSystemProvider(char letter, FileSystemProvider fsp, URLStreamHandler handler);
	}

	private static void putFileSystems(FileSystemInit to) {
		// Joined FS
		{
			QuiltJoinedFileSystemProvider jfs = QuiltJoinedFileSystemProvider.findInstance();
			if (jfs == null) {
				jfs = new QuiltJoinedFileSystemProvider();
			}
			// We don't care about creating lots of stream handlers.
			to.putFileSystemProvider('j', jfs, new org.quiltmc.loader.impl.filesystem.quilt.jfs.Handler());
		}

		// Memory FS
		{
			QuiltMemoryFileSystemProvider mfs = QuiltMemoryFileSystemProvider.findInstance();
			if (mfs == null) {
				mfs = new QuiltMemoryFileSystemProvider();
			}
			// We don't care about creating lots of stream handlers.
			to.putFileSystemProvider('m', mfs, new org.quiltmc.loader.impl.filesystem.quilt.mfs.Handler());
		}

		// Unified FS
		{
			QuiltUnifiedFileSystemProvider ufs = QuiltUnifiedFileSystemProvider.findInstance();
			if (ufs == null) {
				ufs = new QuiltUnifiedFileSystemProvider();
			}
			// We don't care about creating lots of stream handlers.
			to.putFileSystemProvider('u', ufs, new org.quiltmc.loader.impl.filesystem.quilt.ufs.Handler());
		}

		// Zip FS
		{
			QuiltZipFileSystemProvider zfs = QuiltZipFileSystemProvider.findInstance();
			if (zfs == null) {
				zfs = new QuiltZipFileSystemProvider();
			}
			// We don't care about creating lots of stream handlers.
			to.putFileSystemProvider('z', zfs, new org.quiltmc.loader.impl.filesystem.quilt.zfs.Handler());
		}
	}
}
