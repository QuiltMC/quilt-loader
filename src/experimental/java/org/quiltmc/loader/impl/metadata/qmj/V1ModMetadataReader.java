package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;

import static org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader.parseException;

// TODO: Figure out a way to not need to always specify JsonLoaderValue everywhere so we can let other users and plugins have location data.
final class V1ModMetadataReader {
	public static V1ModMetadataImpl read(Logger logger, JsonLoaderValue.ObjectImpl root, Map<String, ModLicense> spdxLicenses) {
		// Read loader category
		@Nullable JsonLoaderValue quiltLoader = root.get("quilt_loader");

		if (quiltLoader == null) {
			throw new ParseException("quilt_loader field is required");
		}

		if (quiltLoader.type() != LoaderValue.LType.OBJECT) {
			throw parseException(quiltLoader, "quilt_loader field must be an object");
		}

		return readFields(logger, root, spdxLicenses);
	}

	private static V1ModMetadataImpl readFields(Logger logger, JsonLoaderValue.ObjectImpl root, Map<String, ModLicense> spdxLicenses) {
		/* Required fields */
		String id;
		String group;
		Version version = null;
		/* Optional fields */
		String name = null;
		String description = null;
		List<ModLicense> licenses = new ArrayList<>();
		List<ModContributor> contributors = new ArrayList<>();
		Map<String, String> contactInformation = new LinkedHashMap<>();
		List<ModDependency> depends = new ArrayList<>();
		List<ModDependency> breaks = new ArrayList<>();
		Icons icons = null;
		/* Internal fields */
		List<?> provides = new ArrayList<>();
		Map<String, List<AdapterLoadableClassEntry>> entrypoints = new LinkedHashMap<>();
		List<AdapterLoadableClassEntry> plugins = new ArrayList<>();
		List<String> jars = new ArrayList<>();
		Map<String, String> languageAdapters = new LinkedHashMap<>();
		List<String> repositories = new ArrayList<>();
		/* TODO: Move to plugins */
		List<String> mixins = new ArrayList<>();
		List<String> accessWideners = new ArrayList<>();

		JsonLoaderValue.ObjectImpl quiltLoader = (JsonLoaderValue.ObjectImpl) root.get("quilt_loader");

		// Loader metadata
		{
			// Check if our required fields are here
			id = requiredString(quiltLoader, "id");
			group = requiredString(quiltLoader, "group");

			// Versions
			@Nullable JsonLoaderValue versionValue = quiltLoader.get("version");

			if (versionValue == null) {
				throw new ParseException("version is a required field");
			}

			version = Version.of(versionValue.getString());
			// Now we reach optional fields
			// TODO: provides

			@Nullable
			JsonLoaderValue entrypointsValue = quiltLoader.get("entrypoints");

			if (entrypointsValue != null) {
				if (entrypointsValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(entrypointsValue, "entrypoints must be an object");
				}

				readAdapterLoadableClassEntries((JsonLoaderValue.ObjectImpl) entrypointsValue, "entrypoints", entrypoints);
			}

			@Nullable
			JsonLoaderValue pluginsValue = quiltLoader.get("plugins");

			if (pluginsValue != null) {
				if (pluginsValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(pluginsValue, "plugins must be an array");
				}

				for (LoaderValue entry : pluginsValue.getArray()) {
					plugins.add(readAdapterLoadableClassEntry((JsonLoaderValue) entry, "plugins"));
				}
			}

			@Nullable
			JsonLoaderValue jarsValue = quiltLoader.get("jars");

			if (jarsValue != null) {
				if (jarsValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(jarsValue, "jars must be an array");
				}

				readStringList((JsonLoaderValue.ArrayImpl) jarsValue, "jars", jars);
			}

			@Nullable
			JsonLoaderValue languageAdaptersValue = quiltLoader.get("language_adapters");

			if (languageAdaptersValue != null) {
				if (languageAdaptersValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(languageAdaptersValue, "language_adapters must be an object");
				}

				readStringMap((JsonLoaderValue.ObjectImpl) languageAdaptersValue, "language_adapters", languageAdapters);
			}

			// TODO: depends
			// TODO: breaks

			@Nullable
			JsonLoaderValue repositoriesValue = quiltLoader.get("repositories");

			if (repositoriesValue != null) {
				if (repositoriesValue.type() != LoaderValue.LType.ARRAY) {
					throw parseException(repositoriesValue, "repositories must be an array");
				}

				readStringList((JsonLoaderValue.ArrayImpl) repositoriesValue, "repositories", repositories);
			}

			// Metadata
			JsonLoaderValue metadataValue = quiltLoader.get("metadata");

			if (metadataValue != null) {
				if (metadataValue.type() != LoaderValue.LType.OBJECT) {
					throw parseException(metadataValue, "metadata must be an object");
				}

				JsonLoaderValue.ObjectImpl metadata = (JsonLoaderValue.ObjectImpl) metadataValue;

				name = string(metadata, "name");
				description = string(metadata, "description");
				// TODO: contributors

				@Nullable
				JsonLoaderValue contact = metadata.get("contact");

				if (contact != null) {
					if (contact.type() != LoaderValue.LType.OBJECT) {
						throw parseException(contact, "contact must be an object");
					}

					readStringMap((JsonLoaderValue.ObjectImpl) contact, "contact", contactInformation);
				}

				readLicenses(metadata, licenses, spdxLicenses);
				// TODO: icon
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
					readStringList((JsonLoaderValue.ArrayImpl) mixinValue, "mixin", mixins);
					break;
				case STRING:
					mixins.add(mixinValue.getString());
					break;
				default:
					throw parseException(mixinValue, "mixin value must be an array of strings or a string");
				}
			}

			// TODO: Access wideners
			// TODO: Minecraft game metadata
		}

		return new V1ModMetadataImpl(
				root,
				id,
				group,
				version,
				name,
				description,
				licenses,
				contributors,
				contactInformation,
				depends,
				breaks,
				icons,
				provides,
				entrypoints,
				plugins,
				jars,
				languageAdapters,
				repositories,
				mixins,
				accessWideners
		);
	}

	private static String requiredString(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			throw new ParseException(String.format("%s is a required field", field));
		}

		if (value.type() != LoaderValue.LType.STRING) {
			throw parseException(value, String.format("%s must be a string", field));
		}

		return value.getString();
	}

	@Nullable
	private static String string(JsonLoaderValue.ObjectImpl object, String field) {
		JsonLoaderValue value = object.get(field);

		if (value == null) {
			return null;
		}

		if (value.type() != LoaderValue.LType.STRING) {
			throw parseException(value, String.format("%s must be a string", field));
		}

		return value.getString();
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

			destination.add(value.getString());
		}
	}

	private static void readStringMap(JsonLoaderValue.ObjectImpl object, String inside, Map<String, String> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String key = entry.getKey();
			LoaderValue value = entry.getValue();

			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("entry with key %s inside \"%s\" must be a string", key, inside));
			}

			if (destination.put(key, value.getString()) != null) {
				// TODO: Warn in dev environment about duplicate keys
			}
		}
	}

	private static void readLicenses(JsonLoaderValue.ObjectImpl metadata, List<ModLicense> licenses, Map<String, ModLicense> spdxLicenses) {
		JsonLoaderValue licensesValue = metadata.get("license");

		if (licensesValue != null) {
			switch (licensesValue.type()) {
			case ARRAY:
				for (LoaderValue license : ((JsonLoaderValue.ArrayImpl) licenses)) {
					licenses.add(readLicenseObject((JsonLoaderValue) license, spdxLicenses));
				}

				break;
			case OBJECT:
			case STRING:
				licenses.add(readLicenseObject(licensesValue, spdxLicenses));
				break;
			default:
				throw parseException(licensesValue, "license field must be a string, an array or an object");
			}
		}
	}

	private static ModLicense readLicenseObject(JsonLoaderValue licenseValue, Map<String, ModLicense> spdxLicenses) {
		switch (licenseValue.type()) {
		case OBJECT: {
			JsonLoaderValue.ObjectImpl object = licenseValue.getObject();

			String name = requiredString(object, "name");
			String id = requiredString(object, "id");
			String url = requiredString(object, "url");
			@Nullable String description = string(object, "description");

			return new ModLicenseImpl(name, id, url, description != null ? description : "");
		}
		case STRING: {
			@Nullable ModLicense license = spdxLicenses.get(licenseValue.getString());

			// TODO: Emit dev time warning
			if (license == null) {
				// No entry in the reference?
				// Fill in the data the best we can.
				String id = licenseValue.getString();

				return new ModLicenseImpl(id, id, String.format("https://spdx.org/licenses/%s.html", id), "");
			}

			return license;
		}
		default:
			throw parseException(licenseValue, "License entry must be an object or string");
		}
	}

	private static void readAdapterLoadableClassEntries(JsonLoaderValue.ObjectImpl object, String inside, Map<String, List<AdapterLoadableClassEntry>> destination) {
		for (Map.Entry<String, LoaderValue> entry : object.entrySet()) {
			String entrypointKey = entry.getKey();
			LoaderValue value = entry.getValue();

			// Add the entry if not already present
			destination.putIfAbsent(entrypointKey, new ArrayList<>());
			List<AdapterLoadableClassEntry> entries = destination.get(entrypointKey);

			switch (value.type()) {
			case ARRAY:
				for (LoaderValue entrypoint : value.getArray()) {
					entries.add(readAdapterLoadableClassEntry((JsonLoaderValue) entrypoint, inside));
				}

				break;
			case STRING:
				entries.add(readAdapterLoadableClassEntry((JsonLoaderValue) value, inside));
				break;
			default:
			}
		}
	}

	private static AdapterLoadableClassEntry readAdapterLoadableClassEntry(JsonLoaderValue entry, String inside) {
		switch (entry.type()) {
		case OBJECT:
			LoaderValue.LObject entryObject = entry.getObject();
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

			return new AdapterLoadableClassEntry(adapter.getString(), value.getString());
		case STRING:
			// Assume `default` as language adapter
			return new AdapterLoadableClassEntry("default", entry.getString());
		default:
			throw parseException(entry, String.format("value inside \"%s\" must be a string or object", inside));
		}
	}

	private static ModDependency readDependencyObject(JsonLoaderValue value) {
		switch (value.type()) {
		case OBJECT:
			// Single dependency, with optional version(s) and unless criteria
			// TODO
			throw new UnsupportedOperationException("Implement me!");
		case STRING:
			// Single dependency, any version matching id
			return new ModDependencyImpl.OnlyImpl(value.getString());
		case ARRAY:
			// OR or all sub dependencies
			JsonLoaderValue.ArrayImpl array = value.getArray();
			List<ModDependency> dependencies = new ArrayList<>(array.size());

			for (LoaderValue loaderValue : array) {
				dependencies.add(readDependencyObject((JsonLoaderValue) loaderValue));
			}

			return new ModDependencyImpl.AnyImpl(dependencies);
		default:
			throw parseException(
					value,
					"Dependency object must be an object or string to represent a single dependency or an array to represent any dependency"
			);
		}
	}

	private V1ModMetadataReader() {
	}
}
