package org.quiltmc.loader.api;

import java.util.Collection;

import net.fabricmc.loader.api.metadata.ModEnvironment;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holder interface for all fields that should be moved to a loader plugin.
 *
 * @deprecated subject to removal after plugins are implemented.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface ModMetadataToBeMovedToPlugins extends ModMetadata {
	Collection<String> mixins();

	Collection<String> accessWideners();

	ModEnvironment environment();
}
