package org.quiltmc.loader.api;

// TODO: make this extend the Fabric exception?
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
