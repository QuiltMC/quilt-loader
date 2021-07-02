package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.ModContributor;

class ModContributorImpl implements ModContributor {
	private final String name;
	private final String role;

	ModContributorImpl(String name, String role) {
		this.name = name;
		this.role = role;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String role() {
		return role;
	}
}
