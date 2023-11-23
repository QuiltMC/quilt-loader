package org.quiltmc.loader.impl.filesystem;

import java.io.IOException;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Thrown by {@link QuiltZipFileSystem} if the input file is zero bytes long. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class ZeroByteFileException extends IOException {

	public ZeroByteFileException() {
		super();
	}

	public ZeroByteFileException(String message) {
		super(message);
	}

	public ZeroByteFileException(Throwable cause) {
		super(cause);
	}

	public ZeroByteFileException(String message, Throwable cause) {
		super(message, cause);
	}
}
