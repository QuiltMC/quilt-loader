package org.quiltmc.loader.api.minecraft;

import org.quiltmc.loader.api.ModContainer;

import net.fabricmc.api.EnvType;

/**
 * A mod initializer ran only on {@link EnvType#CLIENT}.
 *
 * <p>This entrypoint is suitable for setting up client-specific logic, such as rendering
 * or integrated server tweaks.</p>
 *
 * <p>In {@code fabric.mod.json}, the entrypoint is defined with {@code client} key.</p>
 *
 * @see ModInitializer
 * @see DedicatedServerModInitializer
 * @see org.quiltmc.loader.api.QuiltLoader#getEntrypointContainers(String, Class)
 */
@FunctionalInterface
public interface ClientModInitializer {
	/**
	 * Runs the mod initializer on the client environment.
	 */
	void onInitializeClient(ModContainer mod);
}
