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

package org.quiltmc.loader.impl.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

/** A delegating {@link SeekableByteChannel} that doesn't {@link Closeable#close()} the underlying byte channel. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class DisconnectableByteChannel implements SeekableByteChannel {

	private final SeekableByteChannel to;
	private boolean closed = false;

	public DisconnectableByteChannel(SeekableByteChannel to) {
		this.to = to;
	}

	@Override
	public boolean isOpen() {
		return !closed;
	}

	private final void ensureOpen() throws IOException {
		if (closed) {
			throw new ClosedChannelException();
		}
	}

	@Override
	public void close() throws IOException {
		closed = true;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		ensureOpen();
		return to.read(dst);
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		ensureOpen();
		return to.write(src);
	}

	@Override
	public long position() throws IOException {
		ensureOpen();
		return to.position();
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		ensureOpen();
		to.position(newPosition);
		return this;
	}

	@Override
	public long size() throws IOException {
		ensureOpen();
		return to.size();
	}

	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		ensureOpen();
		to.truncate(size);
		return this;
	}
}
