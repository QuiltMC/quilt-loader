package org.quiltmc.loader.impl.transformer;

import org.quiltmc.loader.impl.discovery.ModResolutionException;

/** Thrown when something goes wrong with chasm. */
public class ChasmTransformException extends ModResolutionException {

	public ChasmTransformException(String format, Object... args) {
		super(format, args);
	}

	public ChasmTransformException(String s, Throwable t) {
		super(s, t);
	}

	public ChasmTransformException(String s) {
		super(s);
	}

	public ChasmTransformException(Throwable t) {
		super(t);
	}
}
