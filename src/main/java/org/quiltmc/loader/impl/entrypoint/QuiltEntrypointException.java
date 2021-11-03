package org.quiltmc.loader.impl.entrypoint;

import org.quiltmc.loader.api.entrypoint.EntrypointException;

public class QuiltEntrypointException extends EntrypointException {

	private final String key;

	public QuiltEntrypointException(String key, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "'!", cause);
		this.key = key;
	}

	public QuiltEntrypointException(String key, String causingMod, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "' provided by '" + causingMod + "'", cause);
		this.key = key;
	}

	public QuiltEntrypointException(String s) {
		super(s);
		this.key = "";
	}

	public QuiltEntrypointException(Throwable t) {
		super(t);
		this.key = "";
	}

	@Override
	public String getKey() {
		return key;
	}
}
