package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.api.ModMetadata;

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
}
