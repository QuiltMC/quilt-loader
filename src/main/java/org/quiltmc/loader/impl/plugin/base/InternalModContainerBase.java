package org.quiltmc.loader.impl.plugin.base;

import java.nio.file.Path;
import java.util.List;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;

public class InternalModContainerBase implements ModContainerExt {

	private final String pluginId;
	private final ModMetadataExt metadata;
	private final Path from;
	private final Path resourceRoot;

	public InternalModContainerBase(String pluginId, ModMetadataExt metadata, Path from, Path resourceRoot) {
		this.pluginId = pluginId;
		this.metadata = metadata;
		this.from = from;
		this.resourceRoot = resourceRoot;
	}

	@Override
	public ModMetadataExt metadata() {
		return metadata;
	}

	@Override
	public Path rootPath() {
		return resourceRoot;
	}

	@Override
	public List<List<Path>> getSourcePaths() {
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public BasicSourceType getSourceType() {
		return BasicSourceType.NORMAL_FABRIC;
	}
}
