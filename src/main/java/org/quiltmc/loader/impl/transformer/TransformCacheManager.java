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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.filesystem.PartiallyWrittenIOException;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedPath;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.util.FilePreloadHelper;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class TransformCacheManager {

	static final boolean SHOW_KEY_DIFFERENCE = Boolean.getBoolean(SystemProperties.LOG_CACHE_KEY_CHANGES);

	/** Sub-folder for classes which are not associated with any mod in particular, but still need to be classloaded. */
	public static final String TRANSFORM_CACHE_NONMOD_CLASSLOADABLE = "Unknown Mod";

	private static final String CACHE_FILE = "files.zip";

	private static final String FILE_TRANSFORM_COMPLETE = "__TRANSFORM_COMPLETE";
	private static final String HIDDEN_CLASSES_PATH = "hidden_classes.txt";

	public static TransformCacheResult populateTransformBundle(Path transformCacheFolder, List<ModLoadOption> modList,
		Map<String, String> modOriginHash, ModSolveResult result) throws ModResolutionException {
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
			String name = modOption.from().getFileName().toString();
			map.put("mod:" + mod.getKey(), name + " " + modOriginHash.get(modOption.id()));
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
			existing = createTransformCache(transformCacheFolder.resolve(CACHE_FILE), toString(map), modList);
			isNewlyGenerated = true;
		} else if (!Boolean.getBoolean(SystemProperties.DISABLE_PRELOAD_TRANSFORM_CACHE)) {
			FilePreloadHelper.preLoad(transformCacheFolder.resolve(CACHE_FILE));
		}
		try {
			return new TransformCacheResult(existing, isNewlyGenerated, new HashSet<>(Files.readAllLines(existing.resolve(HIDDEN_CLASSES_PATH))));
		} catch (IOException e) {
			throw new ModResolutionException("Failed to read hidden classes in the transform cache file!", e);
		}
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
				erasePreviousTransformCache(transformCacheFolder, cacheFile, null);
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
		ModLoadOption> modList) throws ModResolutionException {

		try {
			Files.createDirectories(transformCacheFile.getParent());
		} catch (IOException e) {
			throw new ModResolutionException("Failed to create the transform cache parent directory!", e);
		}

		if (!Boolean.getBoolean(SystemProperties.DISABLE_OPTIMIZED_COMPRESSED_TRANSFORM_CACHE)) {
			try (QuiltUnifiedFileSystem fs = new QuiltUnifiedFileSystem("transform-cache", true)) {
				QuiltUnifiedPath root = fs.getRoot();
				TransformCache cache = TransformCacheGenerator.generate(root, modList);
				fs.dumpEntries("after-populate");
				Files.write(root.resolve("options.txt"), options.getBytes(StandardCharsets.UTF_8));
				Files.write(root.resolve(HIDDEN_CLASSES_PATH), cache.getHiddenClasses());
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

			TransformCacheGenerator.generate(inner, modList);

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
}
