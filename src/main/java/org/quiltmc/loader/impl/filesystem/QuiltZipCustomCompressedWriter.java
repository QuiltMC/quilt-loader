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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.quiltmc.loader.api.ExtendedFiles;
import org.quiltmc.loader.api.filesystem.StandardZipDecompressor;
import org.quiltmc.loader.impl.filesystem.ZipSource.SharedByteChannels;
import org.quiltmc.loader.impl.util.ExposedByteArrayOutputStream;

/** Writer class that implements
 * {@link QuiltZipFileSystem#writeQuiltCompressedFileSystem(java.nio.file.Path, java.nio.file.Path)}. */
final class QuiltZipCustomCompressedWriter {

	enum FileVersion {
		V2("quiltmczipcmpv2"),
		/** V3 can store files directly without compression by setting the "compressed size" to -1. */
		V3("quiltmczipcmpv3"),
		/** V3, but also stores a list of referenced files. */
		V3_REF("quiltmczipexref"),
		PARTIAL(Arrays.copyOf("PARTIAL!PARTIAL!PARTIAL!".getBytes(StandardCharsets.UTF_8), V2.header.length));

		static final int HEADER_LENGTH = V2.header.length;

		final byte[] header;

		private FileVersion(String headerStr) {
			this.header = headerStr.getBytes(StandardCharsets.UTF_8);
		}

		private FileVersion(byte[] raw) {
			this.header = raw;
		}

		static {
			int len = V2.header.length;
			for (FileVersion ver : values()) {
				if (ver.header.length != len) {
					throw new Error("Header lengths should be identical!");
				}
			}
		}

		boolean matches(byte[] readHeader) {
			return Arrays.equals(header, readHeader);
		}
	}

	static final int NOT_REFERENCED_INDEX = 0xFFFF;

	private static final AtomicInteger WRITER_THREAD_INDEX = new AtomicInteger();
	private static final StopThreadsPath THREAD_STOPPER = new StopThreadsPath();

	final Path src, dst;
	final Map<Path, String> referenceFiles;
	final QuiltZipCompressionStatistics stats;
	final LinkedBlockingQueue<Path> sourceFiles = new LinkedBlockingQueue<>();
	final Map<Path, FileEntry> files = new ConcurrentHashMap<>();
	final AtomicInteger currentOffset = new AtomicInteger();

	volatile boolean interrupted;
	volatile boolean aborted = false;
	volatile Exception exception;

	QuiltZipCustomCompressedWriter(Path src, Map<Path, String> referenceFiles, Path dst, QuiltZipCompressionStatistics stats) {
		this.src = src;
		this.referenceFiles = referenceFiles;
		this.dst = dst;
		this.stats = stats;
	}

	/** @see QuiltZipFileSystem#writeQuiltCompressedFileSystem(Path, Path) */
	void write() throws IOException {
		try (FileChannel channel = FileChannel.open(dst, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			write0(channel);
		} finally {
			aborted = true;
		}
	}

	private void write0(FileChannel channel) throws IOException {

		// Steps:
		// 1: Find all folders and files
		// 2: Pass each file on to a queue of files to be processed
		// 3: On threads compress those files into a set of byte arrays
		// 4: Write the directory entry list using those compressed files
		// 5: Append the byte arrays to the output file directly, in the right order

		// Spin up the other threads now
		int mainIndex = WRITER_THREAD_INDEX.incrementAndGet();

		channel.write(ByteBuffer.wrap(FileVersion.PARTIAL.header));
		// 4 bytes: Directory pointer
		channel.write(ByteBuffer.allocate(4));
		currentOffset.set((int) channel.position());

		int threadCount = Runtime.getRuntime().availableProcessors();
		WriterThread[] threads = new WriterThread[threadCount];
		for (int i = 0; i < threadCount; i++) {
			threads[i] = new WriterThread(mainIndex, i, channel);
			threads[i].setUncaughtExceptionHandler((thread, ex) -> {
				System.err.println("Exception in thread " + thread.getName());
				ex.printStackTrace(System.err);
				ExecutionException ee = new ExecutionException(thread.getName(), ex);
				synchronized (QuiltZipCustomCompressedWriter.this) {
					if (exception == null) {
						exception = ee;
					} else {
						exception.addSuppressed(ee);
					}
				}
			});
			threads[i].setDaemon(true);
			threads[i].start();
		}

		final Deque<Directory> stack = new ArrayDeque<>();

		try {
			Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Directory sub = new Directory(dir.getFileName().toString());
					Directory parent = stack.peek();
					if (parent != null) {
						parent.childDirectories.add(sub);
					}
					stack.push(sub);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (exception != null) {
						// Recheck in case we should stop early
						return FileVisitResult.TERMINATE;
					}
					stack.peek().childFiles.add(file);
					sourceFiles.add(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Directory state = stack.pop();
					if (stack.isEmpty()) {
						stack.push(state);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			synchronized (this) {
				if (exception == null) {
					exception = e;
				} else {
					exception.addSuppressed(e);
				}
			}
		}

		// Inform every thread that there are no more files left
		for (int i = 0; i < threadCount; i++) {
			sourceFiles.add(THREAD_STOPPER);
		}

		if (stack.size() != 1) {
			// A bug in our code apparently
			synchronized (this) {
				IllegalStateException illegal = new IllegalStateException("Directory stack too large/small! " + stack);
				if (exception != null) {
					// ...or probably caused by this exception, so it's okay
					exception.addSuppressed(illegal);
				} else {
					exception = illegal;
				}
			}
		}

		// Wait for every thread to finish
		for (int i = 0; i < threadCount; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				// We need to stop now apparently
				Thread.currentThread().interrupt();
				interrupted = true;
				for (int j = i + 1; j < threadCount; j++) {
					threads[j].interrupt();
				}
				break;
			}
		}

		// Check state of every thread to propagate exceptions upwards
		synchronized (QuiltZipCustomCompressedWriter.this) {
			if (!sourceFiles.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				Iterator<Path> iterator = sourceFiles.iterator();
				int count = 0;
				while (iterator.hasNext()) {
					count++;
					Path next = iterator.next();
					if (count < 100) {
						if (sb.length() == 0) {
							sb.append(", ");
						}
						sb.append(next);
					}
				}
				if (count >= 100) {
					sb.append(", [" + (count - 100) + " more]");
				}
				IOException e = new IOException("Some source files haven't been processed!\n" + sb);
				if (exception == null) {
					// And we don't know why
					exception = e;
				} else {
					// And we might know why, so it's probably okay
					exception.addSuppressed(e);
				}
			}

			if (interrupted) {
				// Either us, or another thread, was interrupted.
				if (exception == null) {
					exception = new InterruptedIOException();
				} else {
					exception.addSuppressed(new InterruptedIOException());
				}
			}

			if (exception != null) {
				aborted = true;
				if (exception instanceof IllegalStateException) {
					throw (IllegalStateException) exception;
				} else if (exception instanceof IOException) {
					throw (IOException) exception;
				} else if (exception instanceof ExecutionException) {
					throw new RuntimeException("One of the writer threads crashed!", exception);
				} else {
					throw new IllegalStateException(
						"Unexpected 'Exception' type - this should only be set to IOException or IllegalStateException!",
						exception
					);
				}
			}
		}

		Set<Path> externalFiles = new HashSet<>();
		for (FileEntry entry : files.values()) {
			if (entry instanceof ReferencedFileEntry) {
				externalFiles.add(((ReferencedFileEntry) entry).referenced);
			}
		}
		Map<Path, Integer> referenceIndex = externalFiles.isEmpty() ? null : new HashMap<>();

		// Write the directory
		int directoryOffset = currentOffset.get();
		ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(baos);
		DataOutputStream dos = new DataOutputStream(gzip);
		if (referenceIndex != null) {
			List<Path> sorted = new ArrayList<>(externalFiles);
			sorted.sort(Comparator.comparing(referenceFiles::get));
			writeUnsignedShort(sorted.size(), dos);
			for (int index = 0; index < sorted.size(); index++) {
				Path file = sorted.get(index);
				String refname = referenceFiles.get(file);
				if (refname == null) {
					throw new IllegalStateException("Unknown referenced file '" + file + "'\nNot in " + referenceFiles);
				}
				byte[] strBytes = refname.getBytes(StandardCharsets.UTF_8);
				writeUnsignedShort(strBytes.length, dos);
				dos.write(strBytes);
				referenceIndex.put(file, index);
			}
		}
		writeDirectory(stack.pop(), files, referenceIndex, dos);
		gzip.finish();
		channel.write(baos.wrapIntoBuffer(), directoryOffset);

		// Write the directory offset
		baos = new ExposedByteArrayOutputStream();
		dos = new DataOutputStream(baos);
		dos.writeInt(directoryOffset);
		channel.write(baos.wrapIntoBuffer(), FileVersion.HEADER_LENGTH);
		channel.force(false);

		// and the finished header
		channel.write(ByteBuffer.wrap((referenceIndex == null ? FileVersion.V3 : FileVersion.V3_REF).header), 0);

		if (stats != null) {
			stats.finish(channel.size());
		}
	}

	private void writeUnsignedShort(int value, DataOutputStream to) throws IOException {
		if (value < 0 || value > 0xFFFF) {
			throw new IOException("Value out-of-range: " + value);
		}
		to.writeShort(value);
	}

	private void writeDirectory(Directory directory, Map<Path, FileEntry> fileMap, Map<Path, Integer> referenceIndex,
		DataOutputStream to) throws IOException {

		// Some directories might have thousands of files, but it's not common
		to.writeShort(directory.childFiles.size());
		for (Path file : directory.childFiles) {
			byte[] nameBytes = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);
			to.writeByte(nameBytes.length);
			to.write(nameBytes);
			FileEntry entry = fileMap.get(file);
			if (referenceIndex != null) {
				if (entry instanceof ReferencedFileEntry) {
					ReferencedFileEntry ref = (ReferencedFileEntry) entry;
					Integer index = referenceIndex.get(ref.referenced);
					if (index == null) {
						throw new IllegalStateException(
							"Missing index for referenced file " + ref.referenced + " in " + referenceIndex
						);
					}
					writeUnsignedShort(index, to);
				} else {
					writeUnsignedShort(NOT_REFERENCED_INDEX, to);
				}
			} else if (entry instanceof ReferencedFileEntry) {
				throw new IllegalStateException("Encountered a ReferencedFileEntry but there's no referenceIndex?");
			}
			to.writeInt(entry.offset);
			to.writeInt(entry.uncompressedLength);
			to.writeInt(entry.isCompressed ? entry.compressedLength : -1);
		}
		to.writeShort(directory.childDirectories.size());
		for (Directory sub : directory.childDirectories) {
			byte[] nameBytes = sub.folderName.getBytes(StandardCharsets.UTF_8);
			to.writeByte(nameBytes.length);
			to.write(nameBytes);
			writeDirectory(sub, fileMap, referenceIndex, to);
		}
	}

	static final class Directory {
		final String folderName;
		final List<Directory> childDirectories = new ArrayList<>();
		final List<Path> childFiles = new ArrayList<>();

		public Directory(String folderName) {
			this.folderName = folderName;
		}
	}

	private final class WriterThread extends Thread {

		final FileChannel channel;
		Deflater deflater;

		public WriterThread(int mainIndex, int subIndex, FileChannel channel) {
			super("QuiltZipWriter-" + mainIndex + "." + subIndex);
			this.channel = channel;
		}

		@Override
		public void run() {
			while (exception == null && !aborted) {
				final Path next;
				try {
					next = sourceFiles.take();
				} catch (InterruptedException e) {
					interrupted = true;
					break;
				}
				if (next == THREAD_STOPPER) {
					break;
				}

				try {
					if (checkAndWriteReference(next)) {
						continue;
					}

					if (deflater == null) {
						deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
					} else {
						deflater.reset();
					}

					int uncompressedLength;
					ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
					try (DeflaterOutputStream compressor = new DeflaterOutputStream(baos, deflater)) {
						uncompressedLength = (int) Files.copy(next, compressor);
					}
					int offset = currentOffset.getAndAdd(baos.size());
					int length = baos.size();
					channel.write(ByteBuffer.wrap(baos.getArray(), 0, length), offset);
					files.put(next, new FileEntry(offset, uncompressedLength, length, true));
					if (stats != null) {
						stats.onStoreInternal(next, uncompressedLength, length, true);
					}
				} catch (IOException e) {
					e = new IOException("Failed to copy " + next, e);
					synchronized (QuiltZipCustomCompressedWriter.this) {
						if (aborted) {
							// Don't try to append to an exception if it's already been thrown
							e.printStackTrace();
							break;
						}
						if (exception == null) {
							exception = e;
						} else {
							exception.addSuppressed(e);
						}
						break;
					}
				}
			}

			if (deflater != null) {
				deflater.end();
			}
		}

		private boolean checkAndWriteReference(Path path) throws IOException {

			Path target = path;
			while (ExtendedFiles.isMountedFile(target)) {
				Path next = ExtendedFiles.readMountTarget(target);
				if (next == null) {
					break;
				} else {
					target = next;
				}
			}

			// In theory we could expose this from the API
			// but we don't so hardcode this
			FileSystem fs = target.getFileSystem();
			if (fs instanceof QuiltMapFileSystem<?, ?>) {
				QuiltMapFileSystem<?, ?> qfs = (QuiltMapFileSystem<?, ?>) fs;
				QuiltUnifiedEntry entry = qfs.getEntry(target);
				if (entry instanceof QuiltZipFile) {
					QuiltZipFile zip = (QuiltZipFile) entry;

					int offset = (int) zip.offset;
					if (offset != zip.offset) {
						// TODO: Implement reading from offsets >2GB from start!
						return false;
					}

					if (zip.decompressor != null && zip.decompressor != StandardZipDecompressor.INSTANCE) {
						return false;
					}

					ZipSource source = zip.source;
					if (source instanceof SharedByteChannels) {
						SharedByteChannels channels = (SharedByteChannels) source;
						Path zipFile = channels.zipFrom;
						if (referenceFiles.containsKey(zipFile)) {
							files.put(
								path, new ReferencedFileEntry(
									offset, zip.uncompressedSize, zip.compressedSize, zip.decompressor != null, zipFile
								)
							);
							if (stats != null) {
								stats.onStoreReference(
									path, zipFile, zip.uncompressedSize, zip.compressedSize, zip.decompressor!=null
								);
							}
							return true;
						}
					}
				} else {
					return false;
				}
			}

			return false;
		}
	}

	static class FileEntry {
		final int offset;
		final int uncompressedLength, compressedLength;
		final boolean isCompressed;

		FileEntry(int offset, int uncompressedLength, int compressedLength, boolean isCompressed) {
			this.offset = offset;
			this.uncompressedLength = uncompressedLength;
			this.compressedLength = compressedLength;
			this.isCompressed = isCompressed;
		}
	}

	static final class ReferencedFileEntry extends FileEntry {
		final Path referenced;

		ReferencedFileEntry(int offset, int uncompressedLength, int compressedLength, boolean isCompressed,
			Path referenced) {
			super(offset, uncompressedLength, compressedLength, isCompressed);
			this.referenced = referenced;
		}
	}

	private static final class StopThreadsPath extends NullPath {
		@Override
		protected IllegalStateException illegal() {
			throw new IllegalStateException(
				"QuiltZipCustomCompressedWriter must NEVER permit StopThreadsPath to leak!"
			);
		}
	}
}
