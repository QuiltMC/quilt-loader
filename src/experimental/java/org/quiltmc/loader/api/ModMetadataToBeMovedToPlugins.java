package org.quiltmc.loader.api;

import java.util.Collection;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import net.fabricmc.api.EnvType;

import org.jetbrains.annotations.ApiStatus;

/**
 * Holder interface for all fields that should be moved to a loader plugin.
 *
 * @deprecated subject to removal after plugins are implemented.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface ModMetadataToBeMovedToPlugins extends ModMetadata {
	/**
	 * @param env The environment. Only used by fabric meta.
	 */
	Collection<String> mixins(EnvType env);

	Collection<String> accessWideners();

	ModEnvironment environment();
}
