package org.quiltmc.loader.api;

import net.fabricmc.loader.api.ModContainer;

/**
 * A mod initializer.
 *
 * <p>In {@code fabric.mod.json}, the entrypoint is defined with {@code main} key.</p>
 *
 * @see ClientModInitializer
 * @see DedicatedServerModInitializer
 * @see net.fabricmc.loader.api.FabricLoader#getEntrypointContainers(String, Class)
 */
@FunctionalInterface
public interface ModInitializer {
	void onInitialize(ModContainer mod);
}
