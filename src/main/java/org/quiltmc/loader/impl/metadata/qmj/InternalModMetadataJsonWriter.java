/*
 * Copyright 2022, 2023 QuiltMC
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
import java.io.Writer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class InternalModMetadataJsonWriter {

	public static void write(InternalModMetadata mod, Writer writer) throws IOException {
		JsonWriter json = JsonWriter.json(writer);
		((JsonLoaderValue) toJson(mod)).write(json);
	}

	public static void write(InternalModMetadata mod, Path path) throws IOException {
		JsonWriter json = JsonWriter.json(path);
		((JsonLoaderValue) toJson(mod)).write(json);
	}

	public static LoaderValue toJson(InternalModMetadata mod) {
		// Put all the custom values (+more if QMJ impl). If this is empty then we cannot get the custom values anyway.
		Map<String, LoaderValue> values = new TreeMap<>((o1, o2) -> {
			// Force schema then quilt_loader
			if (o1.equals("schemaVersion")) {
				return -1;
			} else if (o2.equals("schemaVersion")) {
				return 1;
			}

			if (o1.equals("quilt_loader")) {
				return -1;
			} else if (o2.equals("quilt_loader")) {
				return 1;
			}

			return o1.compareTo(o2);
		});
		values.putAll(mod.values());

		if (!values.containsKey("schemaVersion")) {
			values.put("schemaVersion", new JsonLoaderValue.NumberImpl("", 1));
		}

		// Implementation does not have quilt_loader tag (likely FMJ wrapper)
		if (!values.containsKey("quilt_loader")) {
			Map<String, LoaderValue> quilt_loader = new LinkedHashMap<>();
			quilt_loader.put("id", string(mod.id()));
			if (!mod.group().equals("loader.fabric")) {
				quilt_loader.put("group", string(mod.group()));
			}
			quilt_loader.put("version", string(mod.version().raw()));
			quilt_loader.put("entrypoints",
					object(
							mod
									.getEntrypoints()
									.entrySet()
									.stream()
									.collect(
											Collectors.toMap(
													Map.Entry::getKey,
													entry -> array(entry.getValue().stream().map(entrypoint -> {
														if (entrypoint instanceof AdapterLoadableClassEntry) {
															AdapterLoadableClassEntry adapted = (AdapterLoadableClassEntry) entrypoint;
															if (adapted.getAdapter().equals("default")) {
																return string(adapted.getValue());
															} else {
																Map<String, LoaderValue> value = new LinkedHashMap<>();
																value.put("adapter", string(adapted.getAdapter()));
																value.put("value", string(adapted.getValue()));
																return object(value);
															}
														}
														return new JsonLoaderValue.NullImpl("");
													})))
									)
					)
			);
			quilt_loader.put("jars", array(mod.jars().stream().map(InternalModMetadataJsonWriter::string)));
			quilt_loader.put("language_adapters", object(mod.languageAdapters().entrySet().stream().collect(Collectors.toMap(
					Map.Entry::getKey,
					adapter -> string(adapter.getValue())
			))));
			quilt_loader.put("depends", array(mod.depends().stream().map(InternalModMetadataJsonWriter::modDependency)));
			quilt_loader.put("breaks", array(mod.breaks().stream().map(InternalModMetadataJsonWriter::modDependency)));
			quilt_loader.put("repositories", array(mod.repositories().stream().map(InternalModMetadataJsonWriter::string)));
			quilt_loader.put("load_type", string(mod.loadType().name().toLowerCase()));
			quilt_loader.put("provides", array(mod.provides().stream().map(provided -> {
				if (provided.version().equals(mod.version()) && !provided.id().contains(":")) {
					return string(provided.id());
				}

				Map<String, LoaderValue> value = new LinkedHashMap<>();
				value.put("id", string(provided.id().substring(provided.id().indexOf(":" + 1))));
				if (provided.id().contains(":")) {
					value.put("group", string(provided.id().substring(0, provided.id().indexOf(":"))));
				}
				value.put("version", string(provided.version().raw()));

				return object(value);
			})));
			quilt_loader.put("intermediate_mappings", string(mod.intermediateMappings()));
			// TODO: metadata

			values.put("quilt_loader", object(quilt_loader));
		}

		// TODO: Add Mixins
		// TODO: Add AW

		// TODO: Add Environment?

		return object(values);
	}

	@NotNull
	private static JsonLoaderValue array(Stream<LoaderValue> values) {
		return new JsonLoaderValue.ArrayImpl("", values.collect(Collectors.toList()));
	}

	@NotNull
	private static JsonLoaderValue array(List<LoaderValue> values) {
		return new JsonLoaderValue.ArrayImpl("", values);
	}

	@NotNull
	private static JsonLoaderValue object(Map<String, LoaderValue> quilt_loader) {
		return new JsonLoaderValue.ObjectImpl("", quilt_loader);
	}

	private static JsonLoaderValue modDependency(ModDependency dep) {
		if (dep instanceof ModDependency.Only) {
			ModDependency.Only only = (ModDependency.Only) dep;
			Map<String, LoaderValue> value = new LinkedHashMap<>();
			value.put("id", string(only.id().toString()));


			if (only.optional()) {
				value.put("optional", new JsonLoaderValue.BooleanImpl("", only.optional()));
			}
			if (!only.versionRange().equals(VersionRange.ANY)) {
				value.put("version", version(only.versionRange()));
			}
			if (!only.reason().isEmpty()) {
				value.put("reason", string(only.reason()));
			}
			if (only.unless() != null) {
				value.put("unless", modDependency(only.unless()));
			}

			if (value.size() == 1) {
				return (JsonLoaderValue) value.get("id");
			}

			return object(value);
		} else if (dep instanceof ModDependency.Any || dep instanceof ModDependency.All) {
			Collection<ModDependency.Only> deps = (Collection<ModDependency.Only>) dep;
			return array(deps.stream().map(InternalModMetadataJsonWriter::modDependency));
		}

		throw new IllegalStateException("Unknown Mod Dependency type! " + dep.getClass().getName());
	}

	@NotNull
	private static JsonLoaderValue string(String only) {
		return new JsonLoaderValue.StringImpl("", only);
	}

	// TODO: test this
	private static JsonLoaderValue version(VersionRange range) {
		if (range.equals(VersionRange.ANY)) {
			return string("*");
		}

		return array(range.convertToConstraints().stream().map(VersionConstraint::toString).map(InternalModMetadataJsonWriter::string));
	}
}
