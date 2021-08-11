package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Map;

import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.ModMetadataToBeMovedToPlugins;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;

/**
 * Internal mod metadata interface which stores implementation detail.
 */
public interface InternalModMetadata extends ModMetadata, ModMetadataToBeMovedToPlugins, ConvertibleModMetadata {
	// TODO: Data type
	Collection<?> provides();

	ModLoadType loadType();

	Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints();

	Collection<AdapterLoadableClassEntry> getPlugins();

	Collection<String> jars();

	Map<String, String> languageAdapters();

	Collection<String> repositories();

	@Override
	default LoaderModMetadata asFabricModMetadata() {
	    return new QuiltModMetadataWrapper(this);
	}

	@Override
	default InternalModMetadata asQuiltModMetadata() {
	    return this;
	}
}
