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

package org.quiltmc.loader.impl.transformer;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.discovery.RuntimeModRemapper;
import org.quiltmc.loader.impl.filesystem.PartiallyWrittenIOException;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltMemoryPath;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.util.FilePreloadHelper;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.HashUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class TransformCache {

	static final boolean SHOW_KEY_DIFFERENCE = Boolean.getBoolean(SystemProperties.LOG_CACHE_KEY_CHANGES);

	/** Sub-folder for classes which are not associated with any mod in particular, but still need to be classloaded. */
	public static final String TRANSFORM_CACHE_NONMOD_CLASSLOADABLE = "Unknown Mod";

	private static final String CACHE_FILE = "files.zip";
	private static final String FILE_TRANSFORM_COMPLETE = "__TRANSFORM_COMPLETE";

	public static TransformCacheResult populateTransformBundle(Path transformCacheFolder, List<ModLoadOption> modList,
		ModSolveResult result) throws ModResolutionException {
		Map<String, String> map = new TreeMap<>();
		// Mod order is important? For now, assume it is
		int index = 0;
		for (ModLoadOption mod : modList) {
			map.put("mod#" + index++, mod.id());
		}

		for (Entry<String, ModLoadOption> provided : result.providedMods().entrySet()) {
			map.put("provided-mod:" + provided.getKey(), provided.getValue().metadata().id());
		}

		for (Entry<String, ModLoadOption> mod : result.directMods().entrySet()) {
			ModLoadOption modOption = mod.getValue();
			try {
				String name = modOption.from().getFileName().toString();
				byte[] hash = modOption.computeOriginHash();
				map.put("mod:" + mod.getKey(), name + " " + HashUtil.hashToString(hash));
			} catch (IOException io) {
				throw new ModResolutionException("Failed to compute the hash of " + modOption, io);
			}
		}

		boolean enableChasm = Boolean.getBoolean(SystemProperties.ENABLE_EXPERIMENTAL_CHASM);
		map.put("system-property:" + SystemProperties.ENABLE_EXPERIMENTAL_CHASM, "" + enableChasm);

		try {
			Files.createDirectories(transformCacheFolder.getParent());
		} catch (IOException e) {
			throw new ModResolutionException("Failed to create parent directories of the transform cache file!", e);
		}

		QuiltZipPath existing = checkTransformCache(transformCacheFolder, map);
		boolean isNewlyGenerated = false;
		if (existing == null) {
			existing = createTransformCache(transformCacheFolder.resolve(CACHE_FILE), toString(map), modList, result);
			isNewlyGenerated = true;
		} else if (!Boolean.getBoolean(SystemProperties.DISABLE_PRELOAD_TRANSFORM_CACHE)) {
			FilePreloadHelper.preLoad(transformCacheFolder.resolve(CACHE_FILE));
		}
		return new TransformCacheResult(transformCacheFolder, isNewlyGenerated, existing);
	}

	private static String toString(Map<String, String> map) {
		StringBuilder optionList = new StringBuilder();
		for (Entry<String, String> entry : map.entrySet()) {
			optionList.append(entry.getKey());
			optionList.append("=");
			optionList.append(entry.getValue());
			optionList.append("\n");
		}
		String options = optionList.toString();
		optionList = null;
		return options;
	}

	private static QuiltZipPath checkTransformCache(Path transformCacheFolder, Map<String, String> options)
		throws ModResolutionException {

		Path cacheFile = transformCacheFolder.resolve(CACHE_FILE);

		if (!FasterFiles.exists(cacheFile)) {
			Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it's missing");
			erasePreviousTransformCache(transformCacheFolder, cacheFile, null);
			return null;
		}

		if (QuiltLoader.isDevelopmentEnvironment()) {
			Log.info(LogCategory.CACHE, "Not reusing previous transform cache since we're in a development environment");
			erasePreviousTransformCache(transformCacheFolder, cacheFile, null);
			return null;
		}

		try (QuiltZipFileSystem fs = new QuiltZipFileSystem("transform-cache", cacheFile, "")) {
			QuiltZipPath inner = fs.getRoot();
			if (!FasterFiles.isRegularFile(inner.resolve(FILE_TRANSFORM_COMPLETE))) {
				Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it's incomplete!");
				// delete the previous transform cache to prevent FileAlreadyExistsException later
				try { Files.deleteIfExists(cacheFile); } catch(IOException ignored) {}
				return null;
			}
			Path optionFile = inner.resolve("options.txt");

			try (BufferedReader br = Files.newBufferedReader(optionFile, StandardCharsets.UTF_8)) {
				String line;
				Map<String, String> oldOptions = new TreeMap<>(options);
				Map<String, String> newOptions = new TreeMap<>();
				Map<String, String> differingOptions = new TreeMap<>();
				while ((line = br.readLine()) != null) {
					if (line.isEmpty()) {
						continue;
					}
					int eq = line.indexOf('=');
					String key = line.substring(0, eq);
					String value = line.substring(eq + 1);
					String oldValue = oldOptions.remove(key);
					if (oldValue != null) {
						if (!value.equals(oldValue)) {
							differingOptions.put(key, value);
						}
					} else {
						newOptions.put(key, value);
					}
				}

				if (!oldOptions.isEmpty() || !newOptions.isEmpty() || !differingOptions.isEmpty()) {
					if (SHOW_KEY_DIFFERENCE) {
						Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it has different keys:");

						for (Map.Entry<String, String> old : oldOptions.entrySet()) {
							Log.info(LogCategory.CACHE, "  Missing: '" + old.getKey() + "': '" + old.getValue() + "'");
						}

						for (Map.Entry<String, String> added : newOptions.entrySet()) {
							Log.info(LogCategory.CACHE, "  Included: '" + added.getKey() + "': '" + added.getValue() + "'");
						}

						for (Map.Entry<String, String> diff : differingOptions.entrySet()) {
							String key = diff.getKey();
							String oldValue = diff.getValue();
							String newValue = options.get(key);
							Log.info(
								LogCategory.CACHE, "  Different: '" + key + "': '" + oldValue + "' -> '" + newValue + "'"
							);
						}
					} else {
						Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it has "
							+ (oldOptions.size() + newOptions.size() + differingOptions.size())
							+ " different keys."
							+ " (Add '-Dloader.transform_cache.log_changed_keys=true' to see all changes).");
					}
					erasePreviousTransformCache(transformCacheFolder, cacheFile, null);
					return null;
				}
			}
			return inner;
		} catch (IOException | IOError io) {
			if (io instanceof PartiallyWrittenIOException) {
				Log.info(LogCategory.CACHE, "Not reusing previous transform cache since it's incomplete!");
			} else {
				Log.info(
					LogCategory.CACHE,
					"Not reusing previous transform cache since something went wrong while reading it!"
				);
			}

			erasePreviousTransformCache(transformCacheFolder, cacheFile, io);

			return null;
		}
	}

	private static void erasePreviousTransformCache(Path transformCacheFolder, Path cacheFile, Throwable suppressed)
		throws ModResolutionException {

		if (!Files.exists(transformCacheFolder)) {
			return;
		}

		try {
			Files.walkFileTree(transformCacheFolder, Collections.emptySet(), 1, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			ModResolutionException ex = new ModResolutionException(
				"Failed to read an older transform cache file " + cacheFile + " and then delete it!", e
			);
			if (suppressed != null) {
				ex.addSuppressed(suppressed);
			}
			throw ex;
		}
	}

	static final boolean WRITE_CUSTOM = true;

	private static QuiltZipPath createTransformCache(Path transformCacheFile, String options, List<
		ModLoadOption> modList, ModSolveResult result) throws ModResolutionException {

		try {
			Files.createDirectories(transformCacheFile.getParent());
		} catch (IOException e) {
			throw new ModResolutionException("Failed to create the transform cache parent directory!", e);
		}

		if (!Boolean.getBoolean(SystemProperties.DISABLE_OPTIMIZED_COMPRESSED_TRANSFORM_CACHE)) {
			try (QuiltMemoryFileSystem.ReadWrite rw = new QuiltMemoryFileSystem.ReadWrite("transform-cache", true)) {
				QuiltMemoryPath root = rw.getRoot();
				populateTransformCache(root, modList, result);
				Files.write(root.resolve("options.txt"), options.getBytes(StandardCharsets.UTF_8));
				Files.createFile(root.resolve(FILE_TRANSFORM_COMPLETE));
				QuiltZipFileSystem.writeQuiltCompressedFileSystem(root, transformCacheFile);

				return openCache(transformCacheFile);
			} catch (IOException e) {
				throw new ModResolutionException("Failed to create the transform bundle!", e);
			}
		}

		try (FileSystemUtil.FileSystemDelegate fs = FileSystemUtil.getJarFileSystem(transformCacheFile, true)) {
			URI fileUri = transformCacheFile.toUri();
			URI zipUri = new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null);

			Path inner = fs.get().getPath("/");

			populateTransformCache(inner, modList, result);

			Files.write(inner.resolve("options.txt"), options.getBytes(StandardCharsets.UTF_8));
			Files.createFile(inner.resolve(FILE_TRANSFORM_COMPLETE));

		} catch (IOException e) {
			throw new ModResolutionException("Failed to create the transform bundle!", e);
		} catch (URISyntaxException e) {
			throw new ModResolutionException(e);
		}

		return openCache(transformCacheFile);
	}

	private static QuiltZipPath openCache(Path transformCacheFile) throws ModResolutionException {
		try {
			QuiltZipPath path = new QuiltZipFileSystem("transform-cache", transformCacheFile, "").getRoot();
			return path;
		} catch (IOException e) {
			// TODO: Better error message for the gui!
			throw new ModResolutionException("Failed to read the newly written transform cache!", e);
		}
	}

	private static void populateTransformCache(Path root, List<ModLoadOption> modList, ModSolveResult solveResult)
		throws ModResolutionException, IOException {

		RuntimeModRemapper.remap(root, modList);

		if (Boolean.getBoolean(SystemProperties.ENABLE_EXPERIMENTAL_CHASM)) {
			ChasmInvoker.applyChasm(root, modList, solveResult);
		}

		InternalsHiderTransform internalsHider = new InternalsHiderTransform(InternalsHiderTransform.Target.MOD);
		Map<Path, ClassData> classes = new HashMap<>();

		forEachClassFile(root, modList, (mod, file) -> {
			byte[] classBytes = Files.readAllBytes(file);
			classes.put(file, new ClassData(mod, classBytes));
			internalsHider.scanClass(mod, file, classBytes);
			return null;
		});

		for (Map.Entry<Path, ClassData> entry : classes.entrySet()) {
			byte[] newBytes = internalsHider.run(entry.getValue().mod, entry.getValue().classBytes);
			if (newBytes != null) {
				Files.write(entry.getKey(), newBytes);
			}
		}

		internalsHider.finish();
	}

	private static void forEachClassFile(Path root, List<ModLoadOption> modList, ClassConsumer action)
		throws IOException {
		for (ModLoadOption mod : modList) {
			visitFolder(mod, root.resolve(mod.id()), action);
		}
		visitFolder(null, root.resolve(TRANSFORM_CACHE_NONMOD_CLASSLOADABLE), action);
	}

	private static void visitFolder(ModLoadOption mod, Path root, ClassConsumer action) throws IOException {
		if (!Files.isDirectory(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				String folderName = Objects.toString(dir.getFileName());
				if (folderName != null && !couldBeJavaElement(folderName, false)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String fileName = file.getFileName().toString();
				if (fileName.endsWith(".class") && couldBeJavaElement(fileName, true)) {
					byte[] result = action.run(mod, file);
					if (result != null) {
						Files.write(file, result);
					}
				}
				return FileVisitResult.CONTINUE;
			}

			private boolean couldBeJavaElement(String name, boolean ignoreClassSuffix) {
				int end = name.length();
				if (ignoreClassSuffix) {
					end -= ".class".length();
				}
				for (int i = 0; i < end; i++) {
					if (name.charAt(i) == '.') {
						return false;
					}
				}
				return true;
			}
		});
	}

	static final class ClassData {
		final ModLoadOption mod;
		final byte[] classBytes;

		ClassData(ModLoadOption mod, byte[] classBytes) {
			this.mod = mod;
			this.classBytes = classBytes;
		}
	}

	@FunctionalInterface
	interface ClassConsumer {
		byte[] run(ModLoadOption mod, Path file) throws IOException;
	}
}
