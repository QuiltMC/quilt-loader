/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.metadata.qmj;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;

/**
 * The central class used to read a {@code quilt.mod.json}.
 */
public final class ModMetadataReader {
	/**
	 * Name of the schema field used to detect what version of the {@code quilt.mod.json} file we are parsing. This
	 * field name should be used in all future versions of the {@code quilt.mod.json} to ensure past loader versions
	 * may detect and not load versions of the schema that are not understood.
	 */
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
	@SuppressWarnings("SwitchStatementWithTooFewBranches") // Switch statement intentionally used for future expandability
	public static InternalModMetadata read(Logger logger, Path json) throws IOException, ParseException {
		JsonLoaderValue value;

		try (JsonReader reader = JsonReader.json(new InputStreamReader(Files.newInputStream(json), StandardCharsets.UTF_8))) {
			// Root must be an object
			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				throw new ParseException(reader, "A quilt.mod.json must have an object at the root");
			}

			// Read the entire file
			value = JsonLoaderValue.read(reader);

			// Make sure we don't have anything else lurking at the bottom of the document
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new ParseException(reader, "Encountered additional data at end of document");
			}
		}

		// We have asserted above we have an object
		JsonLoaderValue.ObjectImpl root = value.asObject();
		@Nullable JsonLoaderValue schemaVersion = root.get(SCHEMA_VERSION);

		if (schemaVersion == null) {
			throw new ParseException("No schema_version field was found, this is probably not a quilt mod");
		}

		if (schemaVersion.type() != LoaderValue.LType.NUMBER) {
			throw parseException(schemaVersion, "schema_version must be a number");
		}

		int version = schemaVersion.asNumber().intValue();

		switch (version) {
		case 1:
			return V1ModMetadataReader.read(logger, root);
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

	/**
	 * Creates a parse exception that also includes the location of a json loader value.
	 *
	 * @param value the value to get the location of
	 * @param message the error message
	 * @return a new parse exception
	 */
	static ParseException parseException(JsonLoaderValue value, String message, Throwable cause) {
		return new ParseException(String.format("%s %s", value.location(), message), cause);
	}

	private ModMetadataReader() {
	}
}
