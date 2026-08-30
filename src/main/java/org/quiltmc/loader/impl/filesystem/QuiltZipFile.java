/*
 * Copyright 2025 QuiltMC
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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

import org.quiltmc.loader.impl.filesystem.QuiltUnifiedEntry.QuiltUnifiedFile;
import org.quiltmc.loader.impl.filesystem.QuiltZipFileSystem.CustomZipInputStream;
import org.quiltmc.loader.impl.util.FileUtil;
import org.quiltmc.loader.impl.util.LimitedInputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
class QuiltZipFile extends QuiltUnifiedFile {
	final ZipSource source;
	final long offset;
	final int compressedSize, uncompressedSize;
	final boolean isCompressed;

	QuiltZipFile(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry, CustomZipInputStream zip)
		throws IOException {
		super(path);
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
		} else {
			while (true) {
				int skipped = (int) zip.skip(1 << 16);
				if (skipped == 0) {
					break;
				}
			}
		}

		this.compressedSize = compressed;
		this.uncompressedSize = uncompressed;

		this.source = source.forIndividualFile(offset, compressedSize);

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

		if (QuiltZipFileSystem.DEBUG_TEST_READING) {
			testReading(entry.toString());
		}
	}

	QuiltZipFile(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize, int uncompressedSize,
		boolean isCompressed) {

		super(path);

		this.source = source;
		this.offset = offset;
		this.compressedSize = compressedSize;
		this.uncompressedSize = uncompressedSize;
		this.isCompressed = isCompressed;

		if (QuiltZipFileSystem.DEBUG_TEST_READING) {
			testReading(path.toString());
		}
	}

	private void testReading(String path) {
		if (!path.endsWith(".json") && !path.endsWith(".txt") && !"META-INF/MANIFEST.MF".equals(path)) {
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
	protected QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath) {
		return new QuiltZipFile(newPath, source, offset, compressedSize, uncompressedSize, isCompressed);
	}

	@Override
	protected BasicFileAttributes createAttributes() {
		return new QuiltFileAttributes(path, uncompressedSize);
	}

	@Override
	InputStream createInputStream() throws IOException {
		InputStream stream = createUncompressingInputStream();
		if (isCompressed) {
			stream = new InflaterInputStream(stream, new Inflater(true));
			// Make InputStream.available work
			// older versions of FerriteCore used this to allocate a byte array to read into
			// - newer versions are fixed, but we still want to keep backwards compatibility
			// Also make InputStream.read(byte[], int, int) read as much as possible
			// - minecraft 1.18.2 reads the font sizes by incorrectly assuming read(new byte[65536])
			// will always read exactly 65536 bytes, when that's not normally true
			stream = new LimitedInputStream(stream, uncompressedSize);
		}
		return stream;
	}

	private InputStream createUncompressingInputStream() throws IOException, IOException {
		return new LimitedInputStream(source.stream(offset, compressedSize), compressedSize);
	}

	@Override
	OutputStream createOutputStream(boolean append, boolean truncate) throws IOException {
		throw new IOException(ReadOnlyFileSystem.READ_ONLY_ERROR_MESSAGE);
	}

	@Override
	SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException {
		for (OpenOption option : options) {
			if (option != StandardOpenOption.READ) {
				throw new IOException(ReadOnlyFileSystem.READ_ONLY_ERROR_MESSAGE);
			}
		}

		return createByteChannel();
	}

	SeekableByteChannel createByteChannel() throws IOException {
		if (!isCompressed) {
			return source.channel(offset, uncompressedSize);
		} else {
			return new InflaterSeekableByteChannel();
		}
	}

	static class CopyOnWriteZipFile extends QuiltZipFile {

		CopyOnWriteZipFile(QuiltMapPath<?, ?> path, ZipSource source, long offset, int compressedSize,
			int uncompressedSize, boolean isCompressed) {

			super(path, source, offset, compressedSize, uncompressedSize, isCompressed);
		}

		CopyOnWriteZipFile(QuiltMapPath<?, ?> path, ZipSource source, ZipEntry entry, CustomZipInputStream zip)
			throws IOException {

			super(path, source, entry, zip);
		}

		@Override
		protected QuiltUnifiedEntry switchToReadOnly() {
			return super.createCopiedTo(path);
		}

		@Override
		protected QuiltUnifiedEntry createCopiedTo(QuiltMapPath<?, ?> newPath) {
			return new CopyOnWriteZipFile(newPath, source, offset, compressedSize, uncompressedSize, isCompressed);
		}

		private QuiltUnifiedFile deepCopy(boolean truncate) throws IOException {
			path.fs.provider().delete(path);
			QuiltMemoryFile.ReadWrite file = new QuiltMemoryFile.ReadWrite(path);
			if (!truncate) {
				try (OutputStream dst = file.createOutputStream(true, true)) {
					Files.copy(path, dst);
				}
			}
			path.fs.addEntryRequiringParent(file);
			return file;
		}

		@Override
		OutputStream createOutputStream(boolean append, boolean truncate) throws IOException {
			return deepCopy(truncate).createOutputStream(append, truncate);
		}

		@Override
		SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException {
			for (OpenOption option : options) {
				if (option != StandardOpenOption.READ) {
					return deepCopy(options.contains(StandardOpenOption.TRUNCATE_EXISTING)).createByteChannel(options);
				}
			}
			return super.createByteChannel(options);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
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
				}
			}
			position += toRead;

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
