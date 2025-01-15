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
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

/** Essentially a {@link QuiltJoinedFileSystem} but which caches all paths in advance. Not exposed as a filesystem since
 * this is a bit more dynamic than that.
 * 
 * @param <D> Optional extra data that may be stored alongside paths. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class QuiltClassPath<D> {

	private static final boolean VALIDATE = SystemProperties.getBoolean(
		SystemProperties.VALIDATE_QUILT_CLASS_PATH, SystemProperties.VALIDATION_LEVEL > 0
	);

	private static final AtomicInteger ZIP_SCANNER_COUNT = new AtomicInteger();
	private static final Queue<Runnable> SCAN_TASKS = new ArrayDeque<>();
	private static final Set<Thread> ACTIVE_SCANNERS = new HashSet<>();

	/** Saves quite a bit of memory to use our own hash table, while also not being too much work (since we already key
	 * by int hash) */
	private static final boolean USE_CUSTOM_TABLE = !Boolean.getBoolean(SystemProperties.DISABLE_QUILT_CLASS_PATH_CUSTOM_TABLE);

	private final Class<D> dataClass;
	private final List<PathData> allRoots = VALIDATE ? new CopyOnWriteArrayList<>() : null;
	private final AtomicReference<PathData[]> roots = new AtomicReference<>(new PathData[0]);
	private final FileMap files = USE_CUSTOM_TABLE ? new HashTableFileMap() : new StandardFileMap();

	/** Set if {@link #VALIDATE} finds a problem. */
	private static boolean printFullDetail = false;

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	public static final class PathResult<D> {
		public final Path path;

		/** The root which the data was associated with. May be null if the path was added with no data. */
		public final Path root;

		/** The data associated with the root path. May be null if no data was added for the root path. */
		public final D data;

		PathResult(Path path, Path root, D data) {
			this.path = path;
			this.root = root;
			this.data = data;
		}

		@Override
		public String toString() {
			return "PathResult{path=\"" + path + "\", root=\"" + root + "\", data=" + data + "}";
		}

		@Override
		public int hashCode() {
			return Objects.hash(path, System.identityHashCode(root), System.identityHashCode(data));
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof PathResult)) {
				return false;
			}
			PathResult<?> other = (PathResult<?>) obj;
			return Objects.equals(path, other.path) //
				&& root == other.root && data == other.data;
		}
	}

	private static final class PathData {
		final Path path;
		final Object data;

		PathData(Path path, Object data) {
			this.path = path;
			this.data = data;
		}
	}

	public QuiltClassPath() {
		this(null);
	}

	public QuiltClassPath(Class<D> dataClass) {
		this.dataClass = dataClass;
	}

	public void addRoot(Path root) {
		addRoot(root, null);
	}

	public void addRoot(Path root, D data) {
		if (dataClass == null) {
			if (data != null) {
				throw new IllegalArgumentException("Cannot add extra data if this doesn't have a data class!");
			}
		} else if (data != null) {
			// We might as well validate this
			dataClass.cast(data);
		}

		if (root instanceof QuiltJoinedPath) {
			QuiltJoinedFileSystem fs = ((QuiltJoinedPath) root).fs;

			for (Path from : fs.from) {
				addRoot(from, data);
			}

			return;
		}

		if (VALIDATE) {
			allRoots.add(new PathData(root, data));
		}

		if (root instanceof QuiltMemoryPath) {
			QuiltMemoryFileSystem fs = ((QuiltMemoryPath) root).fs;

			if (fs instanceof QuiltMemoryFileSystem.ReadWrite) {
				Log.warn(LogCategory.GENERAL, "Adding read/write FS root to the classpath, this may slow down class loading: " + fs.name);
				addRootToInternalArray(root, data);
			} else {

				files.ensureCapacityFor(fs.getEntryCount());

				ExtraData extraData = data == null ? null : new ExtraData(root, data);

				for (Path key : fs.getEntryPathIterator()) {
					putQuickFile(key.toString(), key, extraData);
				}
			}

		} else if (root instanceof QuiltMapPath<?, ?>) {
			QuiltMapFileSystem<?, ?> fs = ((QuiltMapPath<?, ?>) root).fs;

			files.ensureCapacityFor(fs.getEntryCount());

			ExtraData extraData = data == null ? null : new ExtraData(root, data);

			for (Path key : fs.getEntryPathIterator()) {
				putQuickFile(key.toString(), key, extraData);
			}

		} else {

			FileSystem fs = root.getFileSystem();

			if ("jar".equals(fs.provider().getScheme())) {
				// Assume it's read-only for speed
				addRootToInternalArray(root, data);
				ExtraData extraData = data == null ? null : new ExtraData(root, data);
				beginScanning(root, extraData);
				return;
			}

			if (!QuiltLoader.isDevelopmentEnvironment()) { // Directory and other unique filesystems expected in a dev environment
				Log.warn(LogCategory.GENERAL, "Adding unknown root to the classpath, this may slow down class loading: " + root.getFileSystem() + " " + root);
			}

			addRootToInternalArray(root, data);
		}
	}

	private void addRootToInternalArray(Path root, D data) {
		roots.updateAndGet(array -> {
			PathData[] array2 = Arrays.copyOf(array, array.length + 1);
			array2[array.length] = new PathData(root, data);
			return array2;
		});
	}

	private void putQuickFile(String fileName, Path file, ExtraData extraData) {
		files.put(file, extraData);
	}

	private void beginScanning(Path zipRoot, ExtraData extraData) {
		synchronized (QuiltClassPath.class) {
			SCAN_TASKS.add(() -> scanZip(zipRoot, extraData));
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

	private void scanZip(Path zipRoot, ExtraData extraData) {
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
					putQuickFile(dir.toString(), dir, extraData);
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
					putQuickFile(relativeString.toString(), file, extraData);
					return FileVisitResult.CONTINUE;
				}
			});
			roots.updateAndGet(array -> {
				PathData[] array2 = new PathData[array.length - 1];
				int output = 0;
				for (int i = 0; i < array.length; i++) {
					PathData old = array[i];
					if (old.path != zipRoot) {
						array2[output++] = old;
					}
				}
				return array2;
			});
			long end = System.nanoTime();
			Log.info(LogCategory.GENERAL, "Took " + (end - start) / 1000 + "us to scan " + zipRoot.getFileSystem() + " " + zipRoot);
		} catch (IOException e) {
			Log.warn(LogCategory.GENERAL, "Failed to scan " + zipRoot + "!", e);
		}
	}

	public Path findResource(String path) {
		PathResult<D> result = findResourceData(path);
		return result != null ? result.path : null;
	}

	public PathResult<D> findResourceData(String path) {
		PathData[] rootsCopy0 = roots.get();
		PathResult<D> quick = quickFindResource(path);
		if (VALIDATE) {
			PathResult<D> slow = findResourceIn(allRoots.toArray(new PathData[0]), path);
			if (!Objects.equals(slow, quick)) {
				PathResult<D> quick2 = quickFindResource(path);
				PathData[] rootsCopy1 = roots.get();
				IllegalStateException ex = new IllegalStateException(
					"quickFindResource( " + path + " ) returned a different path to the slow find resource!"
						+ "\nquick 1 = " + describePath(quick)
						+ "\nslow = " + describePath(slow)
						+ "\nquick 2 = " + describePath(quick2)
						+ "\nroots 1 = " + describePathDatas(Arrays.asList(rootsCopy0))
						+ "\nroots 2 = " + describePathDatas(Arrays.asList(rootsCopy1))
						+ "\nall_roots = " + describePathDatas(allRoots)
				);
				ex.printStackTrace();
				printFullDetail = true;
				quickFindResource(path);
				throw ex;
			}
		}
		return quick;
	}

	private D castData(Object data) {
		if (dataClass == null) {
			if (data != null) {
				throw new IllegalStateException("Expected no data, but got " + data.getClass());
			}
			return null;
		} else {
			return dataClass.cast(data);
		}
	}

	private static String describePath(PathData path) {
		if (path == null) {
			return "null";
		}
		return describePath(path.path);
	}

	private static String describePath(PathResult<?> path) {
		if (path == null) {
			return "null";
		}
		return describePath(path.path);
	}

	private static String describePath(Path path) {
		if (path == null) {
			return "null";
		}
		if (path instanceof HashCollisionPath) {
			return ((HashCollisionPath) path).describe();
		}
		if (path instanceof OverlappingPath) {
			return ((OverlappingPath) path).describe();
		}
		if (path instanceof ExtraDataPath) {
			return ((ExtraDataPath) path).describe();
		}
		return "'" + path + "'.fs:" + path.getFileSystem();
	}

	private PathResult<D> quickFindResource(String path) {
		String absolutePath = path;
		if (!path.startsWith("/")) {
			absolutePath = "/" + path;
		}
		if (printFullDetail) {
			Log.warn(LogCategory.GENERAL, "quickFindResource(" + path + ")");
		}

		// Obtaining the roots array before we get from the file map prevents a race condition
		// where the following happens:
		/*
		 * step | findResource | scanner
		 *  1   | files.get()  |
		 *  2   |              | files.put()
		 *  3   |              | roots.remove()
		 *  4   | loop(roots)  |
		 */
		// Grabbing a copy of the roots array before we check in files ensures we never miss a path
		// This fix is also applied to quickGetResources
		PathData[] fullArray = roots.get();
		Path quick = files.get(absolutePath);

		if (printFullDetail) {
			Log.warn(LogCategory.GENERAL, "- files.get(" + absolutePath + ") -> " + describePath(quick));
		}

		if (quick instanceof HashCollisionPath) {
			quick = ((HashCollisionPath) quick).get(absolutePath);
			if (printFullDetail) {
				Log.warn(LogCategory.GENERAL, "- after hash collision -> " + describePath(quick));
			}
		}

		if (quick != null) {
			if (quick instanceof OverlappingPath) {
				quick = ((OverlappingPath) quick).getFirst();
			}

			if (quick instanceof ExtraDataPath) {
				ExtraDataPath dataPath = (ExtraDataPath) quick;
				return new PathResult<>(dataPath.path, dataPath.data.root, castData(dataPath.data.data));
			} else {
				return new PathResult<>(quick, null, null);
			}
		}

		return findResourceIn(fullArray, path);
	}

	private PathResult<D> findResourceIn(PathData[] array, String path) {
		for (PathData root : array) {
			Path ext = root.path.resolve(path);
			if (FasterFiles.exists(ext)) {
				if (root.data == null) {
					return new PathResult<>(ext, null, null);
				} else {
					return new PathResult<>(ext, root.path, castData(root.data));
				}
			}
		}
		return null;
	}

	public List<Path> getResources(String path) {
		List<PathResult<D>> fullResult = getAllResourceData(path);
		List<Path> list = new ArrayList<>(fullResult.size());
		for (PathResult<D> result : fullResult) {
			list.add(result.path);
		}
		return list;
	}

	public List<PathResult<D>> getAllResourceData(String path) {
		List<PathResult<D>> quick = quickGetResources(path);
		if (VALIDATE) {
			List<PathResult<D>> slow = new ArrayList<>();
			getResourcesIn(allRoots.toArray(new PathData[0]), path, slow);
			if (!quick.equals(slow)) {
				List<PathResult<D>> quick2 = quickGetResources(path);
				IllegalStateException ex = new IllegalStateException(
					"quickGetResources( " + path + " ) returned a different list of paths to the slow get resources!"
						+ "\nquick 1 = " + describePathResults(quick)
						+ "\nslow    = " + describePathResults(slow)
						+ "\nquick 2 = " + describePathResults(quick2)
				);
				ex.printStackTrace();
				printFullDetail = true;
				quickGetResources(path);
				throw ex;
			}
		}
		return quick;
	}

	private static String describePathDatas(List<PathData> paths) {
		StringBuilder sb = new StringBuilder("[");
		for (PathData p : paths) {
			if (sb.length() > 1) {
				sb.append(", ");
			}
			sb.append(describePath(p));
		}
		sb.append(" ]");
		return sb.toString();
	}

	private static <D> String describePathResults(List<PathResult<D>> paths) {
		StringBuilder sb = new StringBuilder("[");
		for (PathResult<D> p : paths) {
			if (sb.length() > 1) {
				sb.append(", ");
			}
			sb.append(describePath(p));
		}
		sb.append(" ]");
		return sb.toString();
	}

	private static String describePaths(List<Path> paths) {
		StringBuilder sb = new StringBuilder("[");
		for (Path p : paths) {
			if (sb.length() > 1) {
				sb.append(", ");
			}
			sb.append(describePath(p));
		}
		sb.append(" ]");
		return sb.toString();
	}

	private List<PathResult<D>> quickGetResources(String path) {
		String absolutePath = path;
		if (!path.startsWith("/")) {
			absolutePath = "/" + path;
		}

		// Thread race condition fix
		// see "quickFindResource" for details
		PathData[] rootsArray = roots.get();
		Path quick = files.get(absolutePath);

		if (quick instanceof HashCollisionPath) {
			quick = ((HashCollisionPath) quick).get(absolutePath);
		}

		List<PathResult<D>> paths = new ArrayList<>();
		if (quick != null) {
			if (quick instanceof OverlappingPath) {
				for (Path real : ((OverlappingPath) quick).paths) {
					if (real instanceof ExtraDataPath) {
						ExtraDataPath dataPath = (ExtraDataPath) real;
						paths.add(new PathResult<>(dataPath.path, dataPath.data.root, castData(dataPath.data.data)));
					} else {
						paths.add(new PathResult<>(real, null, null));
					}
				}
			} else if (quick instanceof ExtraDataPath) {
				ExtraDataPath dataPath = (ExtraDataPath) quick;
				paths.add(new PathResult<>(dataPath.path, dataPath.data.root, castData(dataPath.data.data)));
			} else {
				paths.add(new PathResult<>(quick, null, null));
			}
		}

		getResourcesIn(rootsArray, path, paths);
		return Collections.unmodifiableList(paths);
	}

	private void getResourcesIn(PathData[] src, String path, List<PathResult<D>> dst) {
		for (PathData root : src) {
			Path ext = root.path.resolve(path);
			if (FasterFiles.exists(ext)) {
				if (root.data == null) {
					dst.add(new PathResult<>(ext, null, null));
				} else {
					dst.add(new PathResult<>(ext, root.path, castData(root.data)));
				}
			}
		}
	}

	private static boolean isEqualPath(Path in, Path value) {
		if (in instanceof OverlappingPath) {
			in = ((OverlappingPath) in).paths[0];
		}
		if (in instanceof ExtraDataPath) {
			in = ((ExtraDataPath) in).path;
		}
		if (value instanceof OverlappingPath) {
			value = ((OverlappingPath) value).paths[0];
		}
		if (value instanceof ExtraDataPath) {
			value = ((ExtraDataPath) value).path;
		}
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

		if (value instanceof ExtraDataPath) {
			value = ((ExtraDataPath) value).path;
		}

		boolean should = VALIDATE && key.equals(value.toString());

		int offset = key.length();
		int names = value.getNameCount();
		for (int part = names - 1; part >= 0; part--) {
			String sub = value.getName(part).toString();
			offset -= sub.length();
			if (!key.startsWith(sub, offset)) {
				if (VALIDATE && should) {
					throw new IllegalStateException("Optimized equality test seems to be broken for " + key + " " + describePath(value));
				}
				return false;
			}
			offset--;
			if (offset < 0) {
				if (VALIDATE && should) {
					throw new IllegalStateException("Optimized equality test seems to be broken for " + key + " " + describePath(value));
				}
				return false;
			}
			if (key.charAt(offset) != '/') {
				if (VALIDATE && should) {
					throw new IllegalStateException("Optimized equality test seems to be broken for " + key + " " + describePath(value));
				}
				return false;
			}
		}

		if (offset != 0) {
			if (VALIDATE && should) {
				throw new IllegalStateException("Optimized equality test seems to be broken for " + key + " " + describePath(value));
			}
			return false;
		}

		if (VALIDATE && !should) {
			throw new IllegalStateException("Optimized equality test seems to be broken for " + key + " " + describePath(value));
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
				Path compare = result;
				if (result instanceof OverlappingPath) {
					compare = ((OverlappingPath) result).paths[0];
				}
				if (compare != null && !isEqual(key, compare)) {
					return null;
				}
			}

			return result;
		}

		abstract Path get0(String key);

		abstract void ensureCapacityFor(int newPathCount);

		abstract void put(Path newPath, ExtraData extraData);

		protected static Path computeNewPath(Path current, Path file, ExtraData extraData) {
			if (current == null) {
				return ExtraDataPath.wrap(file, extraData);
			} else if (current instanceof HashCollisionPath) {
				HashCollisionPath collision = (HashCollisionPath) current;
				int equalIndex = collision.getEqualPathIndex(file);
				if (equalIndex < 0) {
					Path[] newArray = new Path[collision.values.length + 1];
					System.arraycopy(collision.values, 0, newArray, 0, collision.values.length);
					newArray[collision.values.length] = ExtraDataPath.wrap(file, extraData);
					collision.values = newArray;
				} else {
					Path equal = collision.values[equalIndex];
					if (equal instanceof OverlappingPath) {
						OverlappingPath multi = (OverlappingPath) equal;
						multi.addPath(file, extraData);
						multi.data &= ~OverlappingPath.FLAG_HAS_WARNED;
					} else {
						OverlappingPath multi = new OverlappingPath();
						multi.paths = new Path[] { equal, ExtraDataPath.wrap(file, extraData) };
						collision.values[equalIndex] = multi;
					}
				}
				return collision;
			} else if (current instanceof OverlappingPath) {
				if (isEqualPath(file, ((OverlappingPath) current).paths[0])) {
					OverlappingPath multi = (OverlappingPath) current;
					multi.addPath(file, extraData);
					multi.data &= ~OverlappingPath.FLAG_HAS_WARNED;
					return multi;
				} else {
					return new HashCollisionPath(current, ExtraDataPath.wrap(file, extraData));
				}
			} else {
				if (isEqualPath(file, current)) {
					OverlappingPath multi = new OverlappingPath();
					multi.paths = new Path[] { current, ExtraDataPath.wrap(file, extraData) };
					return multi;
				} else {
					return new HashCollisionPath(current, ExtraDataPath.wrap(file, extraData));
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
		void put(Path path, ExtraData extraData) {
			files.compute(path.toString().hashCode(), (a, current) -> computeNewPath(current, path, extraData));
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
			int hash = key.hashCode();
			int index = hash & tbl.length - 1;

			if (printFullDetail) {
				Log.warn(LogCategory.GENERAL, "- hash(" + key + ") = " + hash + "; index = " + index + " in Path[" + tbl.length + "]");
			}

			return tbl[index];
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
		synchronized void put(Path newPath, ExtraData extraData) {
			entryCount++;
			if (table.length * FILL_PERCENT < entryCount) {
				rehash(table.length * 2);
			}
			int index = hashCode(newPath) & table.length - 1;
			table[index] = computeNewPath(table[index], newPath, extraData);
		}

		private static int hashCode(Path path) {
			if (path instanceof QuiltBasePath) {
				return ((QuiltBasePath<?, ?>) path).toStringHashCode();
			}
			return path.toString().hashCode();
		}

		private void rehash(int newSize) {
			Path[] oldTable = table;
			Path[] newTable = new Path[newSize];
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

					Path hashPath;
					if (sub2 instanceof OverlappingPath) {
						hashPath = ((OverlappingPath) sub2).paths[0];
					} else {
						hashPath = sub2;
					}

					final ExtraData extraData;
					final Path toAdd;
					if (hashPath instanceof ExtraDataPath) {
						ExtraDataPath dataPath = (ExtraDataPath) hashPath;
						if (hashPath == sub2) {
							// Originally an ExtraDataPath, so re-wrap it
							toAdd = dataPath.path;
							extraData = dataPath.data;
						} else {
							// Originally an OverlappingPath, so only modify the hash path
							// and put the original OverlappingPath
							toAdd = sub2;
							extraData = null;
						}
						hashPath = dataPath.path;
					} else {
						extraData = null;
						toAdd = sub2;
					}

					int index = hashCode(hashPath) & newTable.length - 1;
					newTable[index] = computeNewPath(newTable[index], toAdd, extraData);
				}
			}
			table = newTable;
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

		private String describe() {
			String[] array = new String[values.length];
			for (int i = 0; i < array.length; i++) {
				array[i] = describePath(values[i]);
			}
			return "HashCollisionPath " + Arrays.toString(array);
		}

		@Override
		protected IllegalStateException illegal() {
			IllegalStateException ex = new IllegalStateException(
				"QuiltClassPath must NEVER return a HashCollisionPath - something has gone very wrong!"
			);
			ex.printStackTrace();
			throw ex;
		}

		/** @return The equal path index in {@link #values}, or a negative number if it's not present. */
		public int getEqualPathIndex(Path in) {
			for (int i = 0; i < values.length; i++) {
				if (isEqualPath(in, values[i])) {
					return i;
				}
			}
			return -1;
		}

		public Path get(String key) {
			for (Path value : values) {
				Path compare = value;
				if (value instanceof OverlappingPath) {
					compare = ((OverlappingPath) value).paths[0];
				}
				if (isEqual(key, compare)) {
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

		public void addPath(Path file, ExtraData extraData) {
			paths = Arrays.copyOf(paths, paths.length + 1);
			paths[paths.length - 1] = ExtraDataPath.wrap(file, extraData);
			file.getNameCount();
		}

		private String describe() {
			String[] array = new String[paths.length];
			for (int i = 0; i < array.length; i++) {
				array[i] = describePath(paths[i]);
			}
			return "OverlappingPath " + Integer.toHexString(data) + " " + Arrays.toString(array);
		}

		@Override
		protected IllegalStateException illegal() {
			IllegalStateException ex = new IllegalStateException(
				"QuiltClassPath must NEVER return an OverlappingPath - something has gone very wrong!"
			);
			ex.printStackTrace();
			throw ex;
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

	/** Optional extra information about how a path should be handled. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	private static final class ExtraData {

		final Path root;
		/** Corresponds to the "D" parameter of {@link QuiltClassPath} */
		final Object data;

		ExtraData(Path root, Object data) {
			this.root = root;
			this.data = data;
		}
	}

	/** Used to store {@link ExtraData} about a real Path.*/
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	private static final class ExtraDataPath extends NullPath {
		final Path path;
		final ExtraData data;

		ExtraDataPath(Path path, ExtraData data) {
			// Make sure the given path:
			// 1: Is not null
			// 2: Is not a NullPath
			path.getNameCount();
			this.path = path;
			this.data = data;
		}

		public static Path wrap(Path file, ExtraData extraData) {
			if (extraData != null) {
				return new ExtraDataPath(file, extraData);
			} else {
				return file;
			}
		}

		@Override
		protected IllegalStateException illegal() {
			IllegalStateException ex = new IllegalStateException(
				"QuiltClassPath must NEVER return an ExtraDataPath - something has gone very wrong!"
			);
			ex.printStackTrace();
			throw ex;
		}

		private String describe() {
			return "ExtraDataPath " + describePath(path);
		}
	}
}
