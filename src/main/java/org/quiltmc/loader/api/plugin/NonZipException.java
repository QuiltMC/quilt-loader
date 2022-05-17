package org.quiltmc.loader.api.plugin;

/** Thrown by {@link QuiltPluginManager#loadZip(java.nio.file.Path)} if a file couldn't be opened as a zip. */
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
