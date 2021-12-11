package org.quiltmc.loader.api.plugin;

public class NonZipException extends Exception {

	public NonZipException(String message) {
		super(message);
	}

	public NonZipException(Throwable cause) {
		super(cause);
	}

	public NonZipException(String message, Throwable cause) {
		super(message, cause);
	}

}
