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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.quiltmc.loader.impl.util.DisconnectableByteChannel;
import org.quiltmc.loader.impl.util.ExposedByteArrayOutputStream;
import org.quiltmc.loader.impl.util.QuiltLoaderCleanupTasks;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.ReadOnlyByteArrayChannel;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
abstract class ZipSource implements FileSystemSource {

	abstract InputStream openConstructingStream() throws IOException;

	abstract ZipSource forIndividualFile(long offset, int length);

	abstract void build() throws IOException;

	abstract InputStream stream(long position, int length) throws IOException;

	abstract SeekableByteChannel channel(long position, int length) throws IOException;

	static abstract class NonClosingSource extends ZipSource {

		@Override
		public boolean needsClosing() {
			return false;
		}

		@Override
		public boolean isOpen() {
			return true;
		}

		@Override
		public void open(QuiltBaseFileSystem<?, ?> fs) {
			// NO-OP
		}

		@Override
		public void close(QuiltBaseFileSystem<?, ?> fs) throws IOException {
			// CO-OP
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class InputStreamSource extends NonClosingSource {

		private InputStream from;
		private ExposedByteArrayOutputStream baos;

		InputStreamSource(InputStream from) {
			this.from = from;
			this.baos = new ExposedByteArrayOutputStream();
		}

		@Override
		InputStream openConstructingStream() throws IOException {
			return new InputStream() {
				@Override
				public int read() throws IOException {
					int read = from.read();
					if (read >= 0) {
						baos.write(read);
					}
					return read;
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int read = from.read(b, off, len);
					if (read > 0) {
						baos.write(b, off, read);
					}
					return read;
				}

				@Override
				public void close() throws IOException {
					from.close();
				}
			};
		}

		@Override
		ZipSource forIndividualFile(long offset, int length) {
			int pos = (int) offset;
			return new InMemorySource(Arrays.copyOfRange(baos.getArray(), pos, pos + length));
		}

		@Override
		void build() throws IOException {
			from.close();
			from = null;
			baos = null;
		}

		@Override
		InputStream stream(long position, int length) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		SeekableByteChannel channel(long position, int length) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class InMemorySource extends NonClosingSource {

		private final byte[] source;

		InMemorySource(byte[] source) {
			this.source = source;
		}

		@Override
		InputStream openConstructingStream() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		ZipSource forIndividualFile(long offset, int length) {
			throw new UnsupportedOperationException();
		}

		@Override
		void build() throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		InputStream stream(long position, int length) throws IOException {
			return new ByteArrayInputStream(source);
		}

		@Override
		SeekableByteChannel channel(long position, int length) throws IOException {
			return new ReadOnlyByteArrayChannel(source);
		}
	}

	/** Used to cache {@link SeekableByteChannel} per-thread, since it's an expensive operation to open them. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class SharedByteChannels extends ZipSource {

		final Path zipFrom;
		final Set<WeakReference<QuiltBaseFileSystem<?, ?>>> fileSystems = new HashSet<>();

		// This is not very nice: we want to use a ThreadLocal
		// but we can't since we need to close every channel afterwards
		final Map<Thread, SeekableByteChannel> channels;
		volatile boolean isOpen = true;

		SharedByteChannels(Path zipFrom) {
			this.zipFrom = zipFrom;
			channels = new ConcurrentHashMap<>();
			QuiltLoaderCleanupTasks.addCleanupTask(this, this::removeDeadThreads);
		}

		@Override
		public InputStream openConstructingStream() throws IOException {
			return Files.newInputStream(zipFrom);
		}

		@Override
		ZipSource forIndividualFile(long offset, int length) {
			return this;
		}

		@Override
		void build() throws IOException {
			// NO-OP
		}

		@Override
		public boolean needsClosing() {
			return true;
		}

		@Override
		public boolean isOpen() {
			return isOpen;
		}

		@Override
		public synchronized void open(QuiltBaseFileSystem<?, ?> fs) {
			fileSystems.add(fs.thisRef);
		}

		@Override
		public synchronized void close(QuiltBaseFileSystem<?, ?> fs) throws IOException {
			fileSystems.remove(fs.thisRef);
			if (fileSystems.isEmpty()) {
				isOpen = false;
				for (SeekableByteChannel channel : channels.values()) {
					channel.close();
				}
				channels.clear();
				QuiltLoaderCleanupTasks.removeCleanupTask(this);
			}
		}

		@Override
		InputStream stream(long position, int length) throws IOException {
			return new SharedByteChannels.ByteChannel2Stream(channel(position, length), 0);
		}

		@Override
		SeekableByteChannel channel(long position, int length) throws IOException {
			try {
				SeekableByteChannel srcChannel = channels.computeIfAbsent(Thread.currentThread(), t -> {
					try {
						return Files.newByteChannel(zipFrom, StandardOpenOption.READ);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
				return new OffsetSeekableByteChannel(new DisconnectableByteChannel(srcChannel), position, length);
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

			Iterator<WeakReference<QuiltBaseFileSystem<?, ?>>> iter = fileSystems.iterator();
			while (iter.hasNext()) {
				if (iter.next().get() == null) {
					iter.remove();
				}
			}

			if (fileSystems.isEmpty()) {
				QuiltLoaderCleanupTasks.removeCleanupTask(this);
			}
		}

		/** An {@link InputStream} which is based on a {@link SeekableByteChannel}, which allows the backing channel to
		 * be used by multiple streams in the same thread. */
		@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
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

			@Override
			public void close() throws IOException {
				channel.close();
			}
		}

		@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
		static class OffsetSeekableByteChannel implements SeekableByteChannel {
			final SeekableByteChannel from;

			final long offset;
			final int length;
			volatile long position = 0;

			OffsetSeekableByteChannel(SeekableByteChannel from, long offset, int length) throws IOException {
				if (length == -1) {
					length = (int) (from.size() - offset);
				}
				this.offset = offset;
				this.length = length;
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
				if (position >= length) {
					return -1;
				}
				int toRead = (int) Math.min(length - position, dst.remaining());
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
				return length;
			}

			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				if (size >= length) {
					return this;
				} else {
					throw new IOException("read only");
				}
			}
		}
	}
}
