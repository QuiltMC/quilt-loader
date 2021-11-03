package org.quiltmc.loader.api.minecraft;

import org.quiltmc.loader.api.ModContainer;

/**
 * A mod initializer ran only on {@link EnvType#SERVER}.
 *
 * <p>In {@code fabric.mod.json}, the entrypoint is defined with {@code server} key.</p>
 *
 * @see ModInitializer
 * @see ClientModInitializer
 * @see org.quiltmc.loader.api.QuiltLoader#getEntrypointContainers(String, Class)
 */
@FunctionalInterface
public interface DedicatedServerModInitializer {
	/**
	 * Runs the mod initializer on the server environment.
	 */
	void onInitializeServer(ModContainer mod);
}
