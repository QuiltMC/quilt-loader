package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.Version;

public class GenericVersionImpl implements Version {
	private final String raw;

	public GenericVersionImpl(String raw) {
		this.raw = raw;
	}

	@Override
	public String raw() {
		return raw;
	}
}
