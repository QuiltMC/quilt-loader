package net.fabricmc.loader.impl.quiltmc;

import java.nio.file.Path;
import java.util.List;

import org.quiltmc.loader.api.ModContainer;

public final class Quilt2FabricModOrigin implements net.fabricmc.loader.api.metadata.ModOrigin {
	private final ModContainer mod;

	public Quilt2FabricModOrigin(ModContainer quilt) {
		this.mod = quilt;
	}

	@Override
	public Kind getKind() {
		return Kind.UNKNOWN;
	}

	@Override
	public List<Path> getPaths() {
		throw new UnsupportedOperationException("getPaths() Not supported for Kind.UNKNOWN");
	}

	@Override
	public String getParentModId() {
		throw new UnsupportedOperationException("getParentModId() Not supported for Kind.UNKNOWN");
	}

	@Override
	public String getParentSubLocation() {
		throw new UnsupportedOperationException("getParentSubLocation() Not supported for Kind.UNKNOWN");
	}
}
