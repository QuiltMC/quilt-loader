package org.quiltmc.loader.impl.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public final class ExposedByteArrayOutputStream extends ByteArrayOutputStream {
	public byte[] getArray() {
		return buf;
	}

	public ByteBuffer wrapIntoBuffer() {
		return ByteBuffer.wrap(buf, 0, count);
	}
}
