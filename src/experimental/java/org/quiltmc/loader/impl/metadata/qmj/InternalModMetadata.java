package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Map;

import org.apache.logging.log4j.core.util.Loader;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.impl.metadata.LoaderModMetadata;

/**
 * Internal mod metadata interface which stores implementation detail.
 */
public interface InternalModMetadata extends ModMetadata, ModMetadataToBeMovedToPlugins, ConvertibleModMetadata {
	// TODO: Data type
	Collection<?> provides();

	Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints(String key);

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
