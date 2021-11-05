package org.quiltmc.loader.api.minecraft;

import org.quiltmc.loader.impl.QuiltLoaderImpl;

import net.fabricmc.api.EnvType;

/** Public access for some minecraft-specific functionality in quilt loader. */
public final class MinecraftQuiltLoader {
	private MinecraftQuiltLoader() {}

	/**
	 * Get the current environment type.
	 *
	 * @return the current environment type
	 */
	public static EnvType getEnvironmentType() {
		// TODO: Get this from a plugin instead!
		QuiltLoaderImpl impl = QuiltLoaderImpl.INSTANCE;
		if (impl == null) {
			throw new IllegalStateException("Accessed QuiltLoader too early!");
		}
		return impl.getEnvironmentType();
	}
}
