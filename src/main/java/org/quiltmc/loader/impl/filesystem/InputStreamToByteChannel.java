package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** A {@link SeekableByteChannel} which can only read from an {@link InputStream}, and retains read bytes in a
 * buffer. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class InputStreamToByteChannel implements SeekableByteChannel {

	private static final int BUFFER_PART_LENGTH = 1 << 13;

	private final InputStream source;

	private final List<byte[]> buffer = new ArrayList<>();
	private byte[] currentBuffer;

	/** Absolute position of {@link #buffer} plus {@link #currentBuffer} */
	private long bufferPosition;
	private boolean isSourceComplete;
	private boolean open = true;
	private long position;

	public InputStreamToByteChannel(InputStream source) {
		this.source = source;

		currentBuffer = new byte[BUFFER_PART_LENGTH];
		buffer.add(currentBuffer);
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void close() throws IOException {
		open = false;
	}

	private void ensureOpen() throws ClosedChannelException {
		if (!open) {
			throw new ClosedChannelException();
		}
	}

	private void readUntil(long newSourcePosition) throws IOException {
		while (bufferPosition < newSourcePosition) {
			if (isSourceComplete) {
				return;
			}

			int positionInCurrent = (int) (bufferPosition % BUFFER_PART_LENGTH);
			int read = source.read(currentBuffer, positionInCurrent, currentBuffer.length - positionInCurrent);

			if (read < 0) {
				isSourceComplete = true;
				currentBuffer = null;
				return;
			}

			bufferPosition += read;
			positionInCurrent += read;
			if (positionInCurrent == BUFFER_PART_LENGTH) {
				currentBuffer = new byte[BUFFER_PART_LENGTH];
				buffer.add(currentBuffer);
			}
		}
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		int length = dst.remaining();
		if (length == 0) {
			return 0;
		}

		long targetEndPosition = position + length;
		readUntil(targetEndPosition);

		if (position >= bufferPosition) {
			return -1;
		}

		int read = 0;
		int remaining = length;

		while (read < length) {

			byte[] bufferPart = buffer.get((int) (position / BUFFER_PART_LENGTH));
			int startInBuffer = (int) (position % BUFFER_PART_LENGTH);
			int endInBuffer = startInBuffer + remaining;
			endInBuffer = Math.min(endInBuffer, BUFFER_PART_LENGTH);

			int thisLen = endInBuffer - startInBuffer;
			dst.put(bufferPart, startInBuffer, thisLen);

			remaining -= thisLen;
			read += thisLen;
			position += thisLen;
		}

		return read;
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		ensureOpen();
		throw new IOException("ReadOnly");
	}

	@Override
	public long position() throws IOException {
		return position;
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		position = newPosition;
		return this;
	}

	@Override
	public long size() throws IOException {
		readUntil(Long.MAX_VALUE);
		return bufferPosition;
	}

	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		ensureOpen();
		throw new IOException("ReadOnly");
	}
}
