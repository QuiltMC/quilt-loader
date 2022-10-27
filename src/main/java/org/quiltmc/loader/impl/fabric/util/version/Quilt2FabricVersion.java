package org.quiltmc.loader.impl.fabric.util.version;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.util.version.SemanticVersionImpl;

public class Quilt2FabricVersion implements net.fabricmc.loader.api.Version {

	final org.quiltmc.loader.api.Version quilt;

	Quilt2FabricVersion(org.quiltmc.loader.api.Version quilt) {
		this.quilt = quilt;
	}

	public static net.fabricmc.loader.api.Version toFabric(org.quiltmc.loader.api.Version quilt) {
		if (quilt == null) {
			return null;
		} else if (quilt.isSemantic()) {
			return new Quilt2FabricSemanticVersion(quilt.semantic());
		} else {
			return new Quilt2FabricVersion(quilt);
		}
	}

	public static org.quiltmc.loader.api.Version fromFabric(net.fabricmc.loader.api.Version from) {
		if (from == null) {
			return null;
		} else if (from instanceof Quilt2FabricVersion) {
			return ((Quilt2FabricVersion) from).quilt;
		} else if (from instanceof SemanticVersion) {
			return Quilt2FabricSemanticVersion.fromFabric((SemanticVersion) from);
		} else {
			throw new IllegalStateException("Unexpected version " + from.getClass());
		}
	}

	@Override
	public int compareTo(net.fabricmc.loader.api.Version o) {
		return quilt.compareTo(fromFabric(o));
	}

	@Override
	public String getFriendlyString() {
		return quilt.raw();
	}

	@Override
	public String toString() {
		return quilt.raw();
	}
}
