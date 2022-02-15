package net.fabricmc.loader.impl.quiltmc;


import org.quiltmc.loader.api.ModSource;

import java.nio.file.Path;
import java.util.List;

public final class Quilt2FabricModOrigin implements net.fabricmc.loader.api.metadata.ModOrigin {
	private final ModSource quilt;
	private final Kind fabricKind;
	public Quilt2FabricModOrigin(ModSource quilt) {
		this.quilt = quilt;
		this.fabricKind = net.fabricmc.loader.api.metadata.ModOrigin.Kind.valueOf(quilt.getKind().toString());
	}
	@Override
	public Kind getKind() {
		return fabricKind;
	}

	@Override
	public List<Path> getPaths() {
		return quilt.getPaths();
	}

	@Override
	public String getParentModId() {
		return quilt.getParentModId();
	}

	@Override
	public String getParentSubLocation() {
		return quilt.getParentSubLocation();
	}
}
