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

package org.quiltmc.loader.impl.metadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.FabricLoader;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;


public final class FabricModMetadataReader {
	public static final int LATEST_VERSION = 1;
	/**
	 * Keys that will be ignored by any mod metadata parser.
	 */
	public static final Set<String> IGNORED_KEYS = Collections.singleton("$schema");

	private static final Pattern MOD_ID_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{1,63}");

	public static FabricLoaderModMetadata parseMetadata(Path path) throws ParseMetadataException, IOException {
		return parseMetadata(Files.newInputStream(path));
	}
	// Per the ECMA-404 (www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf), the JSON spec does not prohibit duplicate keys.
	// For all intents and purposes of replicating the logic of Gson's fromJson before we have migrated to JsonReader, duplicate keys will replace previous entries.
	public static FabricLoaderModMetadata parseMetadata(InputStream is /*String modPath, List<String> modParentPaths*/) throws ParseMetadataException {
		try {
			FabricLoaderModMetadata ret = readModMetadata(is);

			checkModId(ret.getId(), "mod id");

			for (String providesDecl : ret.getProvides()) {
				checkModId(providesDecl, "provides declaration");
			}

			// TODO: verify mod id decls in deps

			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				if (ret.getSchemaVersion() < LATEST_VERSION) {
					Log.warn(LogCategory.METADATA, "Mod ID %s uses outdated schema version: %d < %d", ret.getId(), ret.getSchemaVersion(), FabricModMetadataReader.LATEST_VERSION);
				}

				ret.emitFormatWarnings();
			}

			return ret;
		} catch (ParseMetadataException e) {
			//e.setModPaths(modPath, modParentPaths);
			throw e;
		} catch (Throwable t) {
			ParseMetadataException e = new ParseMetadataException(t);
			//e.setModPaths(modPath, modParentPaths);
			throw e;
		}
	}

	private static FabricLoaderModMetadata readModMetadata(InputStream is) throws IOException, ParseMetadataException {
		// So some context:
		// Per the json specification, ordering of fields is not typically enforced.
		// Furthermore we cannot guarantee the `schemaVersion` is the first field in every `fabric.mod.json`
		//
		// To work around this, we do the following:
		// Try to read first field
		// If the first field is the schemaVersion, read the file normally.
		//
		// If the first field is not the schema version, fallback to a more exhaustive check.
		// Read the rest of the file, looking for the `schemaVersion` field.
		// If we find the field, cache the value
		// If there happens to be another `schemaVersion` that has a differing value, then fail.
		// At the end, if we find no `schemaVersion` then assume the `schemaVersion` is 0
		// Re-read the JSON file.

		// At least, that's what we used to do. However our json doesn't support rewinding,
		// so we'll just copy the entire buffer out to rewind.
		final byte[] srcData;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			// Transfer all bytes to the output
	        byte[] buffer = new byte[4096];
	        int read;
	        while ((read = is.read(buffer, 0, buffer.length)) >= 0) {
	            out.write(buffer, 0, read);
	        }
	        srcData = out.toByteArray();
		}
		int schemaVersion = 0;

		try (JsonReader reader = JsonReader.json(new InputStreamReader(new ByteArrayInputStream(srcData), StandardCharsets.UTF_8))) {

			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				throw new ParseMetadataException("Root of \"fabric.mod.json\" must be an object", reader);
			}

			reader.beginObject();

			boolean firstField = true;

			while (reader.hasNext()) {
				// Try to read the schemaVersion
				String nextName = reader.nextName();

				if (nextName.equals("schemaVersion")) {
					if (reader.peek() != JsonToken.NUMBER) {
						throw new ParseMetadataException("\"schemaVersion\" must be a number.", reader);
					}

					schemaVersion = reader.nextInt();

					if (firstField) {
						// Finish reading the metadata
						FabricLoaderModMetadata ret = readModMetadata(reader, schemaVersion);
						reader.endObject();

						return ret;
					} else {
						// schemaVersion found, but after some content -> start over to parse all data with the detected version
						break;
					}
				} else if (nextName.equals("$schema")) {
					reader.skipValue();
					// Avoid setting firstField to false, to prevent using the slow route when $schema is before schemaVersion
					continue;

				} else {
					reader.skipValue();
				}

				if (!IGNORED_KEYS.contains(nextName)) {
					firstField = false;
				}
			}
		}

		// Slow path, schema version wasn't specified early enough, re-read with detected/inferred version
		try (JsonReader reader = JsonReader.json(new InputStreamReader(new ByteArrayInputStream(srcData), StandardCharsets.UTF_8))) {
			// No need to check if the start of the json file as it has already been checked
			reader.beginObject();
			final FabricLoaderModMetadata ret = readModMetadata(reader, schemaVersion);
			reader.endObject();

			if (QuiltLoaderImpl.INSTANCE.isDevelopmentEnvironment()) {
				Log.warn(LogCategory.METADATA, "\"fabric.mod.json\" from mod %s did not have \"schemaVersion\" as first field.", ret.getId());
			}

			return ret;
		}
	}

	private static FabricLoaderModMetadata readModMetadata(JsonReader reader, int schemaVersion) throws IOException, ParseMetadataException {
		switch (schemaVersion) {
		case 1:
			return V1ModMetadataParser.parse(reader);
		case 0:
			return V0ModMetadataParser.parse(reader);
		default:
			if (schemaVersion > 0) {
				throw new ParseMetadataException(String.format("This version of fabric-loader doesn't support the newer schema version of \"%s\""
						+ "\nPlease update fabric-loader to be able to read this.", schemaVersion));
			}

			throw new ParseMetadataException(String.format("Invalid/Unsupported schema version \"%s\" was found", schemaVersion));
		}
	}

	private static void checkModId(String id, String name) throws ParseMetadataException {
		if (MOD_ID_PATTERN.matcher(id).matches()) return;

		List<String> errorList = new ArrayList<>();

		// A more useful error list for MOD_ID_PATTERN
		if (id.isEmpty()) {
			errorList.add("is empty!");
		} else {
			if (id.length() == 1) {
				errorList.add("is only a single character! (It must be at least 2 characters long)!");
			} else if (id.length() > 64) {
				errorList.add("has more than 64 characters!");
			}

			char first = id.charAt(0);

			if (first < 'a' || first > 'z') {
				errorList.add("starts with an invalid character '" + first + "' (it must be a lowercase a-z - uppercase isn't allowed anywhere in the ID)");
			}

			Set<Character> invalidChars = null;

			for (int i = 1; i < id.length(); i++) {
				char c = id.charAt(i);

				if (c == '-' || c == '_' || ('0' <= c && c <= '9') || ('a' <= c && c <= 'z')) {
					continue;
				}

				if (invalidChars == null) {
					invalidChars = new HashSet<>();
				}

				invalidChars.add(c);
			}

			if (invalidChars != null) {
				StringBuilder error = new StringBuilder("contains invalid characters: ");
				error.append(invalidChars.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", ")));
				errorList.add(error.append("!").toString());
			}
		}

		assert !errorList.isEmpty();

		StringWriter sw = new StringWriter();

		try (PrintWriter pw = new PrintWriter(sw)) {
			pw.printf("Invalid %s %s:", name, id);

			if (errorList.size() == 1) {
				pw.printf(" It %s", errorList.get(0));
			} else {
				for (String error : errorList) {
					pw.printf("\n\t- It %s", error);
				}
			}
		}

		throw new ParseMetadataException(sw.toString());
	}

	static void logWarningMessages(String id, List<ParseWarning> warnings) {
		if (warnings.isEmpty()) return;

		final StringBuilder message = new StringBuilder();

		message.append(String.format("Warnings for mod \"%s\"'s mod json:", id));

		for (ParseWarning warning : warnings) {
			message.append(String.format("\n- %s \"%s\" at %s",
					warning.getReason(), warning.getKey(), warning.getLocation()));
		}

		Log.warn(LogCategory.METADATA, message.toString());
	}

	private FabricModMetadataReader() {
	}
}
