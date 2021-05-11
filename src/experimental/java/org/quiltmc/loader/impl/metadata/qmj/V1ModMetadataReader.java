package org.quiltmc.loader.impl.metadata.qmj;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;

import static org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader.parseException;

final class V1ModMetadataReader {
	private V1ModMetadataReader() {
	}

	public static V1ModMetadataImpl read(Logger logger, JsonLoaderValue.ObjectImpl root) {
		// Read loader category
		@Nullable JsonLoaderValue quiltLoader = root.get("quilt_loader");

		if (quiltLoader == null) {
			throw new ParseException("quilt_loader field is required");
		}

		if (quiltLoader.type() != LoaderValue.LType.OBJECT) {
			throw parseException(quiltLoader, "quilt_loader field must be an object");
		}

		throw new UnsupportedOperationException("Implement me!");
	}
}
