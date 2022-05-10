package org.quiltmc.loader.api.config.exceptions;

public final class ConfigFieldException extends RuntimeException {
	public ConfigFieldException() {
	}

	public ConfigFieldException(String message) {
		super(message);
	}

	public ConfigFieldException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConfigFieldException(Throwable cause) {
		super(cause);
	}
}
