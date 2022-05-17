package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ProvidedMod;

public class ProvidedModImpl implements ProvidedMod {

	private final String group, id;
	private final Version version;

	public ProvidedModImpl(String group, String id, Version version) {
		this.group = group;
		this.id = id;
		this.version = version;
	}

	@Override
	public String group() {
		return group;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Version version() {
		return version;
	}

	@Override
	public String toString() {
		return "ProvidedMod { " + group + ":" + id + " v " + version + " }";
	}
}
