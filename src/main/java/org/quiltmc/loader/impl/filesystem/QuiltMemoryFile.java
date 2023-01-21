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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.SystemProperties;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
abstract class QuiltMemoryFile extends QuiltMemoryEntry {

	private QuiltMemoryFile(QuiltMemoryPath path) {
		super(path);
	}

	abstract InputStream createInputStream() throws IOException;

	abstract OutputStream createOutputStream(boolean append) throws IOException;

	abstract SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException;

	static abstract class ReadOnly extends QuiltMemoryFile {

		final boolean isCompressed;
		final int uncompressedSize;

		ReadOnly(QuiltMemoryPath path, boolean compressed, int uncompressedSize) {
			super(path);
			this.isCompressed = compressed;
			this.uncompressedSize = uncompressedSize;
		}

		abstract byte[] byteArray();

		abstract int bytesOffset();

		abstract int bytesLength();

		static QuiltMemoryFile.ReadOnly create(QuiltMemoryPath path, byte[] bytes, boolean compress) {
			int size = bytes.length;

			if (size < 24 || !compress) {
				return new QuiltMemoryFile.ReadOnly.Absolute(path, false, size, bytes);
			}

			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				GZIPOutputStream gzip = new GZIPOutputStream(baos);
				gzip.write(bytes);
				gzip.close();

				byte[] c = baos.toByteArray();

				if (c.length + 24 < size) {
					return new QuiltMemoryFile.ReadOnly.Absolute(path, true, size, c);
				} else {
					return new QuiltMemoryFile.ReadOnly.Absolute(path, false, size, bytes);
				}
			} catch (IOException e) {
				return new QuiltMemoryFile.ReadOnly.Absolute(path, false, size, bytes);
			}
		}

		@Override
		protected BasicFileAttributes createAttributes() {
			return new QuiltFileAttributes(path, uncompressedSize);
		}

		@Override
		InputStream createInputStream() throws IOException {
			InputStream direct = new ByteArrayInputStream(byteArray(), bytesOffset(), bytesLength());
			if (!isCompressed) {
				return direct;
			}

			return new InputStream() {
				final InputStream from = new GZIPInputStream(direct);

				int countRead = 0;

				@Override
				public int available() throws IOException {
					return uncompressedSize - countRead;
				}

				@Override
				public int read() throws IOException {
					int read = from.read();
					countRead++;
					return read;
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int read = from.read(b, off, len);
					if (read > 0) {
						countRead += read;
					}
					return read;
				}

				@Override
				public void close() throws IOException {
					from.close();
				}
			};
		}

		private static IOException readOnly() throws IOException {
			throw new IOException(QuiltMemoryFileSystemProvider.READ_ONLY_EXCEPTION);
		}

		@Override
		OutputStream createOutputStream(boolean append) throws IOException {
			throw readOnly();
		}

		@Override
		SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) throws IOException {
			for (OpenOption o : options) {
				if (o != StandardOpenOption.READ) {
					throw new UnsupportedOperationException("'" + o + "' not allowed");
				}
			}

			if (isCompressed) {
				return createUncompressingByteChannel();
			} else {
				return new QuiltSeekableByteChannel();
			}
		}

		private SeekableByteChannel createUncompressingByteChannel() throws IOException {
			return new QuiltSeekableByteChannel() {
				boolean open = true;

				final GZIPInputStream gzip = new GZIPInputStream(
						new ByteArrayInputStream(byteArray(), bytesOffset(), bytesLength())
				);
				byte[] buffer = new byte[uncompressedSize];
				int bufferPosition = 0;

				@Override
				public boolean isOpen() {
					return open;
				}

				@Override
				public void close() throws IOException {
					open = false;
					gzip.close();
				}

				@Override
				public synchronized int read(ByteBuffer dst) throws IOException {
					if (position >= uncompressedSize) {
						return -1;
					}
					int toRead = (int) Math.min(uncompressedSize - position, dst.remaining());
					int offset = (int) position;

					int targetPos = toRead + offset;
					while (bufferPosition < targetPos) {
						int read = gzip.read(buffer, bufferPosition, buffer.length - bufferPosition);
						if (read < 0) {
							throw new IOException("Unable to read enough bytes from the gzip stream!");
						} else {
							bufferPosition += read;
							position += read;
						}
					}

					dst.put(buffer, offset, toRead);
					return toRead;
				}
			};
		}

		class QuiltSeekableByteChannel implements SeekableByteChannel {

			volatile long position = 0;

			@Override
			public boolean isOpen() {
				return true;
			}

			@Override
			public void close() throws IOException {
				// no.
			}

			@Override
			public int write(ByteBuffer src) throws IOException {
				throw readOnly();
			}

			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				if (size >= uncompressedSize) {
					return this;
				} else {
					throw readOnly();
				}
			}

			@Override
			public long size() throws IOException {
				return uncompressedSize;
			}

			@Override
			public synchronized int read(ByteBuffer dst) throws IOException {
				if (position >= uncompressedSize) {
					return -1;
				}
				int toRead = (int) Math.min(uncompressedSize - position, dst.remaining());
				int offset = (int) position;
				dst.put(byteArray(), offset + bytesOffset(), toRead);
				position += toRead;
				return toRead;
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
			public synchronized long position() throws IOException {
				return position;
			}
		}

		static final class Absolute extends ReadOnly {
			private final byte[] bytes;

			Absolute(QuiltMemoryPath path, boolean compressed, int uncompressedSize, byte[] bytes) {
				super(path, compressed, uncompressedSize);
				this.bytes = bytes;
			}

			@Override
			byte[] byteArray() {
				return bytes;
			}

			@Override
			int bytesOffset() {
				return 0;
			}

			@Override
			int bytesLength() {
				return bytes.length;
			}
		}

		static final class Relative extends ReadOnly {
			private final int byteOffset;
			private final int byteLength;

			Relative(QuiltMemoryPath path, boolean compressed, int uncompressedSize, int byteOffset, int byteLength) {
				super(path, compressed, uncompressedSize);
				this.byteOffset = byteOffset;
				this.byteLength = byteLength;
			}

			@Override
			byte[] byteArray() {
				return ((QuiltMemoryFileSystem.ReadOnly) path.fs).packedByteArray;
			}

			@Override
			int bytesOffset() {
				return byteOffset;
			}

			@Override
			int bytesLength() {
				return byteLength;
			}
		}
	}

	static final class ReadWrite extends QuiltMemoryFile {
		private static final int MAX_FILE_SIZE = 512 * 1024 * 1024;

		private byte[] bytes = null;
		private int length = 0;

		ReadWrite(QuiltMemoryPath path) {
			super(path);
		}

		private ReadWrite sync() {
			return this;
		}

		void copyFrom(QuiltMemoryFile src) {
			if (src instanceof ReadWrite) {
				copyFrom((ReadWrite) src);
			} else if (src instanceof ReadOnly) {
				copyFrom((ReadOnly) src);
			} else {
				throw new IllegalStateException("Unknown QuiltMemoryFile " + src.getClass());
			}
		}

		void copyFrom(ReadWrite src) {
			if (src.bytes == null) {
				this.bytes = null;
				this.length = 0;
			} else {
				this.bytes = Arrays.copyOf(src.bytes, src.bytes.length);
				this.length = src.length;
			}
		}

		void copyFrom(ReadOnly src) {
			if (src.isCompressed) {
				this.bytes = new byte[src.uncompressedSize];
				this.length = bytes.length;
				try {
					InputStream stream = src.createInputStream();
					int len = 0;
					while (len < length) {
						int read = stream.read(bytes, len, length - len);
						if (read <= 0) {
							throw new EOFException();
						}
						len += read;
					}
				} catch (IOException e) {
					throw new IllegalStateException("Failed to read a perfectly good compressed stream!", e);
				}
			} else {
				int offset = src.bytesOffset();
				this.bytes = Arrays.copyOfRange(src.byteArray(), offset, offset + src.bytesLength());
				this.length = src.bytesLength();
			}
		}

		private void expand(int to) throws IOException {
			if (to > MAX_FILE_SIZE) {
				throw new IOException("File too big!");
			}

			if (bytes != null && bytes.length >= to) {
				return;
			}

			int len = Integer.highestOneBit(to);
			if (len < to) {
				len <<= 1;
			}
			actuallyExpand(len);
		}

		private void actuallyExpand(int to) {
			byte[] newArray = new byte[to];
			if (bytes != null) {
				System.arraycopy(bytes, 0, newArray, 0, length);
			}
			this.bytes = newArray;
		}

		@Override
		protected BasicFileAttributes createAttributes() {
			return new QuiltFileAttributes(path, length);
		}

		@Override
		InputStream createInputStream() throws IOException {
			return new InputStream() {

				private final byte[] tempReader = new byte[0];

				private int position;

				@Override
				public int available() throws IOException {
					synchronized (sync()) {
						return length - position;
					}
				}

				@Override
				public int read() throws IOException {
					synchronized (sync()) {
						int result = read(tempReader);
						if (result < 0) {
							return result;
						} else if (result != 1) {
							throw new IOException("Something went wrong while reading - didn't read exactly 1 byte!");
						} else {
							return tempReader[0];
						}
					}
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					synchronized (sync()) {
						int available = length - position;
						if (available <= 0) {
							return -1;
						}
						int toRead = Math.min(available, len);
						System.arraycopy(bytes, position, b, off, toRead);
						position += toRead;
						return toRead;
					}
				}
			};
		}

		@Override
		OutputStream createOutputStream(boolean append) throws IOException {
			return new OutputStream() {

				private final byte[] tempWriter = new byte[1];

				private int position = append ? length : 0;

				@Override
				public void write(int b) throws IOException {
					synchronized (sync()) {
						tempWriter[0] = (byte) b;
						write(tempWriter);
					}
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					synchronized (sync()) {
						int newLength = position + len;
						expand(newLength);
						System.arraycopy(b, off, bytes, position, len);
						position += len;
						length += len;
					}
				}
			};
		}

		@Override
		SeekableByteChannel createByteChannel(Set<? extends OpenOption> options) {
			return new SeekableByteChannel() {

				int position = 0;

				@Override
				public boolean isOpen() {
					return true;
				}

				@Override
				public void close() throws IOException {
					// no.
				}

				@Override
				public int write(ByteBuffer src) throws IOException {
					synchronized (sync()) {
						int len = src.remaining();
						int newLength = position + len;
						expand(newLength);
						src.get(bytes, position, len);
						position += len;
						length = Math.max(length, position);
						return len;
					}
				}

				@Override
				public SeekableByteChannel truncate(long size) throws IOException {
					synchronized (sync()) {
						if (length > size) {
							length = (int) size;
						}
					}
					return this;
				}

				@Override
				public long size() throws IOException {
					synchronized (sync()) {
						return length;
					}
				}

				@Override
				public int read(ByteBuffer dst) throws IOException {
					synchronized (sync()) {
						int available = length - position;
						if (available <= 0) {
							return -1;
						}
						int toRead = Math.min(available, dst.remaining());
						dst.put(bytes, position, toRead);
						position += toRead;
						return toRead;
					}
				}

				@Override
				public SeekableByteChannel position(long newPosition) throws IOException {
					synchronized (sync()) {
						this.position = (int) Math.min(newPosition, MAX_FILE_SIZE);
					}
					return this;
				}

				@Override
				public long position() throws IOException {
					return position;
				}
			};
		}
	}
}
