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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class ByteChannelInputStream extends InputStream {
	private final ReadableByteChannel channel;

	public ByteChannelInputStream(ReadableByteChannel channel) {
		this.channel = channel;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return channel.read(ByteBuffer.wrap(b, off, len));
	}

	@Override
	public int read() throws IOException {
		byte[] array = new byte[0];
		int read = read(array, 0, 1);
		if (read > 0) {
			return Byte.toUnsignedInt(array[0]);
		} else {
			return -1;
		}
	}

	@Override
	public long skip(long n) throws IOException {
		if (channel instanceof SeekableByteChannel) {
			SeekableByteChannel seekable = (SeekableByteChannel) channel;
			seekable.position(seekable.position() + n);
			return n;
		} else {
			return super.skip(n);
		}
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}
}
