package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.ModDependencyIdentifier;

public final class ModDependencyIdentifierImpl implements ModDependencyIdentifier {
	private final String mavenGroup;
	private final String id;

	public ModDependencyIdentifierImpl(String raw) {
		int split = raw.indexOf(":");
		if (split > 0) {
			mavenGroup = raw.substring(0, split);
			id = raw.substring(split);
		} else {
			mavenGroup = "";
			id = raw;
		}
	}

	public ModDependencyIdentifierImpl(String mavenGroup, String id) {
		this.mavenGroup = mavenGroup;
		this.id = id;
	}

	@Override
	public String mavenGroup() {
		return mavenGroup;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public String toString() {
		if (!this.mavenGroup.isEmpty()) {
			return this.mavenGroup + ":" + this.id;
		}

		return this.id;
	}
}
