package org.quiltmc.loader.impl.memfilesys;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
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

public abstract class QuiltMemoryFileSystem extends FileSystem {

	private static final Set<String> FILE_ATTRS = Collections.singleton("basic");

	final String name;
	final QuiltMemoryPath root = new QuiltMemoryPath(this, null, "/");
	final Map<QuiltMemoryPath, QuiltMemoryEntry> files;

	private QuiltMemoryFileSystem(String name, Map<QuiltMemoryPath, QuiltMemoryEntry> fileMap) {
		this.name = name;
		this.files = fileMap;
	}

	public Path getRoot() {
		return root;
	}

	@Override
	public FileSystemProvider provider() {
		return QuiltMemoryFileSystemProvider.INSTANCE;
	}

	@Override
	public void close() throws IOException {
		// No.
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public String getSeparator() {
		return QuiltMemoryPath.NAME_ROOT;
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return Collections.singleton(root);
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return FILE_ATTRS;
	}

	@Override
	public Path getPath(String first, String... more) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	public BasicFileAttributes readAttributes(QuiltMemoryPath qmp) {
		QuiltMemoryEntry entry = files.get(qmp);

		if (entry != null) {
			return entry.createAttributes();
		} else {
			return QuiltFileAttributes.MISSING;
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
		public ReadOnly(String name, Path from) throws IOException {
			super(name, new HashMap<>());

			int[] stats = new int[3];
			stats[STAT_MEMORY] = 60;

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

		static final class DirBuildState {

			final QuiltMemoryPath folder;
			final List<QuiltMemoryPath> children = new ArrayList<>();

			public DirBuildState(QuiltMemoryPath folder) {
				this.folder = folder;
			}
		}

		@Override
		public boolean isReadOnly() {
			return true;
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
	}

	public static final class ReadWrite extends QuiltMemoryFileSystem {

		private QuiltMemoryFileStore.ReadWrite fileStore;
		private Iterable<FileStore> fileStoreItr;

		public ReadWrite(String name) {
			super(name, new ConcurrentHashMap<>());
			fileStore = new QuiltMemoryFileStore.ReadWrite(name, this);
			fileStoreItr = Collections.singleton(fileStore);
		}

		/** Creates a new read-only version of this file system, copying the entire directory structure and file content
		 * into it. This also compresses the files (if they can be compressed), and trims every byte array used to store
		 * files down to the minimum required length. */
		public QuiltMemoryFileSystem.ReadOnly optimizeToReadOnly() {
			try {
				return new ReadOnly(name, root);
			} catch (IOException e) {
				throw new RuntimeException(
					"For some reason the in-memory file system threw an IOException while reading!", e
				);
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
	}
}
