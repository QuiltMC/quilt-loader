package org.quiltmc.loader.impl.transformer;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

/** Indicates that the installed chasm version doesn't match what loader expects. Temporary exception since chasm will
 * eventually be built into loader. */
@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class UnsupportedChasmException extends RuntimeException {

	public UnsupportedChasmException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnsupportedChasmException(String message) {
		super(message);
	}

	public UnsupportedChasmException(Throwable cause) {
		super(cause);
	}

}
