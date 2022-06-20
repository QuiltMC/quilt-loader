package org.quiltmc.loader.impl.plugin.quilt;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.plugin.base.InternalModContainerBase;

public class BuiltinModContainer extends InternalModContainerBase {

	public BuiltinModContainer(String pluginId, ModMetadataExt metadata, Path from, Path resourceRoot) {
		super(pluginId, metadata, from, resourceRoot);
	}

	@Override
	public BasicSourceType getSourceType() {
		return BasicSourceType.BUILTIN;
	}
}
