package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.api.Version;

public class ModProvided {
	public final String group;
	public final String id;
	public final Version version;

	public ModProvided(String group, String id, Version version) {
		this.group = group;
		this.id = id;
		this.version = version;
	}
}
