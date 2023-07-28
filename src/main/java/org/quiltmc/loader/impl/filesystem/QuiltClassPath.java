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

package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/** Essentially a {@link QuiltJoinedFileSystem} but which caches all paths in advance. Not exposed as a filesystem since
 * this is a bit more dynamic than that. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class QuiltClassPath {

	private static final AtomicInteger ZIP_SCANNER_COUNT = new AtomicInteger();
	private static final Queue<Runnable> SCAN_TASKS = new ArrayDeque<>();
	private static final Set<Thread> ACTIVE_SCANNERS = new HashSet<>();

	/** Saves quite a bit of memory to use our own hash table, while also not being too much work (since we already key
	 * by int hash) */
	private static final boolean USE_CUSTOM_TABLE = true;

	private final List<Path> roots = new CopyOnWriteArrayList<>();
	private final FileMap files = USE_CUSTOM_TABLE ? new HashTableFileMap() : new StandardFileMap();

	public void addRoot(Path root) {
		if (root instanceof QuiltJoinedPath) {
			QuiltJoinedFileSystem fs = ((QuiltJoinedPath) root).fs;

			for (Path from : fs.from) {
				addRoot(from);
			}

		} else if (root instanceof QuiltMemoryPath) {
			QuiltMemoryFileSystem fs = ((QuiltMemoryPath) root).fs;

			if (fs instanceof QuiltMemoryFileSystem.ReadWrite) {
				Log.warn(LogCategory.GENERAL, "Adding read/write FS root to the classpath, this may slow down class loading: " + fs.name);
				roots.add(root);
			} else {

				files.ensureCapacityFor(fs.getEntryCount());

				for (Path key : fs.getEntryPathIterator()) {
					putQuickFile(key.toString(), key);
				}
			}

		} else if (root instanceof QuiltMapPath<?, ?>) {
			QuiltMapFileSystem<?, ?> fs = ((QuiltMapPath<?, ?>) root).fs;

			files.ensureCapacityFor(fs.getEntryCount());

			for (Path key : fs.getEntryPathIterator()) {
				putQuickFile(key.toString(), key);
			}

		} else {

			FileSystem fs = root.getFileSystem();

			if ("jar".equals(fs.provider().getScheme())) {
				// Assume it's read-only for speed
				roots.add(root);
				beginScanning(root);
				return;
			}

			if (!QuiltLoader.isDevelopmentEnvironment()) { // Directory and other unique filesystems expected in a dev environment
				Log.warn(LogCategory.GENERAL, "Adding unknown root to the classpath, this may slow down class loading: " + root.getFileSystem() + " " + root);
			}

			roots.add(root);
		}
	}

	private void putQuickFile(String fileName, Path file) {
		files.put(file);
	}

	private void beginScanning(Path zipRoot) {
		synchronized (QuiltClassPath.class) {
			SCAN_TASKS.add(() -> scanZip(zipRoot));
			int scannerCount = ACTIVE_SCANNERS.size();
			if (scannerCount < 4 && scannerCount < SCAN_TASKS.size()) {
				Thread scanner = new Thread("QuiltClassPath ZipScanner#" + ZIP_SCANNER_COUNT.incrementAndGet()) {
					@Override
					public void run() {
						while (true) {
							Runnable next;
							synchronized (QuiltClassPath.class) {
								next = SCAN_TASKS.poll();
								if (next == null) {
									ACTIVE_SCANNERS.remove(this);
									break;
								}
							}
							next.run();
						}
					}
				};
				ACTIVE_SCANNERS.add(scanner);
				scanner.setDaemon(true);
				scanner.start();
			}
		}
	}

	private void scanZip(Path zipRoot) {
		try {
			long start = System.nanoTime();
			Files.walkFileTree(zipRoot, new SimpleFileVisitor<Path>() {

				// A previous version of this code used Path.relativize to construct the output paths
				// But Java 8's ZipFileSystem doesn't implement this correctly, so we do it manually instead.

				final Deque<String> stack = new ArrayDeque<>();
				int foldersRead = 0;
				int filesRead = 0;

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path fileName = dir.getFileName();
					if (fileName != null) {
						stack.addLast(fileName.toString());
					} else if (!"/".equals(dir.toString())) {
						throw new IOException("Unknown directory with no file names " + dir.getClass() + " '" + dir + "'");
					} else if (!stack.isEmpty()) {
						if (stack.size() == 1 && stack.contains("/")) {
							// Java 8's ZipFileSystem seems to repeat the zip forever?
							Log.info(LogCategory.GENERAL, "Encountered the root directory multiple times, terminating "
								+ zipRoot.getClass() + " (after reading " + foldersRead + " folders and "
								+ filesRead + " files)");
							return FileVisitResult.TERMINATE;
						}
						throw new IOException("Encountered multiple roots? (Non-empty stack): " + stack);
					} else {
						stack.addLast("/");
					}
					foldersRead++;
					putQuickFile(dir.toString(), dir);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					stack.removeLast();
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					StringBuilder relativeString = new StringBuilder();
					boolean first = true;
					for (String path : stack) {
						if (!first) {
							relativeString.append(path);
						}
						relativeString.append("/");
						first = false;
					}
					filesRead++;
					relativeString.append(file.getFileName().toString());
					putQuickFile(relativeString.toString(), file);
					return FileVisitResult.CONTINUE;
				}
			});
			roots.remove(zipRoot);
			long end = System.nanoTime();
			Log.info(LogCategory.GENERAL, "Took " + (end - start) / 1000 + "us to scan " + zipRoot);
		} catch (IOException e) {
			Log.warn(LogCategory.GENERAL, "Failed to scan " + zipRoot + "!", e);
		}
	}

	public Path findResource(String path) {
		String absolutePath = path;
		if (!path.startsWith("/")) {
			absolutePath = "/" + path;
		}
		Path quick = files.get(absolutePath);

		if (quick instanceof HashCollisionPath) {
			quick = ((HashCollisionPath) quick).get(absolutePath);
		}

		if (quick != null) {
			if (quick instanceof OverlappingPath) {
				return ((OverlappingPath) quick).getFirst();
			}
			return quick;
		}

		for (Path root : roots) {
			Path ext = root.resolve(path);
			if (FasterFiles.exists(ext)) {
				return ext;
			}
		}

		return null;
	}

	public List<Path> getResources(String path) {
		String absolutePath = path;
		if (!path.startsWith("/")) {
			absolutePath = "/" + path;
		}

		Path quick = files.get(absolutePath);

		if (quick instanceof HashCollisionPath) {
			quick = ((HashCollisionPath) quick).get(absolutePath);
		}

		List<Path> paths = new ArrayList<>();
		if (quick != null) {
			if (quick instanceof OverlappingPath) {
				Collections.addAll(paths, ((OverlappingPath) quick).paths);
			} else {
				paths.add(quick);
			}
		}

		for (Path root : roots) {
			Path ext = root.resolve(path);
			if (FasterFiles.exists(ext)) {
				paths.add(ext);
			}
		}

		return Collections.unmodifiableList(paths);
	}

	private static boolean isEqualPath(Path in, Path value) {
		if (in instanceof QuiltBasePath) {
			return ((QuiltBasePath<?, ?>) in).isToStringEqual(value);
		}
		int namesIn = in.getNameCount();
		int namesVal = value.getNameCount();
		if (namesIn != namesVal) {
			return false;
		}

		for (int i = 0; i < namesIn; i++) {
			if (!in.getName(i).toString().equals(value.getName(i).toString())) {
				return false;
			}
		}

		return true;
	}

	private static boolean isEqual(String key, Path value) {

		boolean should = key.equals(value.toString());

		int offset = key.length();
		int names = value.getNameCount();
		for (int part = names - 1; part >= 0; part--) {
			String sub = value.getName(part).toString();
			offset -= sub.length();
			if (!key.startsWith(sub, offset)) {
				if (should) {
					throw new IllegalStateException("Optimized equality test seems to be broken for " + key);
				}
				return false;
			}
			offset--;
			if (offset < 0) {
				if (should) {
					throw new IllegalStateException("Optimized equality test seems to be broken for " + key);
				}
				return false;
			}
			if (key.charAt(offset) != '/') {
				if (should) {
					throw new IllegalStateException("Optimized equality test seems to be broken for " + key);
				}
				return false;
			}
		}
		return true;
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static abstract class FileMap {
		final Path get(String key) {
			Path result = get0(key);

			if (result instanceof HashCollisionPath) {
				result = ((HashCollisionPath) result).get(key);
			} else {
				if (result != null && !isEqual(key, result)) {
					System.out.println(key + " != " + result);
					return null;
				}
			}

			return result;
		}

		abstract Path get0(String key);

		abstract void ensureCapacityFor(int newPathCount);

		abstract void put(Path newPath);

		protected Path computeNewPath(Path current, Path file) {
			if (current == null) {
				return file;
			} else if (current instanceof HashCollisionPath) {
				HashCollisionPath collision = (HashCollisionPath) current;
				int equalIndex = collision.getEqualPathIndex(file);
				if (equalIndex < 0) {
					Path[] newArray = new Path[collision.values.length + 1];
					System.arraycopy(collision.values, 0, newArray, 0, collision.values.length);
					newArray[collision.values.length] = file;
					collision.values = newArray;
				} else {
					Path equal = collision.values[equalIndex];
					if (equal instanceof OverlappingPath) {
						OverlappingPath multi = (OverlappingPath) equal;
						multi.addPath(file);
						multi.data &= ~OverlappingPath.FLAG_HAS_WARNED;
					} else {
						OverlappingPath multi = new OverlappingPath();
						multi.paths = new Path[] { equal, file };
						collision.values[equalIndex] = multi;
					}
				}
				return collision;
			} else if (current instanceof OverlappingPath) {
				if (isEqualPath(file, ((OverlappingPath) current).paths[0])) {
					OverlappingPath multi = (OverlappingPath) current;
					multi.addPath(file);multi.data &= ~OverlappingPath.FLAG_HAS_WARNED;
					return multi;
				} else {
					return new HashCollisionPath(current, file);
				}
			} else {
				if (isEqualPath(file, current)) {
					OverlappingPath multi = new OverlappingPath();
					multi.paths = new Path[] { current, file };
					return multi;
				} else {
					return new HashCollisionPath(current, file);
				}
			}
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class StandardFileMap extends FileMap {
		final Map<Integer, Path> files = new ConcurrentHashMap<>();

		public StandardFileMap() {}

		@Override
		void ensureCapacityFor(int newPathCount) {
			// NO-OP (only the table version uses this)
		}

		@Override
		void put(Path path) {
			files.compute(path.toString().hashCode(), (a, current) -> computeNewPath(current, path));
		}

		@Override
		Path get0(String key) {
			return files.get(key.hashCode());
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class HashTableFileMap extends FileMap {
		static final double FILL_PERCENT = 0.75;
		Path[] table = new Path[128];
		int entryCount;

		public HashTableFileMap() {}

		@Override
		Path get0(String key) {
			Path[] tbl = table;
			// Stored in a variable for thread safety
			return tbl[key.hashCode() & tbl.length - 1];
		}

		@Override
		synchronized void ensureCapacityFor(int newPathCount) {
			int result = entryCount + newPathCount;
			int newSize = table.length;
			while (newSize * FILL_PERCENT <= result) {
				newSize *= 2;
			}
			if (newSize != table.length) {
				rehash(newSize);
			}
		}

		@Override
		synchronized void put(Path newPath) {
			entryCount++;
			if (table.length * FILL_PERCENT < entryCount) {
				rehash(table.length * 2);
			}
			int index = hashCode(newPath) & table.length - 1;
			table[index] = computeNewPath(table[index], newPath);
		}

		private static int hashCode(Path path) {
			if (path instanceof QuiltBasePath) {
				return ((QuiltBasePath<?, ?>) path).toStringHashCode();
			}
			return path.toString().hashCode();
		}

		private void rehash(int newSize) {
			Path[] oldTable = table;
			table = new Path[newSize];
			entryCount = 0;
			Path[] array1 = { null };
			Path[] subIter = null;
			for (Path sub : oldTable) {
				if (sub == null) {
					continue;
				}

				if (sub instanceof HashCollisionPath) {
					HashCollisionPath collision = (HashCollisionPath) sub;
					subIter = collision.values;
				} else {
					array1[0] = sub;
					subIter = array1;
				}

				for (Path sub2 : subIter) {
					if (sub2 instanceof OverlappingPath) {
						OverlappingPath overlap = (OverlappingPath) sub2;
						int index = overlap.paths[0].toString().hashCode() & table.length - 1;
						for (Path sub3 : overlap.paths) {
							table[index] = computeNewPath(table[index], sub3);
						}
					} else {
						put(sub2);
					}
				}
			}
		}
	}

	/** Used so we don't need to store a full {@link String} for every file we track. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	private static final class HashCollisionPath extends NullPath {

		Path[] values;

		public HashCollisionPath(Path a, Path b) {
			if (a instanceof HashCollisionPath || b instanceof HashCollisionPath) {
				throw new IllegalStateException("Wrong constructor!");
			}
			values = new Path[] { a, b };
		}

		public HashCollisionPath(HashCollisionPath a, Path b) {
			values = new Path[a.values.length + 1];
			System.arraycopy(a.values, 0, values, 0, a.values.length);
			values[a.values.length] = b;
		}

		@Override
		protected IllegalStateException illegal() {
			throw new IllegalStateException(
				"QuiltClassPath must NEVER return a HashCollisionPath - something has gone very wrong!"
			);
		}

		/** @return The equal path index in {@link #values}, or a negative number if it's not present. */
		public int getEqualPathIndex(Path in) {
			for (int i = 0; i < values.length; i++) {
				Path value = values[i];
				if (value instanceof OverlappingPath) {
					value = ((OverlappingPath) value).paths[0];
				}
				if (isEqualPath(in, value)) {
					return i;
				}
			}
			return -1;
		}

		public Path get(String key) {
			for (Path value : values) {
				if (value instanceof OverlappingPath) {
					value = ((OverlappingPath) value).paths[0];
				}
				if (isEqual(key, value)) {
					return value;
				}
			}
			return null;
		}
	}

	/** Used when multiple paths are stored as values in {@link QuiltClassPath#files}. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	private static final class OverlappingPath extends NullPath {

		static final int FLAG_HAS_WARNED = 1 << 31;
		static final int MASK_HASH = Integer.MAX_VALUE;

		int data;
		Path[] paths;

		public OverlappingPath(int fullHash) {
			data = fullHash & MASK_HASH;
		}

		public OverlappingPath() {}

		public void addPath(Path file) {
			paths = Arrays.copyOf(paths, paths.length + 1);
			paths[paths.length - 1] = file;
		}

		@Override
		protected IllegalStateException illegal() {
			throw new IllegalStateException(
				"QuiltClassPath must NEVER return an OverlappingPath - something has gone very wrong!"
			);
		}

		public Path getFirst() {
			if ((data & ~FLAG_HAS_WARNED) != 0) {
				data |= FLAG_HAS_WARNED;
				String exposedName = paths[0].toString();
				StringBuilder sb = new StringBuilder();
				sb.append("Multiple paths added for '");
				sb.append(exposedName);
				sb.append("', but only a single one can be returned!");
				if ("/".equals(exposedName)) {
					// Since every entry on the classpath contains a root folder
					// it's not useful to log every classpath entry.
					// So instead we'll log the current stacktrace.
					StringWriter writer = new StringWriter();
					new Throwable("Overlapping Path Caller").printStackTrace(new PrintWriter(writer));
					sb.append("\n");
					sb.append(writer.toString());
				} else {
					for (Path path : paths) {
						sb.append("\n - ");
						sb.append(path.getFileSystem());
						sb.append(" ");
						sb.append(path);
					}
				}
				Log.warn(LogCategory.GENERAL, sb.toString());
			}
			return paths[0];
		}
	}
}
