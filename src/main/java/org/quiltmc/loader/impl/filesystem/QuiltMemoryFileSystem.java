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
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.CachedFileSystem;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.plugin.NonZipException;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public abstract class QuiltMemoryFileSystem extends QuiltBaseFileSystem<QuiltMemoryFileSystem, QuiltMemoryPath> implements CachedFileSystem {

	private static final Set<String> FILE_ATTRS = Collections.singleton("basic");

	enum OpenState {
		OPEN,
		/** Used by {@link ReadWrite#replaceWithReadOnly()} when moving from one filesystem to another - since we need
		 * to remove the old file system from the provider while still being able to read it. */
		MOVING,
		CLOSED;
	}

	final Map<QuiltMemoryPath, QuiltMemoryEntry> files;

	volatile OpenState openState = OpenState.OPEN;

	private QuiltMemoryFileSystem(String name, boolean uniquify, Map<QuiltMemoryPath, QuiltMemoryEntry> fileMap) {
		super(QuiltMemoryFileSystem.class, QuiltMemoryPath.class, name, uniquify);
		this.files = fileMap;
		QuiltMemoryFileSystemProvider.PROVIDER.register(this);
	}

	@Override
	@NotNull
	QuiltMemoryPath createPath(@Nullable QuiltMemoryPath parent, String name) {
		return new QuiltMemoryPath(this, parent, name);
	}

	@Override
	public QuiltMemoryFileSystemProvider provider() {
		return QuiltMemoryFileSystemProvider.instance();
	}

	@Override
	public synchronized void close() {
		if (openState == OpenState.OPEN) {
			openState = OpenState.CLOSED;
			QuiltMemoryFileSystemProvider.PROVIDER.closeFileSystem(this);
		}
	}

	synchronized void beginMove() {
		if (openState == OpenState.OPEN) {
			openState = OpenState.MOVING;
			QuiltMemoryFileSystemProvider.PROVIDER.closeFileSystem(this);
		}
	}

	synchronized void endMove() {
		if (openState == OpenState.MOVING) {
			openState = OpenState.CLOSED;
		}
	}

	@Override
	public boolean isOpen() {
		return openState != OpenState.CLOSED;
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return FILE_ATTRS;
	}

	// FasterFileSystem

	@Override
	public boolean isSymbolicLink(Path path) {
		// Not supported
		return false;
	}

	@Override
	public boolean isDirectory(Path path, LinkOption... options) {
		return files.get(path.toAbsolutePath().normalize()) instanceof QuiltMemoryFolder;
	}

	@Override
	public boolean isRegularFile(Path path, LinkOption[] options) {
		return files.get(path.toAbsolutePath().normalize()) instanceof QuiltMemoryFile;
	}

	@Override
	public boolean exists(Path path, LinkOption... options) {
		return files.containsKey(path.toAbsolutePath().normalize());
	}

	@Override
	public boolean notExists(Path path, LinkOption... options) {
		// Nothing can go wrong, so this is fine
		return !exists(path, options);
	}

	@Override
	public boolean isReadable(Path path) {
		return exists(path);
	}

	@Override
	public boolean isExecutable(Path path) {
		return exists(path);
	}

	@Override
	public Stream<Path> list(Path dir) throws IOException {
		return getChildren(dir).stream().map(p -> (Path) p);
	}

	@Override
	public Collection<? extends Path> getChildren(Path dir) throws IOException {
		QuiltMemoryEntry entry = files.get(dir.toAbsolutePath().normalize());
		if (entry instanceof QuiltMemoryFolder) {
			return ((QuiltMemoryFolder) entry).getChildren();
		} else {
			throw new NotDirectoryException(dir.toString());
		}
	}

	public BasicFileAttributes readAttributes(QuiltMemoryPath qmp) throws IOException {
		checkOpen();
		QuiltMemoryEntry entry = files.get(qmp.toAbsolutePath().normalize());

		if (entry != null) {
			return entry.createAttributes();
		} else {
			throw new NoSuchFileException(qmp.toString());
		}
	}

	public <V extends FileAttributeView> V getFileAttributeView(QuiltMemoryPath qmp, Class<V> type) {
		if (type == BasicFileAttributeView.class) {
			BasicFileAttributeView view = new BasicFileAttributeView() {
				@Override
				public String name() {
					return "basic";
				}

				@Override
				public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
					throws IOException {
					// Unsupported
					// Since we don't need to throw we won't
				}

				@Override
				public BasicFileAttributes readAttributes() throws IOException {
					return QuiltMemoryFileSystem.this.readAttributes(qmp);
				}
			};
			return (V) view;
		}

		return null;
	}

	static final class DirBuildState {

		final QuiltMemoryPath folder;
		final List<QuiltMemoryPath> children = new ArrayList<>();

		public DirBuildState(QuiltMemoryPath folder) {
			this.folder = folder;
		}
	}

	public static final class ReadOnly extends QuiltMemoryFileSystem implements ReadOnlyFileSystem {

		/** Used to store all file data content in a single byte array. Potentially provides performance gains as we
		 * don't allocate as many objects (plus it's all packed). Comes with the one-time cost of actually coping all
		 * the bytes out of their original arrays and into the bigger byte array. */
		// TODO: Test:
		// 1: how expensive the "packing" is
		// 2: how much space we gain or perf we gain?
		// Disabled by default after testing - it doesn't save much memory
		// and adds a HUGE memory requirement bump during launch.
		private static final boolean PACK_FILE_DATA = false;

		private static final int STAT_UNCOMPRESSED = 0;
		private static final int STAT_USED = 1;
		private static final int STAT_MEMORY = 2;

		private final int uncompressedSize, usedSize, memorySize;
		private QuiltMemoryFileStore.ReadOnly fileStore;
		private Iterable<FileStore> fileStoreItr;

		/** Only used if {@link #PACK_FILE_DATA} is true. */
		final byte[] packedByteArray;

		/** Creates a new read-only {@link FileSystem} that copies every file in the given directory.
		 *
		 * @param compress if true then all files will be stored in-memory compressed.
		 * @throws IOException if any of the files in the given path could not be read. */
		public ReadOnly(String name, boolean uniquify, Path from, boolean compress) throws IOException {
			super(name, uniquify, new HashMap<>());

			int[] stats = new int[3];
			stats[STAT_MEMORY] = 60;

			if (!FasterFiles.isDirectory(from)) {
				throw new IOException(from + " is not a directory!");
			}

			final Deque<DirBuildState> stack = new ArrayDeque<>();

			Files.walkFileTree(from, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = from.relativize(dir);

					if (stack.isEmpty()) {
						stack.push(new DirBuildState(root));
					} else {
						String pathName = relative.getFileName().toString();
						QuiltMemoryPath path = stack.peek().folder.resolve(pathName);
						stack.peek().children.add(path);
						stats[STAT_MEMORY] += pathName.length() + 28;
						stack.push(new DirBuildState(path));
					}

					return super.preVisitDirectory(dir, attrs);
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					DirBuildState state = stack.peek();
					String fileName = file.getFileName().toString();
					stats[STAT_MEMORY] += fileName.length() + 28;
					QuiltMemoryPath childPath = state.folder.resolve(fileName);
					state.children.add(childPath);
					QuiltMemoryFile.ReadOnly qmf = QuiltMemoryFile.ReadOnly.create(childPath, Files.readAllBytes(file), compress);
					putFileStats(stats, qmf);

					files.put(childPath, qmf);

					return super.visitFile(file, attrs);
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					DirBuildState state = stack.pop();
					QuiltMemoryPath[] children = state.children.toArray(new QuiltMemoryPath[0]);
					files.put(state.folder, new QuiltMemoryFolder.ReadOnly(state.folder, children));

					stats[STAT_MEMORY] += children.length * 4 + 12;

					return super.postVisitDirectory(dir, exc);
				}
			});

			if (!stack.isEmpty()) {
				throw new IllegalStateException("Stack is not empty!");
			}

			uncompressedSize = stats[STAT_UNCOMPRESSED];
			usedSize = stats[STAT_USED];
			memorySize = stats[STAT_MEMORY] + ((int) (files.size() * 24 / 0.75f));
			packedByteArray = pack();
			fileStore = new QuiltMemoryFileStore.ReadOnly(name, usedSize);
			fileStoreItr = Collections.singleton(fileStore);
		}

		private static void putFileStats(int[] stats, QuiltMemoryFile.ReadOnly qmf) {
			stats[STAT_UNCOMPRESSED] += qmf.uncompressedSize;
			stats[STAT_USED] += qmf.byteArray().length;
			stats[STAT_MEMORY] += qmf.byteArray().length + 16;
		}

		private byte[] pack() {
			if (PACK_FILE_DATA) {
				byte[] packed = new byte[usedSize];

				int pos = 0;

				Iterator<Map.Entry<QuiltMemoryPath, QuiltMemoryEntry>> iter = files.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry<QuiltMemoryPath, QuiltMemoryEntry> entry = iter.next();
					if (entry.getValue() instanceof QuiltMemoryFile.ReadOnly.Absolute) {
						QuiltMemoryFile.ReadOnly.Absolute abs = (QuiltMemoryFile.ReadOnly.Absolute) entry.getValue();
						int len = abs.bytesLength();
						System.arraycopy(abs.byteArray(), 0, packed, pos, len);
						entry.setValue(
							new QuiltMemoryFile.ReadOnly.Relative(
								abs.path, abs.isCompressed, abs.uncompressedSize, pos, len
							)
						);
						pos += len;
					}
				}
				return packed;
			} else {
				return null;
			}
		}

		/** Creates a new read-only file system that copies every entry of a {@link ZipInputStream} that starts with
		 * "zipPathPrefix". This is effectively the same as
		 * {@link QuiltZipFileSystem#QuiltZipFileSystem(String, Path, String)}, but doesn't open files from their
		 * original location.
		 * <p>
		 * Using this is likely to be faster than opening a zip via {@link FileSystems#newFileSystem(Path, ClassLoader)}
		 * and then copying it with {@link #ReadOnly(String, boolean, Path, boolean)}.
		 * 
		 * @param name The name for the new filesystem. This affects URLs that reference this file system.
		 * @param zipFrom The zip to read from.
		 * @param zipPathPrefix A prefix for all entries. This should be the empty string to get every entry.
		 * @param compress If true then entries will be compressed in-memory. Generally slow.
		 * @throws IOException if {@link ZipInputStream} threw an {@link IOException} while reading entries. */
		public ReadOnly(String name, ZipInputStream zipFrom, String zipPathPrefix, boolean compress) throws IOException {
			super(name, true, new HashMap<>());

			int[] stats = new int[3];
			stats[STAT_MEMORY] = 60;

			Map<QuiltMemoryPath, Set<QuiltMemoryPath>> folders = new HashMap<>();

			boolean anyEntries = false;
			ZipEntry entry;
			while ((entry = zipFrom.getNextEntry()) != null) {
				anyEntries = true;
				String entryName = entry.getName();

				if (!entryName.startsWith(zipPathPrefix)) {
					continue;
				}
				entryName = entryName.substring(zipPathPrefix.length());
				if (!entryName.startsWith("/")) {
					entryName = "/" + entryName;
				}

				QuiltMemoryPath path = getPath(entryName);
				if (entryName.endsWith("/")) {
					// Folder, but it might have already been automatically added by a file
					folders.computeIfAbsent(path, p -> new HashSet<>());
					putParentFolders(folders, path);
				} else {
					// File
					stats[STAT_MEMORY] += path.name.length() + 28;
					byte[] bytes = FileUtil.readAllBytes(zipFrom);
					QuiltMemoryFile.ReadOnly qmf = QuiltMemoryFile.ReadOnly.create(path, bytes, compress);
					putFileStats(stats, qmf);
					files.put(path, qmf);
					putParentFolders(folders, path);
				}
			}

			if (!anyEntries) {
				// Files that aren't zip files don't throw exceptions
				// Instead they just return null from "ZipInputStream.getNextEntry()"
				// TODO: Check for the zip header constants INSTEAD, since empty zip files also don't pass this
				throw new IOException("No zip entries found!");
			}

			for (Map.Entry<QuiltMemoryPath, Set<QuiltMemoryPath>> folder : folders.entrySet()) {
				QuiltMemoryPath folderPath = folder.getKey();
				QuiltMemoryPath[] entries = folder.getValue().toArray(new QuiltMemoryPath[0]);
				stats[STAT_MEMORY] += folderPath.name.length() + entries.length * 4 + 12;
				files.put(folderPath, new QuiltMemoryFolder.ReadOnly(folderPath, entries));
			}

			uncompressedSize = stats[STAT_UNCOMPRESSED];
			usedSize = stats[STAT_USED];
			memorySize = stats[STAT_MEMORY] + ((int) (files.size() * 24 / 0.75f));
			packedByteArray = pack();

			fileStore = new QuiltMemoryFileStore.ReadOnly(name, usedSize);
			fileStoreItr = Collections.singleton(fileStore);
		}

		private static void putParentFolders(Map<QuiltMemoryPath, Set<QuiltMemoryPath>> folders, QuiltMemoryPath path) {
			if (path == null) {
				return;
			}
			QuiltMemoryPath parent = path;
			QuiltMemoryPath child = path;
			while ((parent = parent.getParent()) != null) {
				Set<QuiltMemoryPath> children = folders.computeIfAbsent(parent, p -> new HashSet<>());
				if (!children.add(child)) {
					break;
				}
				child = parent;
			}
		}

		@Override
		public boolean isReadOnly() {
			return true;
		}

		@Override
		public boolean isPermanentlyReadOnly() {
			return false;
		}

		/** @return The uncompressed size of all files stored in this file system. Since we store file data compressed
		 *         this doesn't reflect actual byte usage. */
		public int getUncompressedSize() {
			return uncompressedSize;
		}

		/** @return The raw number of bytes we store in byte arrays. */
		public int getUsedSize() {
			return usedSize;
		}

		/** @return An estimate of the memory footprint required in this JVM for this file system. Always bigger than
		 *         {@link #getUsedSize()}. */
		public int getEstimatedMemoryFootprint() {
			return memorySize;
		}

		@Override
		public Iterable<FileStore> getFileStores() {
			return fileStoreItr;
		}

		public QuiltMemoryFileSystem.ReadWrite copyToWriteable(String newName) {
			QuiltMemoryFileSystem.ReadWrite fs = new QuiltMemoryFileSystem.ReadWrite(newName, true);
			copyPath(root, fs.root);
			return fs;
		}

		/** Replaces this filesystem with a writable version, at least from the perspective of URL handling. This
		 * {@link FileSystem} will be closed, although existing usages of this filesystem's {@link Path}s won't be
		 * modified. (In other words, this should only be called by the owner of this filesystem). */
		public QuiltMemoryFileSystem.ReadWrite replaceWithWritable() {
			close();
			QuiltMemoryFileSystem.ReadWrite fs = new QuiltMemoryFileSystem.ReadWrite(name, false);
			copyPath(root, fs.root);
			return fs;
		}

		private void copyPath(QuiltMemoryPath src, QuiltMemoryPath dst) {
			QuiltMemoryEntry entrySrc = src.fs.files.get(src);
			if (entrySrc instanceof QuiltMemoryFile) {
				QuiltMemoryFile.ReadOnly fileSrc = (QuiltMemoryFile.ReadOnly) entrySrc;
				QuiltMemoryFile.ReadWrite fileDst = new QuiltMemoryFile.ReadWrite(dst);
				fileDst.copyFrom(fileSrc);
				dst.fs.files.put(dst, fileDst);
			} else {
				QuiltMemoryFolder.ReadOnly folderSrc = (QuiltMemoryFolder.ReadOnly) entrySrc;
				QuiltMemoryFolder.ReadWrite folderDst = new QuiltMemoryFolder.ReadWrite(dst);
				dst.fs.files.put(dst, folderDst);

				for (QuiltMemoryPath pathSrc : folderSrc.children) {
					QuiltMemoryPath pathDst = dst.resolve(pathSrc.name);
					folderDst.children.add(pathDst);
					copyPath(pathSrc, pathDst);
				}
			}
		}
	}

	public static final class ReadWrite extends QuiltMemoryFileSystem {

		private QuiltMemoryFileStore.ReadWrite fileStore;
		private Iterable<FileStore> fileStoreItr;

		public ReadWrite(String name, boolean uniquify) {
			super(name, uniquify, new ConcurrentHashMap<>());
			fileStore = new QuiltMemoryFileStore.ReadWrite(name, this);
			fileStoreItr = Collections.singleton(fileStore);
			files.put(root, new QuiltMemoryFolder.ReadWrite(root));
		}

		public ReadWrite(String name, boolean uniquify, Path from) throws IOException {
			this(name, uniquify);

			if (!FasterFiles.isDirectory(from)) {
				throw new IOException(from + " is not a directory!");
			}

			final Deque<DirBuildState> stack = new ArrayDeque<>();

			Files.walkFileTree(from, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path relative = from.relativize(dir);

					if (stack.isEmpty()) {
						stack.push(new DirBuildState(root));
					} else {
						String pathName = relative.getFileName().toString();
						stack.push(new DirBuildState(stack.peek().folder.resolve(pathName)));
					}

					return super.preVisitDirectory(dir, attrs);
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					DirBuildState state = stack.peek();
					String fileName = file.getFileName().toString();
					QuiltMemoryPath childPath = state.folder.resolve(fileName);
					state.children.add(childPath);
					QuiltMemoryFile.ReadWrite qmf = new QuiltMemoryFile.ReadWrite(childPath);
					qmf.createOutputStream(false).write(Files.readAllBytes(file));
					files.put(childPath, qmf);
					return super.visitFile(file, attrs);
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					DirBuildState state = stack.pop();
					QuiltMemoryFolder.ReadWrite qmf = new QuiltMemoryFolder.ReadWrite(state.folder);
					files.put(state.folder, qmf);
					qmf.children.addAll(state.children);
					return super.postVisitDirectory(dir, exc);
				}
			});

			if (!stack.isEmpty()) {
				throw new IllegalStateException("Stack is not empty!");
			}
		}

		/** Creates a new read-only version of this file system, copying the entire directory structure and file content
		 * into it. This also compresses the files if the compress argument is true (and if compression reduces the file
		 * size), and trims every byte array used to store files down to the minimum required length. */
		public QuiltMemoryFileSystem.ReadOnly optimizeToReadOnly(String newName, boolean compress) {
			try {
				return new ReadOnly(newName, true, root, compress);
			} catch (IOException e) {
				throw new RuntimeException(
					"For some reason the in-memory file system threw an IOException while reading!", e
				);
			}
		}

		/** Creates a new read-only version of this file system, copying the entire directory structure and file content
		 * into it. This also compresses the files if the compress argument is true (and if compression reduces the file
		 * size), and trims every byte array used to store files down to the minimum required length. */
		public QuiltMemoryFileSystem.ReadOnly replaceWithReadOnly(boolean compress) {
			beginMove();
			try {
				return new ReadOnly(name, false, root, compress);
			} catch (IOException e) {
				throw new RuntimeException(
					"For some reason the in-memory file system threw an IOException while reading!", e
				);
			} finally {
				endMove();
			}
		}

		@Override
		public Iterable<FileStore> getFileStores() {
			return fileStoreItr;
		}

		@Override
		public boolean isReadOnly() {
			return false;
		}

		@Override
		public boolean isPermanentlyReadOnly() {
			return false;
		}

		// FasterFileSystem

		@Override
		public Path createFile(Path path, FileAttribute<?>... attrs) throws IOException {
			// This is already fairly quick, so don't bother reimplementing it
			return super.createFile(path, attrs);
		}

		@Override
		public Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException {
			dir = dir.toAbsolutePath().normalize();
			Deque<Path> stack = new ArrayDeque<>();
			Path p = dir;
			do {
				if (isDirectory(p)) {
					break;
				} else {
					stack.push(p);
				}
			} while ((p = p.getParent()) != null);

			while (!stack.isEmpty()) {
				provider().createDirectory(stack.pop(), attrs);
			}

			return dir;
		}

		@Override
		public boolean isWritable(Path path) {
			return exists(path);
		}
	}
}
