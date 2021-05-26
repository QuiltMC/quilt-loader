package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Map;

import org.quiltmc.loader.api.ModMetadata;

/**
 * Internal mod metadata interface which stores implementation detail.
 */
public interface InternalModMetadata extends ModMetadata, ModMetadataToBeMovedToPlugins {
	// TODO: Data type
	Collection<?> provides();

	Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints(String key);

	Collection<AdapterLoadableClassEntry> getPlugins();

	Collection<String> jars();

	Map<String, String> languageAdapters();

	Collection<String> repositories();
}
