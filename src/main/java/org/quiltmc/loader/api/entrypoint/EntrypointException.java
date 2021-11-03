package org.quiltmc.loader.api.entrypoint;

import org.quiltmc.loader.api.QuiltLoader;

/**
 * Represents an exception that arises when obtaining entrypoints.
 * 
 * @see QuiltLoader#getEntrypointContainers(String, Class) 
 */
@SuppressWarnings("serial")
public abstract class EntrypointException extends RuntimeException {

	public EntrypointException() {}

	public EntrypointException(String message) {
		super(message);
	}

	public EntrypointException(String message, Throwable cause) {
		super(message, cause);
	}

	public EntrypointException(Throwable cause) {
		super(cause);
	}

	/**
	 * Returns the key of entrypoint in which the exception arose.
	 *
	 * @return the key
	 */
	public abstract String getKey();
}
