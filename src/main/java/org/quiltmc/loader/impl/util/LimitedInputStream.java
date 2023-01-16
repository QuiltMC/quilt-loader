package org.quiltmc.loader.impl.util;

import java.io.IOException;
import java.io.InputStream;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class LimitedInputStream extends InputStream {
	private final InputStream from;
	private final int limit;

	private int position;

	public LimitedInputStream(InputStream from, int limit) {
		this.from = from;
		this.limit = limit;
	}

	@Override
	public int available() throws IOException {
		return limit - position;
	}

	@Override
	public int read() throws IOException {
		if (position < limit) {
			position++;
			return from.read();
		} else {
			return -1;
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len <= 0) {
			return 0;
		}
		int max = Math.min(len, limit - position);
		if (max <= 0) {
			return -1;
		}
		int read = from.read(b, off, max);
		if (read > 0) {
			position += read;
		}
		return read;
	}
}
