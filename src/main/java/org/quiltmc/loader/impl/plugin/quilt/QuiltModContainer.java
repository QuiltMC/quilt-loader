package org.quiltmc.loader.impl.plugin.quilt;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.plugin.base.InternalModContainerBase;

public class QuiltModContainer extends InternalModContainerBase {

	public QuiltModContainer(String pluginId, ModMetadataExt metadata, Path from, Path resourceRoot) {
		super(pluginId, metadata, from, resourceRoot);
	}

	@Override
	public BasicSourceType getSourceType() {
		return BasicSourceType.NORMAL_QUILT;
	}
}
