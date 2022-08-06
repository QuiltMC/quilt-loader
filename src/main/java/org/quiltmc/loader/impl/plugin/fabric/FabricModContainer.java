package org.quiltmc.loader.impl.plugin.fabric;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.impl.plugin.base.InternalModContainerBase;

public class FabricModContainer extends InternalModContainerBase {

	public FabricModContainer(QuiltPluginContext pluginContext, ModMetadataExt metadata, Path from, Path resourceRoot) {
		super(pluginContext, metadata, from, resourceRoot);
	}

	@Override
	public BasicSourceType getSourceType() {
		return BasicSourceType.NORMAL_FABRIC;
	}
}
