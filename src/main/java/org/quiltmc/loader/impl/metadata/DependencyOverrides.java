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

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonToken;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import net.fabricmc.loader.api.metadata.ModDependency;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class DependencyOverrides {
	private static final Collection<String> ALLOWED_KEYS = new HashSet<>(Arrays.asList("depends", "recommends", "suggests", "conflicts", "breaks"));
	public static final DependencyOverrides INSTANCE = new DependencyOverrides();

	private final boolean exists;
	private final Map<String, Map<String, Map<String, ModDependency>>> dependencyOverrides;

	private DependencyOverrides() {
		Path path = QuiltLoaderImpl.INSTANCE.getConfigDir().resolve("fabric_loader_dependencies.json");
		exists = Files.exists(path);

		if (!exists) {
			dependencyOverrides = Collections.emptyMap();
			return;
		}

		try (JsonReader reader = JsonReader.createStrict(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
			dependencyOverrides = parse(reader);
		} catch (IOException | ParseMetadataException e) {
			throw new RuntimeException("Failed to parse " + path.toString(), e);
		}
	}

	private static Map<String, Map<String, Map<String, ModDependency>>> parse(JsonReader reader) throws ParseMetadataException, IOException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseMetadataException("Root must be an object", reader);
		}

		Map<String, Map<String, Map<String, ModDependency>>> dependencyOverrides = new HashMap<>();
		reader.beginObject();

		if (!reader.nextName().equals("version")) {
			throw new ParseMetadataException("First key must be \"version\"", reader);
		}

		if (reader.peek() != JsonToken.NUMBER || reader.nextInt() != 1) {
			throw new ParseMetadataException("Unsupported \"version\", must be 1", reader);
		}

		while (reader.hasNext()) {
			String key = reader.nextName();

			if ("overrides".equals(key)) {
				reader.beginObject();

				while (reader.hasNext()) {
					String modId = reader.nextName();

					dependencyOverrides.put(modId, readKeys(reader));
				}

				reader.endObject();
			} else {
				throw new ParseMetadataException("Unsupported root key: " + key, reader);
			}
		}

		reader.endObject();
		return dependencyOverrides;
	}

	private static Map<String, Map<String, ModDependency>> readKeys(JsonReader reader) throws IOException, ParseMetadataException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseMetadataException("Dependency container must be an object!", reader);
		}

		Map<String, Map<String, ModDependency>> containersMap = new HashMap<>();
		reader.beginObject();

		while (reader.hasNext()) {
			String key = reader.nextName();

			if (!ALLOWED_KEYS.contains(key.replaceAll("^[+-]", ""))) {
				throw new ParseMetadataException(key + " is not an allowed dependency key, must be one of: " + String.join(", ", ALLOWED_KEYS), reader);
			}

			containersMap.put(key, readDependenciesContainer(reader));
		}

		reader.endObject();
		return containersMap;
	}

	private static Map<String, ModDependency> readDependenciesContainer(JsonReader reader) throws IOException, ParseMetadataException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseMetadataException("Dependency container must be an object!", reader);
		}

		Map<String, ModDependency> modDependencyMap = new HashMap<>();
		reader.beginObject();

		while (reader.hasNext()) {
			final String modId = reader.nextName();
			final List<String> matcherStringList = new ArrayList<>();

			switch (reader.peek()) {
				case STRING:
					matcherStringList.add(reader.nextString());
					break;
				case BEGIN_ARRAY:
					reader.beginArray();

					while (reader.hasNext()) {
						if (reader.peek() != JsonToken.STRING) {
							throw new ParseMetadataException("Dependency version range array must only contain string values", reader);
						}

						matcherStringList.add(reader.nextString());
					}

					reader.endArray();
					break;
				default:
					throw new ParseMetadataException("Dependency version range must be a string or string array!", reader);
			}

			modDependencyMap.put(modId, new ModDependencyImpl(modId, matcherStringList));
		}

		reader.endObject();
		return modDependencyMap;
	}

	public Map<String, ModDependency> getActiveDependencyMap(String key, String modId, Map<String, ModDependency> defaultMap) {
		if(!exists) {
			return defaultMap;
		}

		Map<String, Map<String, ModDependency>> modOverrides = dependencyOverrides.get(modId);

		if (modOverrides == null) {
			// No overrides return the default
			return defaultMap;
		}

		Map<String, ModDependency> override = modOverrides.get(key);

		if (override != null) {
			return Collections.unmodifiableMap(override);
		}

		Map<String, ModDependency> removals = modOverrides.get("-" + key);
		Map<String, ModDependency> additions = modOverrides.get("+" + key);

		if (additions == null && removals == null) {
			return defaultMap;
		}

		Map<String, ModDependency> modifiedMap = new HashMap<>(defaultMap);

		if (removals != null) {
			removals.keySet().forEach(modifiedMap::remove);
		}

		if (additions != null) {
			modifiedMap.putAll(additions);
		}

		return Collections.unmodifiableMap(modifiedMap);
	}

	public Map<String, Map<String, Map<String, ModDependency>>> getDependencyOverrides() {
		return dependencyOverrides;
	}
}
