package org.quiltmc.loader.api.plugin;

import org.quiltmc.loader.api.ModContainer;

public interface ModContainerExt extends ModContainer {
	@Override
	ModMetadataExt metadata();

	String pluginId();
}
