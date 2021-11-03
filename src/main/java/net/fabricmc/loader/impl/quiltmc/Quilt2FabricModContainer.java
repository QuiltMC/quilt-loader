package net.fabricmc.loader.impl.quiltmc;

import java.nio.file.Path;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.metadata.qmj.ConvertibleModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;

import net.fabricmc.loader.api.metadata.ModMetadata;

public final class Quilt2FabricModContainer implements net.fabricmc.loader.api.ModContainer {
	private final ModContainer quilt;

	public Quilt2FabricModContainer(ModContainer quilt) {
		this.quilt = quilt;
	}

	@Override
	public ModMetadata getMetadata() {
		return ((ConvertibleModMetadata) quilt.metadata()).asFabricModMetadata();
	}

	@Override
	public Path getRootPath() {
		return quilt.rootPath();
	}
}
