package org.quiltmc.loader.impl.plugin;

final class HaltLoadingError extends Error {
	static final HaltLoadingError INSTANCE = new HaltLoadingError();

	private HaltLoadingError() {
		super(null, null, false, false);
	}
}
