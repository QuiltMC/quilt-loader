/*
 * Copyright 2024 QuiltMC
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
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.quiltmc.json5.JsonWriter;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModEnvironment;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class InternalModMetadataJsonWriter {
	private static final List<String> SPECIFIC_KEY_ORDER = Stream.of("schema_version", "quilt_loader", "mixin", "access_widener", "minecraft").collect(Collectors.toList());

	public static void write(InternalModMetadata mod, Writer writer) throws IOException {
		JsonWriter json = JsonWriter.json(writer);
		((JsonLoaderValue) toJson(mod)).write(json);
	}

	public static void write(InternalModMetadata mod, Path path) throws IOException {
		JsonWriter json = JsonWriter.json(path);
		((JsonLoaderValue) toJson(mod)).write(json);
	}

	public static LoaderValue toJson(InternalModMetadata mod) {
		Map<String, LoaderValue> values = new TreeMap<>((o1, o2) -> {
			if (o1.equals(o2)) {
				return 0;
			}

			for (String key : SPECIFIC_KEY_ORDER) {
				if (o1.equals(key)) {
					return -1;
				} else if (o2.equals(key)) {
					return 1;
				}
			}

			return o1.compareTo(o2);
		});

		values.put("schema_version", new JsonLoaderValue.NumberImpl("", 1));
		values.put("quilt_loader", createQuiltLoader(mod));

		LoaderValue mixin = createMixins(mod);
		if (mixin != null) {
			values.put("mixin", mixin);
		}

		LoaderValue accessWidener = createAccessWidener(mod);
		if (accessWidener != null) {
			values.put("access_widener", accessWidener);
		}

		LoaderValue minecraft = createMinecraft(mod);
		if (minecraft != null) {
			values.put("minecraft", minecraft);
		}

		// Put all the custom values (+more if QMJ impl). If this is empty then we cannot get the custom values anyway.
		mod.values().forEach((s, loaderValue) -> {
			if (!values.containsKey(s)) {
				values.put(s, loaderValue);
			}
		});

		return object(values);
	}

	private static LoaderValue createQuiltLoader(InternalModMetadata mod) {
		return object(quiltLoader -> {

			quiltLoader.put("id", string(mod.id()));
			// This is required but there isn't a good way to set it without knowing
			quiltLoader.put("group", string(mod.group()));
			quiltLoader.put("version", string(mod.version().raw()));

			quiltLoader.put("metadata", createMetadata(mod));

			putEmptyMap("entrypoints", mod.getEntrypoints(), k -> k, InternalModMetadataJsonWriter::entrypoints, quiltLoader);

			putEmptyCollection("depends", mod.depends(), InternalModMetadataJsonWriter::modDependency, quiltLoader);
			putEmptyCollection("breaks", mod.breaks(), InternalModMetadataJsonWriter::modDependency, quiltLoader);

			putEmptyCollection("provides", mod.provides(), InternalModMetadataJsonWriter.provided(mod), quiltLoader);

			if (mod.loadType() != ModMetadataExt.ModLoadType.IF_REQUIRED) {
				quiltLoader.put("load_type", string(mod.loadType().name().toLowerCase()));
			}

			if (!mod.intermediateMappings().equals("org.quiltmc:hashed")) {
				quiltLoader.put("intermediate_mappings", string(mod.intermediateMappings()));
			}

			putEmptyCollection("jars", mod.jars(), InternalModMetadataJsonWriter::string, quiltLoader);
			putEmptyCollection("repositories", mod.repositories(), InternalModMetadataJsonWriter::string, quiltLoader);

			putEmptyMap("language_adapters", mod.languageAdapters(), k -> k, InternalModMetadataJsonWriter::string, quiltLoader);
		});
	}

	private static JsonLoaderValue entrypoints(Collection<ModMetadataExt.ModEntrypoint> entrypoints) {
		return array(entrypoints.stream().map(entrypoint -> {
			if (entrypoint instanceof AdapterLoadableClassEntry) {
				AdapterLoadableClassEntry adapted = (AdapterLoadableClassEntry) entrypoint;
				if (adapted.getAdapter().equals("default")) {
					return string(adapted.getValue());
				}

				return object(value -> {
					value.put("adapter", string(adapted.getAdapter()));
					value.put("value", string(adapted.getValue()));
				});
			}

			throw new IllegalStateException("Unknown Mod Dependency type! " + entrypoint.getClass().getName());
		}));
	}

	private static JsonLoaderValue modDependency(ModDependency dep) {
		if (dep instanceof ModDependency.Only) {
			ModDependency.Only only = (ModDependency.Only) dep;
			JsonLoaderValue.ObjectImpl obj = object(value -> {
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
			});

			if (obj.size() == 1) {
				return obj.get("id");
			}

			return obj;
		} else if (dep instanceof ModDependency.Any || dep instanceof ModDependency.All) {
			Collection<ModDependency.Only> deps = (Collection<ModDependency.Only>) dep;
			return array(deps.stream().map(InternalModMetadataJsonWriter::modDependency));
		}

		throw new IllegalStateException("Unknown Mod Dependency type! " + dep.getClass().getName());
	}

	private static JsonLoaderValue version(VersionRange range) {
		// TODO: test this
		if (range.equals(VersionRange.ANY)) {
			return string("*");
		}

		return array(range.convertToConstraints().stream().map(VersionConstraint::toString).map(InternalModMetadataJsonWriter::string));
	}

	private static <P extends ModMetadata.ProvidedMod> Function<P, LoaderValue> provided(InternalModMetadata mod) {
		return provided -> {
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
		};
	}

	private static JsonLoaderValue createMetadata(InternalModMetadata mod) {
		return object(metadata -> {
			metadata.put("name", string(mod.name()));
			if (!mod.description().isEmpty()) {
				metadata.put("description", string(mod.description()));
			}

			putEmptyMap("contributors", mod.contributors(), ModContributor::name, contributor -> {
				if (contributor.roles().size() == 1) {
					return string(contributor.roles().iterator().next());
				}
				return array(contributor.roles().stream().map(InternalModMetadataJsonWriter::string));
			}, metadata);

			putEmptyMap("contact", mod.contactInfo(), Function.identity(), InternalModMetadataJsonWriter::string, metadata);

			// Special case a single license
			if (mod.licenses().size() == 1) {
				ModLicense license = mod.licenses().iterator().next();
				metadata.put("licence", createLicense(license));
			} else {
				putEmptyCollection("license", mod.licenses(), InternalModMetadataJsonWriter::createLicense, metadata);
			}


			LoaderValue iconJson = getIcon(mod);
			if (iconJson != null) {
				metadata.put("icon", iconJson);
			}
		});
	}

	private static JsonLoaderValue createLicense(ModLicense license) {
		// Object equality here is fine because it comes from a map
		if (ModLicense.fromIdentifier(license.id()) == license) {
			return string(license.id());
		}

		return object(licenseObj -> {
			licenseObj.put("name", string(license.name()));
			licenseObj.put("id", string(license.id()));
			licenseObj.put("url", string(license.url()));
			licenseObj.put("description", string(license.description()));
		});
	}

	private static LoaderValue getIcon(InternalModMetadata mod) {
		// Cursed time!
		if (mod instanceof V1ModMetadataImpl) {
			try {
				Field iconsField = mod.getClass().getDeclaredField("icons");
				iconsField.setAccessible(true);
				Icons icons = (Icons) iconsField.get(mod);
				if (icons instanceof Icons.Single) {
					String icon = icons.getIcon(0);
					if (icon == null) {
						return null;
					}

					return string(icon);
				} else if (icons instanceof Icons.Multiple) {
					Field mapField = Icons.Multiple.class.getDeclaredField("icons");
					mapField.setAccessible(true);
					SortedMap<Integer, String> map = (SortedMap<Integer, String>) mapField.get(icons);
					return object(obj -> map.forEach((size, icon) -> obj.put(Integer.toString(size), string(icon))));
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else if (mod instanceof FabricModMetadataWrapper) {
			FabricLoaderModMetadata fabric = mod.asFabricModMetadata();
			if (fabric.getClass().getSimpleName().equals("V1ModMetadataFabric")) {
				try {
					// V1ModMetadataFabric.IconEntry
					Field iconField = fabric.getClass().getDeclaredField("icon");
					iconField.setAccessible(true);
					Object icons = iconField.get(fabric);

					if (icons.getClass().getName().contains("Single")) {
						Field stringField = icons.getClass().getDeclaredField("icon");
						stringField.setAccessible(true);
						return string((String) stringField.get(icons));
					} else if (icons.getClass().getName().contains("MapEntry")) {
						Field iconsField = icons.getClass().getDeclaredField("icons");
						iconsField.setAccessible(true);
						SortedMap<Integer, String> map = (SortedMap<Integer, String>) iconsField.get(icons);
						return object(obj -> map.forEach((size, icon) -> obj.put(Integer.toString(size), string(icon))));
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		return null;
	}

	private static LoaderValue createMixins(InternalModMetadata mod) {
		Set<String> client = new HashSet<>(mod.mixins(EnvType.CLIENT));
		Set<String> server = new HashSet<>(mod.mixins(EnvType.SERVER));

		if (client.isEmpty() && server.isEmpty()) {
			return null;
		}

		Set<String> shared = client.stream().filter(server::contains).collect(Collectors.toSet());

		LoaderValue mixin;
		if (client.size() == 1 && server.size() == 1 && shared.size() == 1) { // Client and server both have the same entry
			mixin = string(shared.iterator().next());
		} else if (client.size() == 1 && server.isEmpty()) { // Client only mixin
			mixin = mixinObject(client.iterator().next(), "client");
		} else if (server.size() == 1 && client.isEmpty()) { // Server only mixin
			mixin = mixinObject(server.iterator().next(), "dedicated_server");
		} else { // We know we have an array
			List<LoaderValue> mixins = new ArrayList<>();
			for (String config : shared) {
				mixins.add(string(config));
			}

			for (String config : client) {
				if (!shared.contains(config)) {
					mixins.add(mixinObject(config, "client"));
				}
			}

			for (String config : server) {
				if (!shared.contains(config)) {
					mixins.add(mixinObject(config, "dedicated_server"));
				}
			}

			mixin = array(mixins);
		}

		return mixin;
	}

	private static JsonLoaderValue mixinObject(String config, String env) {
		return object(objectValues -> {
			objectValues.put("config", string(config));
			objectValues.put("environment", string(env));
		});
	}

	private static LoaderValue createAccessWidener(InternalModMetadata mod) {
		if (mod.accessWideners().isEmpty()) {
			return null;
		}

		if (mod.accessWideners().size() == 1) {
			return string(mod.accessWideners().iterator().next());
		} else {
			return array(mod.accessWideners().stream().map(InternalModMetadataJsonWriter::string));
		}
	}

	private static LoaderValue createMinecraft(InternalModMetadata mod) {
		if (mod.environment().equals(ModEnvironment.UNIVERSAL)) {
			return null;
		}

		return object(minecraft -> {
			switch (mod.environment()) {
				case CLIENT:
					minecraft.put("environment", string("client"));
					break;
				case SERVER:
					minecraft.put("environment", string("dedicated_server"));
					break;
			}
		});
	}

	private static <T> void putEmptyCollection(String field, Collection<T> collection, Function<T, ? extends LoaderValue> converter, Map<String, LoaderValue> object) {
		if (!collection.isEmpty()) {
			object.put(field, array(collection.stream().map(converter)));
		}
	}

	private static <K, V> void putEmptyMap(String field, Map<K, V> map, Function<K, String> key, Function<V, ? extends LoaderValue> value, Map<String, LoaderValue> object) {
		if (!map.isEmpty()) {
			object.put(field, object(obj -> map.forEach((k, v) -> obj.put(key.apply(k), value.apply(v)))));
		}
	}

	private static <T> void putEmptyMap(String field, Collection<T> map, Function<T, String> key, Function<T, ? extends LoaderValue> value, Map<String, LoaderValue> object) {
		if (!map.isEmpty()) {
			object.put(field, object(obj -> map.forEach((t) -> obj.put(key.apply(t), value.apply(t)))));
		}
	}

	private static JsonLoaderValue.StringImpl string(String only) {
		return new JsonLoaderValue.StringImpl("", only);
	}

	private static JsonLoaderValue.ArrayImpl array(List<LoaderValue> values) {
		return new JsonLoaderValue.ArrayImpl("", values);
	}

	private static JsonLoaderValue.ArrayImpl array(Stream<LoaderValue> values) {
		return array(values.collect(Collectors.toList()));
	}

	private static JsonLoaderValue.ObjectImpl object(Map<String, LoaderValue> values) {
		return new JsonLoaderValue.ObjectImpl("", values);
	}

	private static JsonLoaderValue.ObjectImpl object(Consumer<Map<String, LoaderValue>> consumer) {
		Map<String, LoaderValue> values = new LinkedHashMap<>();
		consumer.accept(values);
		return object(values);
	}
}
