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

package org.quiltmc.loader.impl.filesystem;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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

	private final List<Path> roots = new CopyOnWriteArrayList<>();
	private final Map<String, Path> files = new ConcurrentHashMap<>();

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
				for (Path key : fs.files.keySet()) {
					putQuickFile(key.toString(), key);
				}
			}

		} else if (root instanceof QuiltZipPath) {
			QuiltZipFileSystem fs = ((QuiltZipPath) root).fs;

			for (Path key : fs.entries.keySet()) {
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

			Log.warn(LogCategory.GENERAL, "Adding unknown root to the classpath, this may slow down class loading: " + root.getFileSystem() + " " + root);
			roots.add(root);
		}
	}

	private void putQuickFile(String fileName, Path file) {
		files.compute(fileName, (name, current) -> {
			if (current == null) {
				return file;
			} else if (current instanceof OverlappingPath) {
				OverlappingPath multi = (OverlappingPath) current;
				multi.paths.add(file);
				multi.hasWarned = false;
				return multi;
			} else {
				OverlappingPath multi = new OverlappingPath(name);
				multi.paths.add(current);
				multi.paths.add(file);
				return multi;
			}
		});
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

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					stack.addLast(dir.getFileName().toString());
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
		if (quick != null) {
			if (quick instanceof OverlappingPath) {
				return ((OverlappingPath) quick).getFirst();
			}
			return quick;
		}

		for (Path root : roots) {
			Path ext = root.resolve(path);
			if (Files.exists(ext)) {
				return ext;
			}
		}

		return null;
	}

	/** Used when multiple paths are stored as values in {@link QuiltClassPath#files}. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	private static final class OverlappingPath extends NullPath {

		final String exposedName;
		final List<Path> paths = new ArrayList<>();
		boolean hasWarned = false;

		public OverlappingPath(String exposedName) {
			this.exposedName = exposedName;
		}

		@Override
		protected IllegalStateException illegal() {
			throw new IllegalStateException(
				"QuiltClassPath must NEVER return an OverlappingPath - something has gone very wrong!"
			);
		}

		public Path getFirst() {
			if (!hasWarned) {
				hasWarned = true;
				StringBuilder sb = new StringBuilder();
				sb.append("Multiple paths added for '");
				sb.append(exposedName);
				sb.append("', but only a single one can be returned!");
				for (Path path : paths) {
					sb.append("\n - ");
					sb.append(path.getFileSystem());
					sb.append(" ");
					sb.append(path);
				}
				Log.warn(LogCategory.GENERAL, sb.toString());
			}
			return paths.get(0);
		}

	}
}
