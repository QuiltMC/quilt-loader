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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.quiltmc.loader.impl.util.ExposedByteArrayOutputStream;

/** Writer class that implements
 * {@link QuiltZipFileSystem#writeQuiltCompressedFileSystem(java.nio.file.Path, java.nio.file.Path)}. */
final class QuiltZipCustomCompressedWriter {

	static final Charset UTF8 = StandardCharsets.UTF_8;
	static final byte[] HEADER = "quiltmczipcmpv2".getBytes(UTF8);
	static final byte[] PARTIAL_HEADER = Arrays.copyOf("PARTIAL!PARTIAL!PARTIAL!".getBytes(UTF8), HEADER.length);

	private static final AtomicInteger WRITER_THREAD_INDEX = new AtomicInteger();
	private static final StopThreadsPath THREAD_STOPPER = new StopThreadsPath();

	final Path src, dst;
	final LinkedBlockingQueue<Path> sourceFiles = new LinkedBlockingQueue<>();
	final Map<Path, FileEntry> files = new ConcurrentHashMap<>();
	final AtomicInteger currentOffset = new AtomicInteger();

	volatile boolean interrupted;
	volatile boolean aborted = false;
	volatile Exception exception;

	QuiltZipCustomCompressedWriter(Path src, Path dst) {
		this.src = src;
		this.dst = dst;
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

		channel.write(ByteBuffer.wrap(PARTIAL_HEADER));
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

		// Write the directory
		int directoryOffset = currentOffset.get();
		ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(baos);
		writeDirectory(stack.pop(), files, new DataOutputStream(gzip));
		gzip.finish();
		channel.write(baos.wrapIntoBuffer(), directoryOffset);

		// Write the directory offset
		baos = new ExposedByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeInt(directoryOffset);
		channel.write(baos.wrapIntoBuffer(), HEADER.length);
		channel.force(false);

		// and the finished header
		channel.write(ByteBuffer.wrap(HEADER), 0);
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

				if (deflater == null) {
					deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
				} else {
					deflater.reset();
				}

				try {
					int uncompressedLength;
					ExposedByteArrayOutputStream baos = new ExposedByteArrayOutputStream();
					try (DeflaterOutputStream compressor = new DeflaterOutputStream(baos, deflater)) {
						uncompressedLength = (int) Files.copy(next, compressor);
					}
					int offset = currentOffset.getAndAdd(baos.size());
					int length = baos.size();
					channel.write(ByteBuffer.wrap(baos.getArray(), 0, length), offset);
					files.put(next, new FileEntry(offset, uncompressedLength, length));
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
