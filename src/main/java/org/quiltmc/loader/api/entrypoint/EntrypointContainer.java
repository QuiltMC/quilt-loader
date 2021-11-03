package org.quiltmc.loader.api.entrypoint;

import org.quiltmc.loader.api.ModContainer;

/**
 * A container holding both an entrypoint instance and the {@link ModContainer} which has provided the entrypoint.
 *
 * @param <T> The type of the entrypoint
 * @see org.quiltmc.loader.api.QuiltLoader#getEntrypointContainers(String, Class) 
 */
public interface EntrypointContainer<T> {
	/**
	 * Returns the entrypoint instance. It will be constructed the first time you call this method.
	 */
	T getEntrypoint();

	/**
	 * Returns the mod that provided this entrypoint.
	 */
	ModContainer getProvider();
}
