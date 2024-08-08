/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

import static org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader.parseException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import net.fabricmc.api.EnvType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LType;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionFormatException;
import org.quiltmc.loader.api.VersionRange;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModEntrypoint;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModLoadType;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode.WarningLevel;
import org.quiltmc.loader.impl.metadata.qmj.JsonLoaderValue.ObjectImpl;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

// TODO: Figure out a way to not need to always specify JsonLoaderValue everywhere so we can let other users and plugins have location data.
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class V1ModMetadataReader {
	public static V1ModMetadataImpl read(JsonLoaderValue.ObjectImpl root) {
		return read(root, null, null, null);
	}

	public static V1ModMetadataImpl read(JsonLoaderValue.ObjectImpl root, Path path, QuiltPluginManager manager, PluginGuiTreeNode parentNode) {
		// Read loader category
		@Nullable JsonLoaderValue quiltLoader = root.get("quilt_loader");

		if (quiltLoader == null) {
			throw new ParseException("quilt_loader field is required");
		}

		if (quiltLoader.type() != LoaderValue.LType.OBJECT) {
			throw parseException(quiltLoader, "quilt_loader field must be an object");
		}

		return new V1ModMetadataReader(path, manager, parentNode).readFields(root);
	}

	private static class QLKeys {
		static final Set<String> VALID_KEYS = new HashSet<>();

		static final String ID = add("id");
		static final String GROUP = add("group");
		static final String VERSION = add("version");
		static final String ENTRYPOINTS = add("entrypoints");
		static final String JARS = add("jars");
		static final String LANGUAGE_ADAPTERS = add("language_adapters");
		static final String DEPENDS = add("depends");
		static final String BREAKS = add("breaks");
		static final String REPOSITORIES = add("repositories");
		static final String LOAD_TYPE = add("load_type");
		static final String PROVIDES = add("provides");
		static final String INTERMEDIATE_MAPPINGS = add("intermediate_mappings");
		static final String METADATA = add("metadata");

		private static String add(String str) {
			QLKeys.VALID_KEYS.add(str);
			return str;
		}
	}

	final Path from;
	final QuiltPluginManager manager;
	final PluginGuiTreeNode warningNode;
	PluginGuiTreeNode modJsonNode = null;
	boolean loggedAnyWarnings = false;
	boolean loggedDeprecatedArrayDepends = false;

	private V1ModMetadataReader(Path from, QuiltPluginManager manager, PluginGuiTreeNode warningNode) {
		this.from = from;
		this.manager = manager;
		this.warningNode = warningNode;
	}

	private V1ModMetadataImpl readFields(JsonLoaderValue.ObjectImpl root) {
		V1ModMetadataBuilder builder = new V1ModMetadataBuilder();
		builder.setRoot(root);

		JsonLoaderValue.ObjectImpl quiltLoader = (JsonLoaderValue.ObjectImpl) root.get("quilt_loader");

		if (quiltLoader == null) {
			throw parseException(root, "quilt_loader is a required field");
		}

		if (QuiltLoader.isDevelopmentEnvironment() && !Boolean.getBoolean(SystemProperties.DISABLE_STRICT_PARSING)) {
			for (String s : quiltLoader.keySet()) {
				if (!QLKeys.VALID_KEYS.contains(s)) {
					throw parseException(Objects.requireNonNull(quiltLoader.get(s)), "Invalid key " + s + " in the quilt_loader object! (To disable this message, " +
							"use the argument -D" + SystemProperties.DISABLE_STRICT_PARSING + "=true)");
				}
			}
		}

		// Loader metadata
		{
			// Check if our required fields are here
			builder.id = requiredString(quiltLoader, QLKeys.ID);

			if (!Patterns.VALID_MOD_ID.matcher(builder.id).matches()) {
				// id must be non-null
				throw parseException(Objects.requireNonNull(quiltLoader.get("id")), "Invalid mod id, likely one of the following errors:\n" +
						"- Mod id contains invalid characters, the allowed characters are a-z 0-9 _-\n" +
						"- The mod id is too short or long, the mod id must be between 2 and 63 characters");
			}

			builder.group = requiredString(quiltLoader, QLKeys.GROUP);

			if (!Patterns.VALID_MAVEN_GROUP.matcher(builder.group).matches()) {
				// group must be non-null
				throw parseException(Objects.requireNonNull(quiltLoader.get("id")), "Invalid mod maven group; the allowed characters are a-z A-Z 0-9 - _ and .");
			}

			// Versions
			@Nullable JsonLoaderValue versionValue = quiltLoader.get(QLKeys.VERSION);

			if (versionValue == null) {
				throw new ParseException("version is a required field");
			}

			// TODO: Here we would check if the version is a placeholder in dev.

			builder.version = Version.of(versionValue.asString());
			// Now we reach optional fields

			@Nullable
			JsonLoaderValue entrypointsValue = quiltLoader.get(QLKeys.ENTRYPOINTS);

			if (entrypointsValue != null) {
				if (entrypointsValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(entrypointsValue, "entrypoints must be an object");
				}

				readEntrypoints((JsonLoaderValue.ObjectImpl) entrypointsValue, QLKeys.ENTRYPOINTS, builder.entrypoints);
			}

			@Nullable
			JsonLoaderValue jarsValue = quiltLoader.get(QLKeys.JARS);

			if (jarsValue != null) {
				if (jarsValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(jarsValue, "jars must be an array");
				}

				readStringList((JsonLoaderValue.ArrayImpl) jarsValue, QLKeys.JARS, builder.jars);
			}

			@Nullable
			JsonLoaderValue languageAdaptersValue = quiltLoader.get(QLKeys.LANGUAGE_ADAPTERS);

			if (languageAdaptersValue != null) {
				if (languageAdaptersValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(languageAdaptersValue, "language_adapters must be an object");
				}

				readStringMap((JsonLoaderValue.ObjectImpl) languageAdaptersValue, QLKeys.LANGUAGE_ADAPTERS, builder.languageAdapters);
			}

			@Nullable JsonLoaderValue dependsValue = assertType(quiltLoader, QLKeys.DEPENDS, LoaderValue.LType.ARRAY);
			if (dependsValue != null) {
				for (LoaderValue v : dependsValue.asArray()) {
					builder.depends.add(readDependencyObject(true, (JsonLoaderValue) v));
				}
			}

			@Nullable JsonLoaderValue breaksValue = assertType(quiltLoader, QLKeys.BREAKS, LoaderValue.LType.ARRAY);
			if (breaksValue != null) {
				for (LoaderValue v : breaksValue.asArray()) {
					builder.breaks.add(readDependencyObject(false, (JsonLoaderValue) v));
				}
			}

			@Nullable
			JsonLoaderValue repositoriesValue = quiltLoader.get(QLKeys.REPOSITORIES);

			if (repositoriesValue != null) {
				if (repositoriesValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(repositoriesValue, "repositories must be an array");
				}

				readStringList((JsonLoaderValue.ArrayImpl) repositoriesValue, QLKeys.REPOSITORIES, builder.repositories);
			}

			@Nullable
			JsonLoaderValue loadTypeValue = quiltLoader.get(QLKeys.LOAD_TYPE);

			if (loadTypeValue != null) {
				if (loadTypeValue.type() != LoaderValue.LType.STRING) {
					throw parseException(loadTypeValue, "load_type must be a string");
				}

				builder.loadType = readLoadType((JsonLoaderValue.StringImpl) loadTypeValue);
			}

			@Nullable
			JsonLoaderValue providesValue = quiltLoader.get(QLKeys.PROVIDES);

			if (providesValue != null) {
				if (providesValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(providesValue, "provides must be an array");
				}

				for (LoaderValue provided : providesValue.asArray()) {
					if (provided.type() == LoaderValue.LType.STRING) {
						String providedId = provided.asString();
						String providedGroup = builder.group;
						int colon = providedId.indexOf(':');
						if (colon > 0) {
							providedGroup = providedId.substring(0, colon);
							providedId = providedId.substring(colon + 1);
						}
						builder.provides.add(new ProvidedModImpl(providedGroup, providedId, builder.version));
					} else if (provided.type() == LType.OBJECT) {
						JsonLoaderValue.ObjectImpl providedObj = (JsonLoaderValue.ObjectImpl) provided.asObject();

						String providedId = requiredString(providedObj, "id");
						String providedGroup = builder.group;
						int colon = providedId.indexOf(':');
						if (colon > 0) {
							providedGroup = providedId.substring(0, colon);
							providedId = providedId.substring(colon + 1);
						}

						Version providedVersion = builder.version;

						if (providedObj.containsKey("version")) {
							providedVersion = Version.of(requiredString(providedObj, "version"));
						}

						builder.provides.add(new ProvidedModImpl(providedGroup, providedId, providedVersion));
					} else {
						throw parseException((JsonLoaderValue) provided, "provides must be an array containing only objects and/or strings");
					}
				}
			}

			@Nullable
			JsonLoaderValue intermediateMappingsValue = quiltLoader.get(QLKeys.INTERMEDIATE_MAPPINGS);

			String[] supported_mappings = { "org.quiltmc:hashed", "net.fabricmc:intermediary" };
			String mappings = "org.quiltmc:hashed";

			if (intermediateMappingsValue != null) {
				if (intermediateMappingsValue.type() != LoaderValue.LType.STRING) {
					throw parseException(intermediateMappingsValue, "intermediate_mappings must be a string");
				}

				mappings = intermediateMappingsValue.asString();

				if (!Patterns.VALID_INTERMEDIATE.matcher(mappings).matches()) {
					throw parseException(intermediateMappingsValue, "intermediate_mappings must be a valid maven coordinate");
				}

				if (!Arrays.asList(supported_mappings).contains(mappings)) {
					throw parseException(intermediateMappingsValue, "unknown intermediate mappings");
				}
			}

			// Until Loader supports hashed mappings
			if (mappings.equals("org.quiltmc:hashed")) {
				throw new ParseException("Oh no! This version of Quilt Loader doesn't support hashed mappings, please update Quilt Loader to use this mod.");
			}

			builder.intermediateMappings = mappings;

			// Metadata
			JsonLoaderValue metadataValue = quiltLoader.get(QLKeys.METADATA);

			if (metadataValue != null) {
				if (metadataValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(metadataValue, "metadata must be an object");
				}

				JsonLoaderValue.ObjectImpl metadata = (JsonLoaderValue.ObjectImpl) metadataValue;

				builder.name = string(metadata, "name");
				builder.description = string(metadata, "description");

				@Nullable
				JsonLoaderValue contributorsValue = metadata.get("contributors");
				if (contributorsValue != null) {
					if (contributorsValue.type() != LType.OBJECT) {
						throw parseException(contributorsValue, "contributors must be an object");
					}
					Map<String, List<String>> intermediate = new LinkedHashMap<>();
					readStringToStringOrListMap(contributorsValue.asObject(), "contributors", intermediate);
					intermediate.forEach((k, v) -> builder.contributors.add(new ModContributorImpl(k, v)));
				}

				@Nullable
				JsonLoaderValue contact = metadata.get("contact");

				if (contact != null) {
					if (contact.type() != LoaderValue.LType.OBJECT) {
						throw parseException(contact, "contact must be an object");
					}

					readStringMap(contact.asObject(), "contact", builder.contactInformation);
				}

				readLicenses(metadata, builder.licenses);

				@Nullable
				JsonLoaderValue iconValue = metadata.get("icon");
				if (iconValue != null) {
					if (iconValue.type() == LType.STRING) {
						builder.icons = new Icons.Single(iconValue.asString());
					} else if (iconValue.type() == LType.OBJECT) {
						SortedMap<Integer, String> map = new TreeMap<>();
						readIntToStringMap(iconValue.asObject(), "icon", map);
						builder.icons = new Icons.Multiple(map);
					}
				}
			}
		}

		{
			// FIXME: These entries need to be moved when plugins are ready
			// TODO: Move mixin parsing to a plugin
			@Nullable
			JsonLoaderValue mixinValue = root.get("mixin");

			if (mixinValue != null) {
				switch (mixinValue.type()) {
				case ARRAY:
					readMixins((JsonLoaderValue.ArrayImpl) mixinValue, builder.mixins);
					break;
				case STRING:
					builder.mixins.computeIfAbsent(EnvType.CLIENT, (env) -> new ArrayList<>()).add(mixinValue.asString());
					builder.mixins.computeIfAbsent(EnvType.SERVER, (env) -> new ArrayList<>()).add(mixinValue.asString());
					break;
				case OBJECT:
					readMixin(mixinValue.asObject(), builder.mixins);
					break;
				default:
					throw parseException(mixinValue, "mixin value must be a string, a mixin entry, or a mixed array of either");
				}
			}

			@Nullable JsonLoaderValue mcValue = root.get("minecraft");
			if (mcValue != null) {
				ObjectImpl object = mcValue.asObject();
				String env = string(object, "environment");
				switch (env) {
					case "client":
						builder.env = ModEnvironment.CLIENT;
						break;
					case "dedicated_server":
						builder.env = ModEnvironment.SERVER;
						break;
					case "":
					case "*":
						builder.env = ModEnvironment.UNIVERSAL;
						break;
					default:
						throw parseException(object.get("environment"), env +
								" is not a valid environment. Valid options are \"*\", \"client\", or \"dedicated_server\"");
				}
			}

			@Nullable
			JsonLoaderValue awValue = root.get("access_widener");
			if (awValue != null) {
				switch (awValue.type()) {
					case ARRAY:
						readStringList((JsonLoaderValue.ArrayImpl) awValue, "access_widener", builder.accessWideners);
						break;
					case STRING:
						builder.accessWideners.add(awValue.asString());
						break;
					default:
						throw parseException(awValue, "mixin value must be an array of strings or a string");
				}
			}

		}

		return new V1ModMetadataImpl(builder);
	}

	private static String requiredString(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			throw parseException(object, String.format("%s is a required field", field));
		}

		if (value.type() != LoaderValue.LType.STRING) {
			throw parseException(value, String.format("%s must be a string", field));
		}

		return value.asString();
	}

	private static JsonLoaderValue requiredField(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			throw parseException(object, String.format("%s is a required field", field));
		} else {
			return value;
		}
	}

	@NotNull
	private static String string(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			return "";
		}

		if (value.type() != LoaderValue.LType.STRING) {
			throw parseException(value, String.format("%s must be a string", field));
		}

		return value.asString();
	}

	private static boolean bool(ObjectImpl object, String field, boolean _default) {
		JsonLoaderValue value = object.get(field);
		
		if (value == null) {
			return _default;
		}
		
		if (value.type() != LoaderValue.LType.BOOLEAN) {
			throw parseException(value, String.format("%s must be a boolean", field));
		}
		
		return value.asBoolean();
	}

	@Nullable
	private static JsonLoaderValue assertType(JsonLoaderValue.ObjectImpl object, String field, LoaderValue.LType type) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			return null;
		}

		if (value.type() != type) {
			throw parseException(object, String.format("%s must be of type %s", field, type));
		}

		return value;
	}

	private static ModLoadType readLoadType(JsonLoaderValue.StringImpl value) {
		switch (value.asString()) {
			case "always":
				return ModLoadType.ALWAYS;
			case "if_possible": 
				return ModLoadType.IF_POSSIBLE;
			case "if_required":
				return ModLoadType.IF_REQUIRED;
			default:
				throw parseException(value, "load_type must be either 'always', 'if_possible', or 'if_required', but got '" + value.asString() + "'");
		}
	}

	/**
	 * Read an array as a list of strings.
	 *
	 * @param array array to read from
	 * @param inside the name of the array field
	 * @param destination the list to add the strings to
	 * @throws ParseException if any entry is not a string
	 */
	private static void readStringList(JsonLoaderValue.ArrayImpl array, String inside, List<String> destination) {
		for (LoaderValue value : array) {
			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("Entry inside %s must be a string", inside));
			}

			destination.add(value.asString());
		}
	}

	private static void readStringMap(JsonLoaderValue.ObjectImpl object, String inside, Map<String, String> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String key = entry.getKey();
			LoaderValue value = entry.getValue();

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("entry with key %s inside \"%s\" must be a string", key, inside));
			}

			if (destination.put(key, value.asString()) != null) {
				// TODO: Warn in dev environment about duplicate keys
			}
		}
	}

	private static void readStringToStringOrListMap(JsonLoaderValue.ObjectImpl object, String inside, Map<String, List<String>> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String key = entry.getKey();
			LoaderValue value = entry.getValue();
			List<String> result = new ArrayList<>();

			if (value.type() == LoaderValue.LType.STRING) {
				result.add(value.asString());
			} else if (value.type() == LoaderValue.LType.ARRAY) {
				readStringList((JsonLoaderValue.ArrayImpl) value.asArray(), key, result);
			} else {
				throw parseException((JsonLoaderValue) value, String.format("entry with key %s inside \"%s\" must be a string or array of strings", key, inside));
			}

			if (destination.put(key, result) != null) {
				// TODO: Warn in dev environment about duplicate keys
			}
		}
	}

	private static void readIntToStringMap(JsonLoaderValue.ObjectImpl object, String inside, Map<Integer, String> destination) {
		object.forEach((key, value) -> {
			int keyInt;
			try {
				keyInt = Integer.parseInt(key);
			} catch (NumberFormatException ex) {
				throw parseException(object, "Key " + key + " must be an integer");
			}

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("entry with key %s inside \"%s\" must be a string", key, inside));
			}

			if (destination.put(keyInt, value.asString()) != null) {
				// TODO: warn in dev env
			}
		});
	}

	private static void readLicenses(JsonLoaderValue.ObjectImpl metadata, List<ModLicense> licenses) {
		JsonLoaderValue licensesValue = metadata.get("license");

		if (licensesValue != null) {
			switch (licensesValue.type()) {
			case ARRAY:
				for (LoaderValue license : licensesValue.asArray()) {
					licenses.add(readLicenseObject((JsonLoaderValue) license));
				}

				break;
			case OBJECT:
			case STRING:
				licenses.add(readLicenseObject(licensesValue));
				break;
			default:
				throw parseException(licensesValue, "license field must be a string, an array or an object");
			}
		}
	}

	private static ModLicense readLicenseObject(JsonLoaderValue licenseValue) {
		switch (licenseValue.type()) {
		case OBJECT: {
			JsonLoaderValue.ObjectImpl object = licenseValue.asObject();

			String name = requiredString(object, "name");
			String id = requiredString(object, "id");
			String url = requiredString(object, "url");
			@Nullable String description = string(object, "description");

			return new ModLicenseImpl(name, id, url, description);
		}
		case STRING: {
			ModLicense ret = ModLicenseImpl.fromIdentifier(licenseValue.asString());

			if (ret == null) {
				// QMJ specification says this *must* be a valid identifier if it doesn't want to use the long-form version
				throw new ParseException("A license declared as a string id must be a valid SPDX identifier");
			}

			return ret;
		}
		default:
			throw parseException(licenseValue, "License entry must be an object or string");
		}
	}

	private static void readEntrypoints(JsonLoaderValue.ObjectImpl object, String inside, Map<String, List<ModEntrypoint>> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String entrypointKey = entry.getKey();
			LoaderValue value = entry.getValue();

			// Add the entry if not already present
			destination.putIfAbsent(entrypointKey, new ArrayList<>());
			List<ModEntrypoint> entries = destination.get(entrypointKey);

			switch (value.type()) {
			case ARRAY:
				for (LoaderValue entrypoint : value.asArray()) {
					entries.add(readEntrypoint((JsonLoaderValue) entrypoint, inside));
				}

				break;
			default:
				entries.add(readEntrypoint((JsonLoaderValue) value, inside));
			}
		}
	}

	private static ModEntrypoint readEntrypoint(JsonLoaderValue entry, String inside) {
		switch (entry.type()) {
		case OBJECT:
			LoaderValue.LObject entryObject = entry.asObject();
			LoaderValue adapter = entryObject.get("adapter");
			LoaderValue value = entryObject.get("value");

			if (adapter == null) {
				throw new ParseException(String.format("entry inside \"%s\" in object form is missing the \"adapter\" field", inside));
			}

			if (value == null) {
				throw new ParseException(String.format("entry inside \"%s\" in object form is missing the \"value\" field", inside));
			}

			if (adapter.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) adapter, String.format("adapter field inside \"%s\" must be a string", inside));
			}

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("adapter field inside \"%s\" must be a string", inside));
			}

			return ModEntrypoint.create(adapter.asString(), value.asString());
		case STRING:
			// Assume `default` as language adapter
			return ModEntrypoint.create("default", entry.asString());
		default:
			throw parseException(entry, String.format("value inside \"%s\" must be a string or object", inside));
		}
	}

	static ModDependency readDependencyObject(Path from, boolean isAny, JsonLoaderValue value) {
		return new V1ModMetadataReader(from, null, null).readDependencyObject(isAny, value);
	}

	ModDependency readDependencyObject(boolean isAny, JsonLoaderValue value) {
		switch (value.type()) {
		case OBJECT:
			JsonLoaderValue.ObjectImpl obj = value.asObject();
			ModDependencyIdentifier id = new ModDependencyIdentifierImpl(requiredString(obj, "id"));
			final VersionRange range;
			try {
				JsonLoaderValue versionsValue = obj.get("versions");
				range = readVersionsValue(versionsValue);
			} catch (VersionFormatException e) {
				throw parseException(obj.get("versions"), "Unable to parse version range", e);
			}
			String reason = string(obj, "reason");
			boolean optional = bool(obj, "optional", false);
			@Nullable JsonLoaderValue unlessObj = obj.get("unless");
			ModDependency unless = null;

			if (unlessObj != null) {
				unless = readDependencyObject(true, unlessObj);
			}

			return new ModDependencyImpl.OnlyImpl(value.location(), id, range, reason, optional, unless);
		case STRING:
			// Single dependency, any version matching id
			return new ModDependencyImpl.OnlyImpl(value.location(),new ModDependencyIdentifierImpl(value.asString()));
		case ARRAY:
			// OR or all sub dependencies
			JsonLoaderValue.ArrayImpl array = value.asArray();
			Collection<ModDependency> dependencies = new ArrayList<>(array.size());

			for (LoaderValue loaderValue : array) {
				dependencies.add(readDependencyObject(isAny, (JsonLoaderValue) loaderValue));
			}

			return isAny ? new ModDependencyImpl.AnyImpl(value.location(), dependencies) : new ModDependencyImpl.AllImpl(value.location(), dependencies);
		default:
			throw parseException(
					value,
					"Dependency object must be an object or string to represent a single dependency or an array to represent any dependency"
			);
		}
	}

	static final String RFC_56_LINK = "https://github.com/QuiltMC/rfcs/pull/56";

	private VersionRange readVersionsValue(LoaderValue versionsValue) throws VersionFormatException {


		if (versionsValue == null) {
			return VersionRange.ANY;
		}

		switch (versionsValue.type()) {
			case OBJECT: {
				return readVersionsObject(versionsValue.asObject());
			}
			case ARRAY: {
				List<VersionRange> versions = new ArrayList<>();
				LoaderValue.LArray versionsArray = versionsValue.asArray();
				for (LoaderValue loaderValue : versionsArray) {
					versions.add(readVersionSpecifier((JsonLoaderValue) loaderValue));
				}
				if (!loggedDeprecatedArrayDepends) {
					loggedDeprecatedArrayDepends = true;
					if (from != null) {
						logWarningHeader();
						Log.warn(LogCategory.DISCOVERY, "- Deprecated array format for dependencies: see " + RFC_56_LINK + " for more information.");
					}
					if (warningNode != null) {
						createModJsonNode()
							.addChild(QuiltLoaderText.of("Uses deprecated array format for declaring dependencies."))
							.setDirectLevel(WarningLevel.CONCERN)
							.addChild(QuiltLoaderText.of("See " + RFC_56_LINK + " for more information on how to fix this"));
					}
				}

				VersionRange result = VersionRange.ofRanges(versions);

				checkUnnecessaryVersions(versions, versionsArray, result, VersionJoiner.ANY);

				return result;
			}
			case STRING: {
				return readVersionSpecifier(versionsValue.asString());
			}
			default: {
				throw new VersionFormatException("Expected to find a string, object, or array but found " + versionsValue);
			}
		}
	}

	private enum VersionJoiner {
		ANY {
			@Override
			VersionRange join(List<VersionRange> versions) {
				return VersionRange.ofRanges(versions);
			}
		},
		ALL {
			@Override
			VersionRange join(List<VersionRange> versions) {
				VersionRange range = null;
				for (VersionRange sub : versions) {
					range = range == null ? sub : sub.combineMatchingBoth(range);
				}
				return range;
			}
		};

		abstract VersionRange join(List<VersionRange> versions);
	}

	private void checkUnnecessaryVersions(List<VersionRange> versions, LoaderValue.LArray versionsArray,
		VersionRange result, VersionJoiner concat) {
		if (from != null || warningNode != null) {
			// Check to see if any of them are unnecessary
			for (int i = 0; i < versions.size(); i++) {
				List<VersionRange> joined = new ArrayList<>();
				joined.addAll(versions.subList(0, i));
				joined.addAll(versions.subList(i + 1, versions.size()));
				VersionRange smaller = concat.join(joined);
				if (result.equals(smaller)) {
					String msg = "Unnecessary dependency range " + versionsArray.get(i) + " (resolves as " //
						+ versions.get(i) + "), since it doesn't affect the result " + result;
					if (from != null) {
						logWarningHeader();
						Log.warn(LogCategory.DISCOVERY, "- " + msg);
					}
					if (warningNode != null) {
						createModJsonNode()
							.addChild(QuiltLoaderText.of(msg))
							.setDirectLevel(WarningLevel.CONCERN)
							.addChild(QuiltLoaderText.of("See " + RFC_56_LINK + " for more information on how to fix this"));
					}
				}
			}
		}
	}

	private void logWarningHeader() {
		if (loggedAnyWarnings) {
			return;
		}
		loggedAnyWarnings = true;
		String describedPath = manager != null ? manager.describePath(from) : from.toString();
		Log.warn(LogCategory.DISCOVERY, "Warnings for quilt.mod.json at: '" + describedPath + "'");
	}

	private PluginGuiTreeNode createModJsonNode() {
		if (warningNode == null) {
			throw new IllegalStateException("Check 'warningNode' first!");
		}
		if (modJsonNode == null) {
			modJsonNode = warningNode.addChild(QuiltLoaderText.of("quilt.mod.json"));
			modJsonNode.mainIcon(QuiltLoaderGui.iconJsonFile()).subIcon(QuiltLoaderGui.iconQuilt());
		}
		return modJsonNode;
	}

	private VersionRange readVersionsObject(LoaderValue.LObject obj) throws VersionFormatException {
		LoaderValue any = obj.get("any");
		LoaderValue all = obj.get("all");
		if (any == null && all == null) {
			throw new VersionFormatException("Expected to find either 'any' or 'all', but found '" + obj + "'");
		}

		LoaderValue value = any != null ? any : all;

		if (value.type() != LType.ARRAY) {
			String name = any != null ? "any" : "all";
			throw new VersionFormatException("Expected the value of " + name + " to be an array, but was " + value);
		}

		LoaderValue.LArray array = value.asArray();

		List<VersionRange> versions = new ArrayList<>();

		for (LoaderValue arrayValue : array) {
			final VersionRange thisRange;
			switch (arrayValue.type()) {
				case STRING: {
					thisRange = readVersionSpecifier(arrayValue.asString());
					break;
				}
				case OBJECT: {
					thisRange = readVersionsObject(arrayValue.asObject());
					break;
				}
				default: {
					throw new VersionFormatException("Expected to find either a string or an object, but found " + arrayValue);
				}
			}
			versions.add(thisRange);
		}

		VersionJoiner concat = any != null ? VersionJoiner.ANY : VersionJoiner.ALL;
		VersionRange range = concat.join(versions);

		if (range == null) {
			throw new VersionFormatException("Expected the 'versions' array to be non-empty at " + value.location());
		}

		if (range == VersionRange.NONE) {
			throw new VersionFormatException("Expected the 'versions' array to match at least one version, but it doesn't! " + value.location());
		}

		if (range == VersionRange.ANY) {
			throw new VersionFormatException("Expected the 'versions' array to restrict some versions, but it doesn't! " + value.location());
		}

		checkUnnecessaryVersions(versions, array, range, concat);

		return range;
	}

	public static boolean isConstraintCharacter(char c) {
		switch (c) {
			case '<': return true;
			case '>': return true;
			case '=': return true;
			case '~': return true;
			case '*': return true;
			case '^': return true;
			default: return false;
		}
	}

	private static VersionRange readVersionSpecifier(JsonLoaderValue value) throws VersionFormatException {
		if (value == null) {
			return VersionRange.ANY;
		}

		return readVersionSpecifier(value.asString());
	}

	public static VersionRange readVersionSpecifier(String string) throws VersionFormatException {
		if (string == null) {
			return VersionRange.ANY;
		}

		if (string.equals("*")) {
			return VersionRange.ANY;
		}

		String withoutPrefix = string.substring(1);

		switch (string.charAt(0)) {
			case '=':
				return VersionRange.ofExact(Version.of(withoutPrefix));
			case '>':
				if (string.charAt(1) == '=') {
					return VersionRange.ofInterval(Version.of(string.substring(2)), true, null, false);
				} else {
					return VersionRange.ofInterval(Version.of(withoutPrefix), false, null, false);
				}
			case '<':
				if (string.charAt(1) == '=') {
					return VersionRange.ofInterval(null, false, Version.of(string.substring(2)), true);
				} else {
					return VersionRange.ofInterval(null, false, Version.of(withoutPrefix), false);
				}
		    // Semantic versions only
			case '~': {
				Version.Semantic min = Version.Semantic.of(withoutPrefix);
				int[] components = min.semantic().versionComponents();
				if (components.length < 2) {
					components = new int[] { components[0], 1 };
				} else if (components.length == 2) {
					components[1]++;
				} else {
					components = new int[] { components[0], components[1] + 1 };
				}

				Version max = Version.Semantic.of(components, null, null);
				return VersionRange.ofInterval(min, true, max, false);
			}
			case '^': {
				Version.Semantic min = Version.Semantic.of(withoutPrefix);
				int newMajor = min.versionComponent(0) + 1;

				Version max = Version.Semantic.of(new int[] {newMajor}, null, null);
				return VersionRange.ofInterval(min, true, max, false);
			}
			default: {
				// Get just the version part of the string
				String stripped = string;
				int preIndex = string.indexOf('-');

				if (preIndex != -1) {
					stripped = string.substring(0, preIndex);

				}
				int metadataIndex = string.indexOf('+');

				if (metadataIndex != -1) {
					stripped = string.substring(0, metadataIndex);
				}

				if (stripped.endsWith(".x")) {
					if (string.indexOf(".x") != string.length() - 2) {
						throw new VersionFormatException(String.format("Invalid version specifier \"%s\"", string));
					}

					Version.Semantic min = Version.Semantic.of(string.substring(0, string.length() - 2));
					int[] components = min.versionComponents(); // Copies
					components[components.length - 1]++;
					Version.Semantic max = Version.Semantic.of(components, null, null);
					return VersionRange.ofInterval(min, true, max, false);
				} else {
					Version v = Version.of(string);

					// same as ^
					// TODO code duplication
					if (v.isSemantic()) {
						Version.Semantic min = v.semantic();
						int newMajor = min.versionComponent(0) + 1;

						Version max = Version.Semantic.of(new int[]{newMajor}, null, null);
						return VersionRange.ofInterval(min, true, max, false);
					} else {
						// same as =
						return VersionRange.ofExact(v);
					}
				}
			}
		}
	}

	private static void readMixins(JsonLoaderValue.ArrayImpl array, Map<EnvType, List<String>> destination) {
		for (LoaderValue value : array) {
			if (value.type() == LoaderValue.LType.STRING) {
				destination.computeIfAbsent(EnvType.CLIENT, (env) -> new ArrayList<>()).add(value.asString());
				destination.computeIfAbsent(EnvType.SERVER, (env) -> new ArrayList<>()).add(value.asString());
			} else if (value.type() == LoaderValue.LType.OBJECT) {
				LoaderValue.LObject object = value.asObject();
				readMixin(object, destination);
			} else {
				throw parseException((JsonLoaderValue) value, "Entry inside mixin must be a string or object");
			}
		}
	}

	private static void readMixin(LoaderValue.LObject object, Map<EnvType, List<String>> destination) {
		LoaderValue config = object.get("config");

		if (config == null) {
			throw parseException((JsonLoaderValue) object, "Mixin entry inside must have a config value");
		} else if (config.type() != LoaderValue.LType.STRING) {
			throw parseException((JsonLoaderValue) object, "Mixin entry config must be a string");
		}
		LoaderValue environment = object.get("environment");

		if (environment == null) {
			destination.computeIfAbsent(EnvType.CLIENT, (env) -> new ArrayList<>()).add(config.asString());
			destination.computeIfAbsent(EnvType.SERVER, (env) -> new ArrayList<>()).add(config.asString());
		} else {
			if (environment.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) object, "Mixin entry environment must be a string");
			}

			switch (environment.asString()) {
				case "client":
					destination.computeIfAbsent(EnvType.CLIENT, (env) -> new ArrayList<>()).add(config.asString());
					break;
				case "dedicated_server":
					destination.computeIfAbsent(EnvType.SERVER, (env) -> new ArrayList<>()).add(config.asString());
					break;
				case "*":
					destination.computeIfAbsent(EnvType.CLIENT, (env) -> new ArrayList<>()).add(config.asString());
					destination.computeIfAbsent(EnvType.SERVER, (env) -> new ArrayList<>()).add(config.asString());
					break;
				default:
					throw parseException((JsonLoaderValue) object, "Mixin entry environment must be one of 'client', 'dedicated_server', or '*'");
			}
		}
	}
}
