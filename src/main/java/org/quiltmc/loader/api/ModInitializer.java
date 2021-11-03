package org.quiltmc.loader.api;

/**
 * A mod initializer.
 *
 * <p>In {@code quilt.mod.json}, the entrypoint is defined with {@code main} key.</p>
 *
 * @see ClientModInitializer
 * @see DedicatedServerModInitializer
 * @see org.quiltmc.loader.api.QuiltLoader#getEntrypointContainers(String, Class)
 */
@FunctionalInterface
public interface ModInitializer {
	void onInitialize(ModContainer mod);
}
