package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.ModLicense;

final class ModLicenseImpl implements ModLicense {
	private final String name;
	private final String id;
	private final String url;
	private final String description;

	ModLicenseImpl(String name, String id, String url, String description) {
		this.name = name;
		this.id = id;
		this.url = url;
		this.description = description;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public String id() {
		return this.id;
	}

	@Override
	public String url() {
		return this.url;
	}

	@Override
	public String description() {
		return this.description;
	}
}
