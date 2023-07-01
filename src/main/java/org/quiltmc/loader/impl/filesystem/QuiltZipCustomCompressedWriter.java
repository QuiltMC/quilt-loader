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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.quiltmc.loader.impl.util.CountingOutputStream;

/** Writer class that implements
 * {@link QuiltZipFileSystem#writeQuiltCompressedFileSystem(java.nio.file.Path, java.nio.file.Path)}. */
final class QuiltZipCustomCompressedWriter {

	static final Charset UTF8 = StandardCharsets.UTF_8;
	static final byte[] HEADER = "quiltmczipcmpv1".getBytes(UTF8);
	static final byte[] PARTIAL_HEADER = Arrays.copyOf("PARTIAL!PARTIAL!PARTIAL!".getBytes(UTF8), HEADER.length);

	private static final AtomicInteger WRITER_THREAD_INDEX = new AtomicInteger();
	private static final StopThreadsPath THREAD_STOPPER = new StopThreadsPath();

	final Path src, dst;
	final LinkedBlockingQueue<Path> sourceFiles = new LinkedBlockingQueue<>();

	volatile boolean interrupted;
	volatile boolean aborted = false;
	volatile Exception exception;

	QuiltZipCustomCompressedWriter(Path src, Path dst) {
		this.src = src;
		this.dst = dst;
	}

	/** @see QuiltZipFileSystem#writeQuiltCompressedFileSystem(Path, Path) */
	void write() throws IOException {
		try {
			write0();
		} finally {
			aborted = true;
		}
	}

	private void write0() throws IOException {

		// Steps:
		// 1: Find all folders and files
		// 2: Pass each file on to a queue of files to be processed
		// 3: On threads compress those files into a set of byte arrays
		// 4: Write the directory entry list using those compressed files
		// 5: Append the byte arrays to the output file directly, in the right order

		// Spin up the other threads now
		int mainIndex = WRITER_THREAD_INDEX.incrementAndGet();

		int threadCount = Runtime.getRuntime().availableProcessors();
		WriterThread[] threads = new WriterThread[threadCount];
		for (int i = 0; i < threadCount; i++) {
			threads[i] = new WriterThread(mainIndex, i);
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
				IOException e = new IOException("Some source files haven't been processed!\n" + sourceFiles);
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
				} else {
					throw new IllegalStateException(
						"Unexpected 'Exception' type - this should only be set to IOException or IllegalStateException!",
						exception
					);
				}
			}
		}

		// Compute the real offsets
		Map<Path, FileEntry> realOffsets = new HashMap<>();
		// Real offset starts from the end of the directory list, which makes everything a lot simpler
		int offset = 0;
		for (WriterThread writer : threads) {
			if (offset == 0) {
				// Everything is already aligned
				realOffsets.putAll(writer.files);
			} else {
				for (Map.Entry<Path, FileEntry> entry : writer.files.entrySet()) {
					FileEntry from = entry.getValue();
					realOffsets.put(
						entry.getKey(), new FileEntry(
							offset + from.offset, from.uncompressedLength, from.compressedLength
						)
					);
				}
			}

			offset += writer.currentOffset();
		}

		final byte[] tmpHeader = PARTIAL_HEADER;

		int offsetStart;

		// Now to write the actual file
		OpenOption[] options = { StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE };
		try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(dst, options))) {
			CountingOutputStream counter = new CountingOutputStream(stream);
			counter.write(tmpHeader);
			counter.write(new byte[4]); // File offset start
			GZIPOutputStream gzip = new GZIPOutputStream(counter);
			writeDirectory(stack.pop(), realOffsets, new DataOutputStream(gzip));
			gzip.finish();
			offsetStart = counter.getBytesWritten();

			for (WriterThread thread : threads) {
				int arrayCount = thread.arrays.size();
				for (int i = 0; i < arrayCount; i++) {
					byte[] array = thread.arrays.get(i);
					int length = i == arrayCount - 1 ? thread.currentArrayIndex : array.length;
					stream.write(array, 0, length);
				}
			}
		}

		// And rewrite the header since we're complete
		try (OutputStream stream = Files.newOutputStream(dst, StandardOpenOption.WRITE)) {
			stream.write(HEADER);
			new DataOutputStream(stream).writeInt(offsetStart);
		}
	}

	private void writeDirectory(Directory directory, Map<Path, FileEntry> fileMap, DataOutputStream to)
		throws IOException {
		// Some directories might have thousands of files, but it's not common
		to.writeShort(directory.childFiles.size());
		for (Path file : directory.childFiles) {
			byte[] nameBytes = file.getFileName().toString().getBytes(UTF8);
			to.writeByte(nameBytes.length);
			to.write(nameBytes);
			FileEntry entry = fileMap.get(file);
			to.writeInt(entry.offset);
			to.writeInt(entry.uncompressedLength);
			to.writeInt(entry.compressedLength);
		}
		to.writeShort(directory.childDirectories.size());
		for (Directory sub : directory.childDirectories) {
			byte[] nameBytes = sub.folderName.getBytes(UTF8);
			to.writeByte(nameBytes.length);
			to.write(nameBytes);
			writeDirectory(sub, fileMap, to);
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

		private static final int ARRAY_LENGTH = 512 * 1024;

		/** List of 512k byte arrays. */
		private final List<byte[]> arrays = new ArrayList<>();

		/** Index in the current byte array, not the index of the current byte array (which is always the last
		 * array). */
		int currentArrayIndex = ARRAY_LENGTH;

		final ExpandingOutputStream outputStream = new ExpandingOutputStream();
		final Map<Path, FileEntry> files = new HashMap<>();

		Deflater deflater;

		public WriterThread(int mainIndex, int subIndex) {
			super("QuiltZipWriter-" + mainIndex + "." + subIndex);
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

				int offset = currentOffset();
				int uncompressedLength;
				if (deflater == null) {
					deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
				} else {
					deflater.reset();
				}
				try (DeflaterOutputStream compressor = new DeflaterOutputStream(outputStream, deflater)) {
					uncompressedLength = (int) Files.copy(next, compressor);
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
				int length = currentOffset() - offset;
				files.put(next, new FileEntry(offset, uncompressedLength, length));
			}

			if (deflater != null) {
				deflater.end();
			}
		}

		private int currentOffset() {
			return (arrays.size() - 1) * ARRAY_LENGTH + currentArrayIndex;
		}

		final class ExpandingOutputStream extends OutputStream {
			final byte[] singleArray = new byte[1];

			@Override
			public void write(int b) throws IOException {
				singleArray[0] = (byte) b;
				write(singleArray, 0, 1);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				while (len > 0) {
					if (currentArrayIndex == ARRAY_LENGTH) {
						arrays.add(new byte[ARRAY_LENGTH]);
						currentArrayIndex = 0;
					}
					byte[] to = arrays.get(arrays.size() - 1);
					int available = to.length - currentArrayIndex;
					int toCopy = Math.min(available, len);
					System.arraycopy(b, off, to, currentArrayIndex, toCopy);
					off += toCopy;
					currentArrayIndex += toCopy;
					len -= toCopy;
				}
			}
		}
	}

	static final class FileEntry {
		final int offset;
		final int uncompressedLength, compressedLength;

		FileEntry(int offset, int uncompressedLength, int compressedLength) {
			this.offset = offset;
			this.uncompressedLength = uncompressedLength;
			this.compressedLength = compressedLength;
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
