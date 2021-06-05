package org.quiltmc.loader.api;

public final class VersionFormatException extends Exception {

	public VersionFormatException() {
	}

	public VersionFormatException(String message) {
		super(message);
	}

	public VersionFormatException(String message, Throwable cause) {
		super(message, cause);
	}

	public VersionFormatException(Throwable cause) {
		super(cause);
	}
}
