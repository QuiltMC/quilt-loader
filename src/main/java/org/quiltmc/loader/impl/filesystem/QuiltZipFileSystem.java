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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.CachedFileSystem;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.LimitedInputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderCleanupTasks;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A read-only file system that only caches the locations of zip entries rather than their zip contents. This is
 * slightly more flexible than java's zip file system since it can have a different "root" than the real root of a zip
 * (useful for the transform cache). This also exists because (in java 8) the ZipFileSystem has a lot of bugs.
 * <p>
 * WARNING: Every new {@link InputStream} and {@link SeekableByteChannel} returned by this file system relies on the
 * input path's {@link SeekableByteChannel#position(long)} method to skip to the correct location. As such you should
 * only use this if the backing path supports efficient random access (generally {@link QuiltMemoryFileSystem} supports
 * this if it's not read-only, or the "compress" constructor argument is false). */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltZipFileSystem extends QuiltBaseFileSystem<QuiltZipFileSystem, QuiltZipPath>
	implements CachedFileSystem {

	final Map<QuiltZipPath, QuiltZipEntry> entries = new HashMap<>();
	final SharedByteChannels channels;

	public QuiltZipFileSystem(String name, Path zipFrom, String zipPathPrefix) throws IOException {
		super(QuiltZipFileSystem.class, QuiltZipPath.class, name, true);

		channels = new SharedByteChannels(this, zipFrom);

		// Check for our header
		byte[] header = new byte[QuiltZipCustomCompressedWriter.HEADER.length];
		try (InputStream fileStream = Files.newInputStream(zipFrom, StandardOpenOption.READ)) {
			BufferedInputStream pushback = new BufferedInputStream(fileStream);
			pushback.mark(header.length);
			int readLength = pushback.read(header);
			if (readLength == header.length && Arrays.equals(header, QuiltZipCustomCompressedWriter.HEADER)) {
				int start = new DataInputStream(pushback).readInt();
				readDirectory(root, start, new DataInputStream(new GZIPInputStream(pushback)), zipPathPrefix);
			} else if (readLength == header.length && Arrays.equals(header, QuiltZipCustomCompressedWriter.PARTIAL_HEADER)) {
				throw new PartiallyWrittenIOException();
			} else {
				pushback.reset();
				initializeFromZip(pushback, zipPathPrefix);
			}
		}

		QuiltZipFileSystemProvider.PROVIDER.register(this);
	}

	private void initializeFromZip(InputStream fileStream, String zipPathPrefix) throws IOException {
		try (CountingInputStream counter = new CountingInputStream(fileStream); //
			CustomZipInputStream zip = new CustomZipInputStream(counter)//
		) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String entryName = entry.getName();

				if (!entryName.startsWith(zipPathPrefix)) {
					continue;
				}
				entryName = entryName.substring(zipPathPrefix.length());
				if (!entryName.startsWith("/")) {
					entryName = "/" + entryName;
				}

				QuiltZipPath path = getPath(entryName);

				if (entryName.endsWith("/")) {
					// Folder, but it might already have been automatically added by a file
					entries.putIfAbsent(path, new QuiltZipFolder());
				} else {
					putFile(path, new QuiltZipFile(channels, entry, zip), IOException::new);
				}
			}
		}
	}

	private void readDirectory(QuiltZipPath path, int start, DataInputStream stream, String zipPathPrefix) throws IOException {
		String pathString = path.toString();
		if (pathString.startsWith(zipPathPrefix) || zipPathPrefix.startsWith(pathString)) {
			entries.computeIfAbsent(path, p -> new QuiltZipFolder());
		}
		int childFiles = stream.readUnsignedShort();
		for (int i = 0; i < childFiles; i++) {
			int length = stream.readUnsignedByte();
			byte[] nameBytes = new byte[length];
			stream.readFully(nameBytes);
			QuiltZipPath filePath = path.resolve(new String(nameBytes, StandardCharsets.UTF_8));
			int offset = start + stream.readInt();
			int uncompressedSize = stream.readInt();
			int compressedSize = stream.readInt();
			if (filePath.toString().startsWith(zipPathPrefix)) {
				QuiltZipFile file = new QuiltZipFile(filePath.toString(), channels, offset, compressedSize, uncompressedSize, true);
				putFile(filePath, file, IOException::new);
			}
		}

		int childFolders = stream.readUnsignedShort();
		for (int i = 0; i < childFolders; i++) {
			int length = stream.readUnsignedByte();
			byte[] nameBytes = new byte[length];
			stream.readFully(nameBytes);
			String name = new String(nameBytes, StandardCharsets.UTF_8);
			readDirectory(path.resolve(name), start, stream, zipPathPrefix);
		}
	}

	/** Constructs a new {@link QuiltZipFileSystem} that only exposes a single sub-folder of a larger
	 * {@link QuiltZipFileSystem}. */
	public QuiltZipFileSystem(String name, QuiltZipPath newRoot) {
		super(QuiltZipFileSystem.class, QuiltZipPath.class, name, true);

		channels = newRoot.fs.channels;
		channels.open(this);

		addFolder(newRoot, getRoot());

		QuiltZipFileSystemProvider.PROVIDER.register(this);
	}

	private void addFolder(QuiltZipPath src, QuiltZipPath dst) {
		QuiltZipFileSystem srcFS = src.fs;
		QuiltZipEntry entryFrom = srcFS.entries.get(src);
		if (entryFrom instanceof QuiltZipFolder) {
			// QuiltZipFolder does store subfolders that are part of the original FS, so we need to fully copy it
			entries.put(dst, new QuiltZipFolder());
			for (Map.Entry<String, QuiltZipPath> child : ((QuiltZipFolder) entryFrom).children.entrySet()) {
				addFolder(child.getValue(), dst.resolve(child.getKey()));
			}
		} else if (entryFrom instanceof QuiltZipFile) {
			// QuiltZipFile doesn't store anything that directly relates it to the source file system, so we can just
			// reuse the object
			putFile(dst, (QuiltZipFile) entryFrom, IllegalStateException::new);
		} else {
			// This isn't meant to happen, it means something got constructed badly
		}
	}

	private <T extends Throwable> void putFile(QuiltZipPath path, QuiltZipFile file, Function<String, T> exCtor)
		throws T {
		entries.put(path, file);
		QuiltZipPath parent = path;
		QuiltZipPath previous = path;
		while ((parent = parent.getParent()) != null) {
			QuiltZipEntry newEntry = entries.computeIfAbsent(parent, f -> new QuiltZipFolder());
			if (!(newEntry instanceof QuiltZipFolder)) {
				throw exCtor.apply("Cannot make a file into a folder " + parent + " for " + path);
			}
			((QuiltZipFolder) newEntry).children.put(previous.name, previous);
			previous = parent;
		}
	}

	/** Writes a "Quilt compressed file system" to the given destination, which can be read by
	 * {@link #QuiltZipFileSystem(String, Path, String)} - likely more quickly than a regular zip file. The source must
	 * be a folder. The output file will be of similar size to a regular zip of the same contents.
	 * 
	 * @param src The source folder to copy from.
	 * @param dst The destination file to copy to. This must not already exist.
	 * @throws IOException if anything goes wrong while writing the file or reading the source files. */
	public static void writeQuiltCompressedFileSystem(Path src, Path dst) throws IOException {
		new QuiltZipCustomCompressedWriter(src, dst).write();
	}

	@Override
	QuiltZipPath createPath(@Nullable QuiltZipPath parent, String name) {
		return new QuiltZipPath(this, parent, name);
	}

	@Override
	public FileSystemProvider provider() {
		return QuiltZipFileSystemProvider.instance();
	}

	@Override
	public void close() throws IOException {
		// We don't really open anything
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public boolean isPermanentlyReadOnly() {
		return true;
	}

	@Override
	public boolean exists(Path path, LinkOption... options) {
		return entries.containsKey(path.toAbsolutePath().normalize());
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		FileStore store = new FileStore() {
			@Override
			public String type() {
				return "QuiltZipFileSystem";
			}

			@Override
			public boolean supportsFileAttributeView(String name) {
				return "basic".equals(name);
			}

			@Override
			public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
				return type == BasicFileAttributeView.class;
			}

			@Override
			public String name() {
				return "QuiltZipFileSystem";
			}

			@Override
			public boolean isReadOnly() {
				return true;
			}

			@Override
			public long getUsableSpace() throws IOException {
				return 10;
			}

			@Override
			public long getUnallocatedSpace() throws IOException {
				return 0;
			}

			@Override
			public long getTotalSpace() throws IOException {
				return getUsableSpace();
			}

			@Override
			public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
				return null;
			}

			@Override
			public Object getAttribute(String attribute) throws IOException {
				return null;
			}
		};
		return Collections.singleton(store);
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return Collections.singleton("basic");
	}

	// Custom classes to grab the real offset while reading the zip

	static final class CountingInputStream extends InputStream {

		final InputStream stream;
		long offset;

		protected CountingInputStream(InputStream in) {
			this.stream = in;
		}

		@Override
		public int read() throws IOException {
			offset++;
			return stream.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = stream.read(b, off, len);
			if (read > 0) {
				offset += read;
			}
			return read;
		}

		@Override
		public long skip(long n) throws IOException {
			long skipped = stream.skip(n);
			if (skipped > 0) {
				offset += skipped;
			}
			return skipped;
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}
	}

	static final class CustomPushbackInputStream extends PushbackInputStream {
		public CustomPushbackInputStream(CountingInputStream in, int size) {
			super(in, size);
		}

		public long getOffset() {
			return ((CountingInputStream) in).offset - buf.length + pos;
		}
	}

	static final class CustomZipInputStream extends ZipInputStream {
		public CustomZipInputStream(CountingInputStream in) {
			super(in);
			this.in = new CustomPushbackInputStream(in, buf.length);
		}

		public long getOffset() {
			return ((CustomPushbackInputStream) in).getOffset();
		}
	}

	/** Used to cache {@link SeekableByteChannel} per-thread, since it's an expensive operation to open them. */
	static final class SharedByteChannels {
		final Path zipFrom;
		final Set<QuiltZipFileSystem> fileSystems = new HashSet<>();

		// This is not very nice: we want to use a ThreadLocal
		// but we can't since we need to close every channel afterwards
		final Map<Thread, SeekableByteChannel> channels;
		volatile boolean isOpen = true;

		SharedByteChannels(QuiltZipFileSystem fs, Path zipFrom) {
			this.zipFrom = zipFrom;
			channels = new ConcurrentHashMap<>();
			open(fs);
			QuiltLoaderCleanupTasks.addCleanupTask(this, this::removeDeadThreads);
		}

		synchronized void open(QuiltZipFileSystem fs) {
			fileSystems.add(fs);
		}

		synchronized void close(QuiltZipFileSystem fs) throws IOException {
			fileSystems.remove(fs);
			if (fileSystems.isEmpty()) {
				isOpen = false;
				for (SeekableByteChannel channel : channels.values()) {
					channel.close();
				}
				channels.clear();
				QuiltLoaderCleanupTasks.removeCleanupTask(this);
			}
		}

		SeekableByteChannel channel() throws IOException {
			try {
				return channels.computeIfAbsent(Thread.currentThread(), t -> {
					try {
						return Files.newByteChannel(zipFrom, StandardOpenOption.READ);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}

		private synchronized void removeDeadThreads() {
			Iterator<Map.Entry<Thread, SeekableByteChannel>> iterator = channels.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<Thread, SeekableByteChannel> next = iterator.next();
				Thread thread = next.getKey();
				if (!thread.isAlive()) {
					iterator.remove();
					try {
						next.getValue().close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			if (fileSystems.isEmpty()) {
				QuiltLoaderCleanupTasks.removeCleanupTask(this);
			}
		}
	}

	/** An {@link InputStream} which is based on a {@link SeekableByteChannel}, which allows the backing channel to be
	 * used by multiple streams in the same thread. */
	static final class ByteChannel2Stream extends InputStream {
		final SeekableByteChannel channel;
		long position;

		ByteChannel2Stream(SeekableByteChannel channel, long position) {
			this.channel = channel;
			this.position = position;
		}

		@Override
		public int read() throws IOException {
			byte[] value = new byte[1];
			int read = read(value, 0, 1);
			if (read == 1) {
				return Byte.toUnsignedInt(value[0]);
			} else {
				return -1;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			channel.position(position);
			int read = channel.read(ByteBuffer.wrap(b, off, len));
			position = channel.position();
			return read;
		}

		@Override
		public long skip(long n) throws IOException {
			position += n;
			return n;
		}
	}

	static abstract class QuiltZipEntry {
		protected abstract BasicFileAttributes createAttributes(QuiltZipPath path);
	}

	static final class QuiltZipFolder extends QuiltZipEntry {
		final Map<String, QuiltZipPath> children = new HashMap<>();

		@Override
		protected BasicFileAttributes createAttributes(QuiltZipPath path) {
			return new QuiltFileAttributes(path, QuiltFileAttributes.SIZE_DIRECTORY);
		}
	}

	static final class QuiltZipFile extends QuiltZipEntry {
		final SharedByteChannels channels;
		final long offset;
		final int compressedSize, uncompressedSize;
		final boolean isCompressed;

		QuiltZipFile(SharedByteChannels channels, ZipEntry entry, CustomZipInputStream zip) throws IOException {
			this.channels = channels;
			this.offset = zip.getOffset();
			int method = entry.getMethod();
			if (method == ZipEntry.DEFLATED) {
				isCompressed = true;
			} else if (method == ZipEntry.STORED) {
				isCompressed = false;
			} else {
				throw new IOException("Unsupported zip entry method " + method);
			}

			int compressed = (int) entry.getCompressedSize();
			int uncompressed = (int) entry.getSize();

			long time = 0;

			if (compressed < 0 || uncompressed < 0) {
				long start = System.nanoTime();
				int outputLength = 0;
				while (true) {
					int skipped = (int) zip.skip(1 << 16);
					if (skipped == 0) {
						break;
					}
					outputLength += skipped;
				}
				compressed = (int) (zip.getOffset() - offset);
				uncompressed = outputLength;
				time = System.nanoTime() - start;
			}

			this.compressedSize = compressed;
			this.uncompressedSize = uncompressed;

			if (Boolean.getBoolean("alexiil.temp.dump_zip_file_system_entries")) {
				StringBuilder sb = new StringBuilder();
				sb.append(entry.getName());
				while (sb.length() < 150) {
					sb.append(" ");
				}
				sb.append(uncompressed);
				while (sb.length() < 160) {
					sb.append(" ");
				}
				sb.append(time / 1000);
				while (sb.length() < 166) {
					sb.append(" ");
				}
				sb.append(" us");
				System.out.println(sb.toString());
			}

//			testReading(entry.toString());
		}

		QuiltZipFile(String path, SharedByteChannels channels, long offset, int compressedSize, int uncompressedSize,
			boolean isCompressed) {

			this.channels = channels;
			this.offset = offset;
			this.compressedSize = compressedSize;
			this.uncompressedSize = uncompressedSize;
			this.isCompressed = isCompressed;

//			testReading(path);
		}


		private void testReading(String path) {
			if (!path.endsWith(".json") && !path.endsWith(".txt")) {
				return;
			}
			System.out.println(path + " @ " + Integer.toHexString((int) offset));
			Error e2 = null;
			byte[] bytes = new byte[0];
			try (InputStream from = createInputStream()) {
				bytes = FileUtil.readAllBytes(from);
			} catch (IOException e) {
				e2 = new Error(e);
			}

			StringBuilder sb = new StringBuilder();

			for (int i = 0; true; i++) {
				int from = i * 20;
				int to = Math.min(from + 20, bytes.length);
				if (from >= to) break;
				if (i > 0) {
					System.out.println(sb.toString());
					sb.setLength(0);
				}
				for (int j = from; j < to; j++) {
					byte b = bytes[j];
					String asStr = Integer.toHexString(Byte.toUnsignedInt(b));
					if (asStr.length() < 2) {
						sb.append("0");
					}
					sb.append(asStr);
					sb.append(' ');
				}
				int leftOver = from - to + 20;
				for (int j = 0; j < leftOver; j++) {
					sb.append("   ");
				}

				sb.append("| ");
				for (int j = from; j < to; j++) {
					byte b = bytes[j];
					char c = (char) b;
					if (c < 32 || c > 127) {
						c = ' ';
					}
					sb.append(c);
				}
			}
			System.out.println(sb.toString());
			if (e2 != null) throw e2;
		}

		@Override
		protected BasicFileAttributes createAttributes(QuiltZipPath path) {
			return new QuiltFileAttributes(path, uncompressedSize);
		}

		InputStream createInputStream() throws IOException {
			InputStream stream = createUncompressingInputStream();
			if (isCompressed) {
				stream = new InflaterInputStream(stream, new Inflater(true));
			}
			return stream;
		}

		private InputStream createUncompressingInputStream() throws IOException, IOException {
			ByteChannel2Stream channel = new ByteChannel2Stream(channels.channel(), offset);
			return new LimitedInputStream(channel, compressedSize);
		}

		SeekableByteChannel createByteChannel() throws IOException {
			if (!isCompressed) {
				Path path = channels.zipFrom;
				return new OffsetSeekableByteChannel(Files.newByteChannel(path, StandardOpenOption.READ));
			} else {
				return new InflaterSeekableByteChannel();
			}
		}

		class OffsetSeekableByteChannel implements SeekableByteChannel {
			final SeekableByteChannel from;

			volatile long position = 0;

			OffsetSeekableByteChannel(SeekableByteChannel from) {
				this.from = from;
			}

			@Override
			public boolean isOpen() {
				return from.isOpen();
			}

			@Override
			public void close() throws IOException {
				from.close();
			}

			@Override
			public synchronized int read(ByteBuffer dst) throws IOException {
				if (position >= uncompressedSize) {
					return -1;
				}
				int toRead = (int) Math.min(uncompressedSize - position, dst.remaining());
				from.position(position + offset);
				int oldLimit = dst.limit();
				dst.limit(dst.position() + toRead);
				int read = from.read(dst);
				dst.limit(oldLimit);
				position += read;
				return read;
			}

			@Override
			public int write(ByteBuffer src) throws IOException {
				throw new IOException("read only");
			}

			@Override
			public synchronized long position() throws IOException {
				return position;
			}

			@Override
			public synchronized SeekableByteChannel position(long newPosition) throws IOException {
				if (newPosition < 0) {
					throw new IllegalArgumentException("position < 0");
				}
				this.position = newPosition;
				return this;
			}

			@Override
			public long size() throws IOException {
				return uncompressedSize;
			}

			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				if (size >= uncompressedSize) {
					return this;
				} else {
					throw new IOException("read only");
				}
			}
		}

		class InflaterSeekableByteChannel implements SeekableByteChannel {
			final InflaterInputStream infl;

			boolean open = true;
			volatile long position = 0;
			byte[] buffer = new byte[uncompressedSize];
			int bufferPosition = 0;

			public InflaterSeekableByteChannel() throws IOException {
				infl = new InflaterInputStream(createUncompressingInputStream(), new Inflater(true));
			}

			@Override
			public boolean isOpen() {
				return open;
			}

			@Override
			public void close() throws IOException {
				open = false;
				infl.close();
			}

			@Override
			public synchronized int read(ByteBuffer dst) throws IOException {
				if (position >= uncompressedSize) {
					return -1;
				}
				int toRead = (int) Math.min(uncompressedSize - position, dst.remaining());
				int pos = (int) position;

				int targetPos = toRead + pos;
				while (bufferPosition < targetPos) {
					int read = infl.read(buffer, bufferPosition, buffer.length - bufferPosition);
					if (read < 0) {
						throw new IOException("Unable to read enough bytes from the gzip stream!");
					} else {
						bufferPosition += read;
						position += read;
					}
				}

				dst.put(buffer, pos, toRead);
				return toRead;
			}

			@Override
			public int write(ByteBuffer src) throws IOException {
				throw new IOException("read only");
			}

			@Override
			public synchronized long position() throws IOException {
				return position;
			}

			@Override
			public synchronized SeekableByteChannel position(long newPosition) throws IOException {
				if (newPosition < 0) {
					throw new IllegalArgumentException("position < 0");
				}
				this.position = newPosition;
				return this;
			}

			@Override
			public long size() throws IOException {
				return uncompressedSize;
			}

			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				if (size >= uncompressedSize) {
					return this;
				} else {
					throw new IOException("read only");
				}
			}
		}
	}
}
