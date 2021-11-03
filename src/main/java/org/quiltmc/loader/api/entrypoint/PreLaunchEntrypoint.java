package org.quiltmc.loader.api.entrypoint;

import org.quiltmc.loader.api.ModContainer;

/**
 * Entrypoint getting invoked just before launching the game.
 *
 * <p><b>Avoid interfering with the game from this!</b> Accessing anything needs careful consideration to avoid
 * interfering with its own initialization or otherwise harming its state. It is recommended to implement this interface
 * on its own class to avoid running static initializers too early, e.g. because they were referenced in field or method
 * signatures in the same class.
 *
 * <p>The entrypoint is exposed with {@code preLaunch} key in the mod json and runs for any environment. It usually
 * executes several seconds before the {@code main}/{@code client}/{@code server} entrypoints.
 * 
 * @see net.fabricmc.loader.api.FabricLoader#getEntrypointContainers(String, Class) 
 */
@FunctionalInterface
public interface PreLaunchEntrypoint extends GameEntrypoint {
	void onPreLaunch(ModContainer mod);
}
