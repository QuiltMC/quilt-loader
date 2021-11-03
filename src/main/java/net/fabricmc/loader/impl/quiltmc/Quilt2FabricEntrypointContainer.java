package net.fabricmc.loader.impl.quiltmc;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

public final class Quilt2FabricEntrypointContainer<T> implements EntrypointContainer<T> {

	private final org.quiltmc.loader.api.entrypoint.EntrypointContainer<T> quilt;

	public Quilt2FabricEntrypointContainer(org.quiltmc.loader.api.entrypoint.EntrypointContainer<T> quilt) {
		this.quilt = quilt;
	}

	@Override
	public T getEntrypoint() {
		return quilt.getEntrypoint();
	}

	@Override
	public ModContainer getProvider() {
		return new Quilt2FabricModContainer(quilt.getProvider());
	}
}
