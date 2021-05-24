package org.quiltmc.loader.impl.metadata.qmj;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;

/**
 * The central class used to read a quilt.mod.json.
 */
public final class ModMetadataReader {
	private static final String SCHEMA_VERSION = "schema_version";

	/**
	 * Reads the {@code quilt.mod.json} at the supplied path
	 *
	 * @param logger the logger to emit warnings from
	 * @param json the json file to read
	 * @return an instance of mod metadata
	 * @throws IOException if there are any issues reading the json file
	 * @throws ParseException if the json file has errors in the quilt.mod.json specification
	 */
	public static ModMetadata read(Logger logger, Path json, Map<String, ModLicense> spdxLicenses) throws IOException, ParseException {
		JsonLoaderValue value;

		try (JsonReader reader = JsonReader.createStrict(new InputStreamReader(Files.newInputStream(json), StandardCharsets.UTF_8))) {
			// Root must be an object
			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				throw new ParseException(reader, "A quilt.mod.json must have an object at the root");
			}

			// Read the entire file
			value = JsonLoaderValue.read(reader);

			// Make sure we don't have anything else lurking at the bottom of the document
			if (reader.peek() == JsonToken.END_DOCUMENT) {
				throw new ParseException(reader, "Encountered additional data at end of document");
			}
		}

		// We have asserted above we have an object
		JsonLoaderValue.ObjectImpl root = value.getObject();
		@Nullable JsonLoaderValue schemaVersion = root.get(SCHEMA_VERSION);

		if (schemaVersion == null) {
			throw new ParseException("No schema_version field was found, this is probably not a quilt mod");
		}

		if (schemaVersion.type() != LoaderValue.LType.NUMBER) {
			throw parseException(schemaVersion, "schema_version must be a number");
		}

		int version = schemaVersion.getNumber().intValue();

		switch (version) {
		case 1:
			return V1ModMetadataReader.read(logger, root, spdxLicenses);
		default:
			if (version < 0) {
				throw parseException(schemaVersion, "schema_version must not be negative");
			} else if (version == 0) {
				// We have no support for version 0
				throw parseException(schemaVersion, "schema_version cannot be 0");
			}

			throw parseException(schemaVersion, String.format("encountered unsupported schema_version %s, you may need to update loader?", version));
		}
	}

	/**
	 * Creates a parse exception that also includes the location of a json loader value.
	 *
	 * @param value the value to get the location of
	 * @param message the error message
	 * @return a new parse exception
	 */
	static ParseException parseException(JsonLoaderValue value, String message) {
		return new ParseException(String.format("%s %s", value.location(), message));
	}

	private ModMetadataReader() {
	}
}
