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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.ModSolveResult;
import org.quiltmc.loader.impl.discovery.ModResolutionException;
import org.quiltmc.loader.impl.filesystem.PartiallyWrittenIOException;
import org.quiltmc.loader.impl.filesystem.QuiltMapFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltUnifiedFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipCompressionStatistics;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem;
import org.quiltmc.loader.impl.filesystem.QuiltZipPath;
import org.quiltmc.loader.impl.util.AsciiTableGenerator;
import org.quiltmc.loader.impl.util.AsciiTableGenerator.AsciiTableColumn;
import org.quiltmc.loader.impl.util.AsciiTableGenerator.AsciiTableRow;
import org.quiltmc.loader.impl.util.FilePreloadHelper;
import org.quiltmc.loader.impl.util.FileSystemUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonToken;
import org.quiltmc.parsers.json.JsonWriter;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class TransformCacheManager {

	static final boolean SHOW_KEY_DIFFERENCE = Boolean.getBoolean(SystemProperties.LOG_CACHE_KEY_CHANGES);

	/** Sub-folder for classes which are not associated with any mod in particular, but still need to be classloaded. */
	public static final String TRANSFORM_CACHE_NONMOD_CLASSLOADABLE = "Unknown Mod";

	private static final String CACHE_FILE = "files.zip";

	private static final String FILE_TRANSFORM_COMPLETE = "__TRANSFORM_COMPLETE";
	private static final String DENY_LOAD_REASONS_PATH = "deny_load_reasons.json";

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

		final Map<Path, String> referencedFiles;

		if (Boolean.getBoolean(SystemProperties.DISABLE_TRANSFORM_CACHE_REFERENCES)) {
			referencedFiles = Collections.emptyMap();
			map.put("system-property:" + SystemProperties.DISABLE_TRANSFORM_CACHE_REFERENCES, "true");
		} else {
			referencedFiles = new HashMap<>();

			for (ModLoadOption option : modList) {
				Path from = option.from();
				List<List<Path>> srcPaths = option.loader().manager().convertToSourcePaths(from);
				if (srcPaths.size() > 1) {
					// We don't handle joined paths
					continue;
				}
				List<Path> fullPath = srcPaths.get(0);
				referencedFiles.put(fullPath.get(0), option.id());
			}
		}

		QuiltZipPath existing = checkTransformCache(transformCacheFolder, map, referencedFiles);
		boolean isNewlyGenerated = false;
		if (existing == null) {
			existing = createTransformCache(transformCacheFolder.resolve(CACHE_FILE), toString(map), modList, referencedFiles);
			isNewlyGenerated = true;
		} else if (!Boolean.getBoolean(SystemProperties.DISABLE_PRELOAD_TRANSFORM_CACHE)) {
			FilePreloadHelper.preLoad(transformCacheFolder.resolve(CACHE_FILE));
		}
		try {
			Map<String, String> hiddenClasses = new HashMap<>();
			try (JsonReader reader = JsonReader.json(existing.resolve(DENY_LOAD_REASONS_PATH))) {
				reader.beginObject();
				while (reader.peek() == JsonToken.NAME) {
					String clName = reader.nextName();
					String clReason = reader.nextString();
					hiddenClasses.put(clName, clReason);
				}
				reader.endObject();
			}
			return new TransformCacheResult(existing, isNewlyGenerated, hiddenClasses);
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

	private static QuiltZipPath checkTransformCache(Path transformCacheFolder, Map<String, String> options, Map<Path, String> referencedFiles)
		throws ModResolutionException {

		Path cacheFile = transformCacheFolder.resolve(CACHE_FILE);

		if (!FasterFiles.exists(cacheFile)) {
			log("Not reusing previous transform cache since it's missing");
			erasePreviousTransformCache(transformCacheFolder, cacheFile, null);
			return null;
		}

		if (QuiltLoader.isDevelopmentEnvironment()) {
			log("Not reusing previous transform cache since we're in a development environment");
			erasePreviousTransformCache(transformCacheFolder, cacheFile, null);
			return null;
		}

		Map<String, Path> fileReferences = flipMap(referencedFiles);
		try (QuiltZipFileSystem fs = new QuiltZipFileSystem("transform-cache", cacheFile, fileReferences, "")) {
			QuiltZipPath inner = fs.getRoot();
			if (!FasterFiles.isRegularFile(inner.resolve(FILE_TRANSFORM_COMPLETE))) {
				log("Not reusing previous transform cache since it's incomplete!");
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
						log("Not reusing previous transform cache since it has different keys:");

						for (Map.Entry<String, String> old : oldOptions.entrySet()) {
							log("  Missing: '" + old.getKey() + "': '" + old.getValue() + "'");
						}

						for (Map.Entry<String, String> added : newOptions.entrySet()) {
							log("  Included: '" + added.getKey() + "': '" + added.getValue() + "'");
						}

						for (Map.Entry<String, String> diff : differingOptions.entrySet()) {
							String key = diff.getKey();
							String oldValue = diff.getValue();
							String newValue = options.get(key);
							log("  Different: '" + key + "': '" + oldValue + "' -> '" + newValue + "'");
						}
					} else {
						log("Not reusing previous transform cache since it has "
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
				log("Not reusing previous transform cache since it's incomplete!");
			} else {
				log("Not reusing previous transform cache since something went wrong while reading it!");
			}

			erasePreviousTransformCache(transformCacheFolder, cacheFile, io);

			return null;
		}
	}

	private static <OldKey, NewKey> Map<NewKey, OldKey> flipMap(Map<OldKey, NewKey> old) {
		Map<NewKey, OldKey> fileReferences = new HashMap<>();
		for (Entry<OldKey, NewKey> entry : old.entrySet()) {
			OldKey previous = fileReferences.put(entry.getValue(), entry.getKey());
			if (previous != null) {
				throw new IllegalStateException("Duplicate " + entry.getValue() + " mapping " + previous + " and " + entry.getKey());
			}
		}
		return fileReferences;
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
		ModLoadOption> modList, Map<Path, String> referencedFiles) throws ModResolutionException {

		try {
			Files.createDirectories(transformCacheFile.getParent());
		} catch (IOException e) {
			throw new ModResolutionException("Failed to create the transform cache parent directory!", e);
		}

		boolean logStats = Boolean.getBoolean(SystemProperties.LOG_TRANSFORM_CACHE_STATS);

		if (!Boolean.getBoolean(SystemProperties.DISABLE_OPTIMIZED_COMPRESSED_TRANSFORM_CACHE)) {
			try (QuiltUnifiedFileSystem fs = new QuiltUnifiedFileSystem("transform-cache", true)) {
				Path root = fs.getRoot();
				writeTransformCache(options, modList, root);

				QuiltZipFileSystem.writeQuiltCompressedFileSystem(
					root, referencedFiles, transformCacheFile, logStats ? new TransformCacheStorageStats() : null
				);

				return openCache(transformCacheFile, referencedFiles);
			} catch (IOException e) {
				throw new ModResolutionException("Failed to create the transform bundle!", e);
			}
		}

		try (FileSystemUtil.FileSystemDelegate fs = FileSystemUtil.getJarFileSystem(transformCacheFile, true)) {
			URI fileUri = transformCacheFile.toUri();
			URI zipUri = new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null);

			Path inner = fs.get().getPath("/");

			writeTransformCache(options, modList, inner);

		} catch (IOException e) {
			throw new ModResolutionException("Failed to create the transform bundle!", e);
		} catch (URISyntaxException e) {
			throw new ModResolutionException(e);
		}

		if (logStats) {
			try {
				log("Zip transform cache size: " + NumberFormat.getIntegerInstance().format(Files.size(transformCacheFile)));
			} catch (IOException e) {
				Log.warn(LogCategory.CACHE, "Zip transform cache size: Unknown (an exception was thrown!)", e);
			}
		}

		return openCache(transformCacheFile, referencedFiles);
	}

	private static void writeTransformCache(String options, List<ModLoadOption> modList, Path root) throws ModResolutionException, IOException {
		TransformCache cache = TransformCacheGenerator.generate(root, modList);
		QuiltMapFileSystem.dumpEntries(root.getFileSystem(), "after-populate");
		Files.write(root.resolve("options.txt"), options.getBytes(StandardCharsets.UTF_8));
		try (JsonWriter json = JsonWriter.json(Files.newBufferedWriter(root.resolve(DENY_LOAD_REASONS_PATH)))) {
			if (true) {
				json.setIndent(" ");
			}
			json.beginObject();
			for (Map.Entry<String,String> entry : cache.getHiddenClasses().entrySet()) {
				json.name(entry.getKey());
				json.value(entry.getValue());
			}
			json.endObject();
		}
		Files.createFile(root.resolve(FILE_TRANSFORM_COMPLETE));
	}

	private static QuiltZipPath openCache(Path transformCacheFile, Map<Path, String> referencedFiles) throws ModResolutionException {
		try {
			Map<String, Path> files = flipMap(referencedFiles);
			QuiltZipPath path = new QuiltZipFileSystem("transform-cache", transformCacheFile, files, "").getRoot();
			return path;
		} catch (IOException e) {
			// TODO: Better error message for the gui!
			throw new ModResolutionException("Failed to read the newly written transform cache!", e);
		}
	}

	private static void log(String message) {
		Log.info(LogCategory.CACHE, message);
	}

	private static final class TransformCacheStorageStats implements QuiltZipCompressionStatistics {
		static final class PerModStats {
			final AtomicLong internal = new AtomicLong(), external = new AtomicLong();
			Path jarFile;
		}

		final AtomicLong totalInternal = new AtomicLong(), totalExternal = new AtomicLong();
		final Map<String, PerModStats> perModStats = new ConcurrentHashMap<>();

		@Override
		public void onStoreReference(Path file, Path external, int rawSize, int storedSize, boolean isCompressed) {
			totalExternal.addAndGet(storedSize);
			if (file.getNameCount() >= 2) {
				String mod = file.getName(0).toString();
				PerModStats stats = perModStats.computeIfAbsent(mod, m -> new PerModStats());
				stats.external.getAndAdd(storedSize);
				stats.jarFile = external;
			}
		}

		@Override
		public void onStoreInternal(Path file, int rawSize, int storedSize, boolean isCompressed) {
			totalInternal.addAndGet(storedSize);
			if (file.getNameCount() >= 2) {
				String mod = file.getName(0).toString();
				perModStats.computeIfAbsent(mod, m -> new PerModStats()).internal.getAndAdd(storedSize);
			}
		}

		@Override
		public void finish(long totalSize) {
			final NumberFormat f = NumberFormat.getIntegerInstance();

			log("Transform cache storage statistics:");
			log("  - Final cache file size: " + f.format(totalSize) + " bytes");

			List<String> sortedMods = new ArrayList<>(perModStats.keySet());
			sortedMods.sort(null);
			AsciiTableGenerator table = new AsciiTableGenerator();
			AsciiTableColumn columnMod = table.addColumn("Mod", false);
			AsciiTableColumn columnOriginal = table.addColumn("Original Jar", true);
			AsciiTableColumn columnModified = table.addColumn("Modified", true);
			AsciiTableColumn columnRef = table.addColumn("Referenced", true);
			AsciiTableColumn columnPercent = table.addColumn("Percent Modified", true);
			for (String mod : sortedMods) {
				PerModStats stats = perModStats.get(mod);
				long original = stats.external.get();
				long modified = stats.internal.get();
				AsciiTableRow row = table.addRow();
				row.put(columnMod, mod);
				if (stats.jarFile == null) {
					row.put(columnOriginal, "Unused");
				} else {
					try {
						row.put(columnOriginal, f.format(Files.size(stats.jarFile)));
					} catch (IOException e) {
						e.printStackTrace();
						row.put(columnOriginal, "?!?");
					}
				}
				row.put(columnModified, f.format(modified));
				row.put(columnRef, f.format(original));
				row.put(columnPercent, ((modified * 1000) / (original + modified) / 10.0) + "%");
			}

			table.addBarRow();
			AsciiTableRow totals = table.addRow();
			totals.put(columnMod, "Total");
			long modified = totalInternal.get();
			long original = totalExternal.get();
			totals.put(columnModified, f.format(modified));
			totals.put(columnRef, f.format(original));
			totals.put(columnPercent, ((modified * 1000) / (original + modified) / 10.0) + "%");

			StringBuilder sb = new StringBuilder("Per-mod statistics:\n");
			table.appendTable(line -> {
				sb.append(line);
				sb.append("\n");
			});
			log(sb.toString());
		}
	}
}
