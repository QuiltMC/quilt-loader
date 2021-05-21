package org.quiltmc.loader.impl.metadata.qmj;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.loader.api.ModLicense;

/**
 * Utilities for obtaining SPDX license metadata from the SPDX json database of licenses.
 *
 * The licenses.json should be updated once in a while and is located here: https://spdx.org/licenses/licenses.json.
 */
final class SPDXModLicenseReader {
	/**
	 * Reads the licenses from json.
	 *
	 * @param reader the reader
	 * @return the licenses or an empty map if there were significant parsing issues
	 */
	static Map<String, ModLicense> readLicenses(JsonReader reader) throws IOException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			return Collections.emptyMap();
		}

		reader.beginObject();

		Map<String, ModLicense> licenses = new LinkedHashMap<>();

		while (reader.hasNext()) {
			if (reader.nextName().equals("licenses")) {
				if (reader.peek() != JsonToken.BEGIN_ARRAY) {
					return Collections.emptyMap();
				}

				reader.beginArray();

				while (reader.hasNext()) {
					if (reader.peek() != JsonToken.BEGIN_OBJECT) {
						// Rest of the entries should be fine if this is malformed.
						continue;
					}

					reader.beginObject();

					@Nullable
					Map.Entry<String, ModLicense> entry = readLicense(reader);

					reader.endObject();

					if (entry != null) {
						licenses.put(entry.getKey(), entry.getValue());
					}
				}

				reader.endArray();
			} else {
				reader.skipValue();
			}
		}

		reader.endObject();

		return licenses;
	}

	/**
	 * Reads a license entry from json.
	 *
	 * @param reader the reader
	 * @return the license, null if there were any missing elements.
	 */
	@Nullable
	static Map.Entry<String, ModLicense> readLicense(JsonReader reader) throws IOException {
		String id = null;
		String name = null;
		String url = null;

		while (reader.hasNext()) {
			switch (reader.nextName()) {
			// The reference url should be good enough as the reference contains the license text.
			case "reference":
				url = reader.nextString();
				break;
			// A human readable name.
			case "name":
				name = reader.nextString();
				break;
			// The actual SPDX identifier.
			case "licenseId":
				id = reader.nextString();
				break;
			default:
				reader.skipValue();
			}
		}

		if (id != null && name != null && url != null) {
			return new AbstractMap.SimpleImmutableEntry<>(id, new ModLicenseImpl(name, id, url, ""));
		}

		return null;
	}

	private SPDXModLicenseReader() {
	}
}
