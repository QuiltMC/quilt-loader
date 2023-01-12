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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.CachedFileSystem;
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
		QuiltMemoryFileSystemProvider.register(this);
	}

	@Override
	@NotNull
	QuiltMemoryPath createPath(@Nullable QuiltMemoryPath parent, String name) {
		return new QuiltMemoryPath(this, parent, name);
	}

	@Override
	public FileSystemProvider provider() {
		return QuiltMemoryFileSystemProvider.instance();
	}

	@Override
	public synchronized void close() {
		if (openState == OpenState.OPEN) {
			openState = OpenState.CLOSED;
			QuiltMemoryFileSystemProvider.closeFileSystem(this);
		}
	}

	synchronized void beginMove() {
		if (openState == OpenState.OPEN) {
			openState = OpenState.MOVING;
			QuiltMemoryFileSystemProvider.closeFileSystem(this);
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

	public BasicFileAttributes readAttributes(QuiltMemoryPath qmp) throws IOException {
		checkOpen();
		QuiltMemoryEntry entry = files.get(qmp);

		if (entry != null) {
			return entry.createAttributes();
		} else {
			throw new NoSuchFileException(qmp.name);
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
				public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
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

	public static final class ReadOnly extends QuiltMemoryFileSystem {

		/** Used to store all file data content in a single byte array. Potentially provides performance gains as we
		 * don't allocate as many objects (plus it's all packed). Comes with the one-time cost of actually coping all
		 * the bytes out of their original arrays and into the bigger byte array. */
		// TODO: Test:
		// 1: how expensive the "packing" is
		// 2: how much space we gain or perf we gain?
		private static final boolean PACK_FILE_DATA = true;

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
		 * @throws IOException if any of the files in the given path could not be read. */
		public ReadOnly(String name, boolean uniquify, Path from) throws IOException {
			super(name, uniquify, new HashMap<>());

			int[] stats = new int[3];
			stats[STAT_MEMORY] = 60;

			if (!Files.isDirectory(from)) {
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
					QuiltMemoryFile.ReadOnly qmf = QuiltMemoryFile.ReadOnly.create(childPath, Files.readAllBytes(file));

					stats[STAT_UNCOMPRESSED] += qmf.uncompressedSize;
					stats[STAT_USED] += qmf.byteArray().length;
					stats[STAT_MEMORY] += qmf.byteArray().length + 16;

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

			if (PACK_FILE_DATA) {
				packedByteArray = new byte[stats[STAT_USED]];

				int pos = 0;

				Iterator<Map.Entry<QuiltMemoryPath, QuiltMemoryEntry>> iter = files.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry<QuiltMemoryPath, QuiltMemoryEntry> entry = iter.next();
					if (entry.getValue() instanceof QuiltMemoryFile.ReadOnly.Absolute) {
						QuiltMemoryFile.ReadOnly.Absolute abs = (QuiltMemoryFile.ReadOnly.Absolute) entry.getValue();
						int len = abs.bytesLength();
						System.arraycopy(abs.byteArray(), 0, packedByteArray, pos, len);
						entry.setValue(
								new QuiltMemoryFile.ReadOnly.Relative(
										abs.path, abs.isCompressed, abs.uncompressedSize, pos, len
								)
						);
						pos += len;
					}
				}

			} else {
				packedByteArray = null;
			}

			fileStore = new QuiltMemoryFileStore.ReadOnly(name, usedSize);
			fileStoreItr = Collections.singleton(fileStore);
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

			if (!Files.isDirectory(from)) {
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
		 * into it. This also compresses the files (if they can be compressed), and trims every byte array used to store
		 * files down to the minimum required length. */
		public QuiltMemoryFileSystem.ReadOnly optimizeToReadOnly(String newName) {
			try {
				return new ReadOnly(newName, true, root);
			} catch (IOException e) {
				throw new RuntimeException(
						"For some reason the in-memory file system threw an IOException while reading!", e
				);
			}
		}

		/** Creates a new read-only version of this file system, copying the entire directory structure and file content
		 * into it. This also compresses the files (if they can be compressed), and trims every byte array used to store
		 * files down to the minimum required length. */
		public QuiltMemoryFileSystem.ReadOnly replaceWithReadOnly() {
			beginMove();
			try {
				return new ReadOnly(name, false, root);
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
	}
}
