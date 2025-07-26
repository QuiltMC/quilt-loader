package org.quiltmc.loader.impl.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ReadOnlyByteArrayChannel implements SeekableByteChannel {

	private final byte[] source;
	private final int offset;
	private final int length;

	private int position;

	public ReadOnlyByteArrayChannel(byte[] source) {
		this(source, 0, source.length);
	}

	public ReadOnlyByteArrayChannel(byte[] source, int offset, int length) {
		this.source = source;
		this.offset = offset;
		this.length = length;
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public void close() throws IOException {
		// NO-OP
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		if (position >= length) {
			return -1;
		}

		int max = Math.min(dst.remaining(), length - position);
		dst.put(source, offset + position, max);
		return max;
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		throw new IOException("Read Only!");
	}

	@Override
	public long position() throws IOException {
		return position;
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		if (newPosition > Integer.MAX_VALUE) {
			newPosition = Integer.MAX_VALUE;
		}
		position = (int) newPosition;
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
			throw new IOException("Read Only!");
		}
	}

}
