package org.quiltmc.loader.impl.metadata.qmj;

import org.quiltmc.loader.impl.metadata.LoaderModMetadata;

public interface ConvertibleModMetadata {
	LoaderModMetadata asFabricModMetadata();

	InternalModMetadata asQuiltModMetadata();
}
