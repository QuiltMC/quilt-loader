package org.quiltmc.loader.impl.metadata.qmj;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;

import static org.quiltmc.loader.impl.metadata.qmj.ModMetadataReader.parseException;

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
		String id = null;
		String group = null;
		Version version = null;
		/* Optional fields */
		String name = null;
		String description = null;
		List<ModLicense> licenses = new ArrayList<>();
		List<ModContributor> contributors = new ArrayList<>();
		Map<String, String> contactInformation = null;
		List<ModDependency> depends = new ArrayList<>();
		List<ModDependency> breaks = new ArrayList<>();
		Icons icons = null;
		/* Internal fields */
		List<?> provides = new ArrayList<>();
		List<AdapterLoadableClassEntry> entrypoints = new ArrayList<>();
		List<AdapterLoadableClassEntry> plugins = new ArrayList<>();
		List<String> jars = new ArrayList<>();
		Map<String, String> languageAdapters = null;
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

			// TODO: Parse version

			// Now we reach optional fields
			// TODO: provides
			// TODO: entrypoints
			// TODO: plugins
			// TODO: jars
			// TODO: language adapters,
			// TODO: depends
			// TODO: breaks
			// TODO: repositories

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
				// TODO: contact
				// TODO: license
				readLicenses(metadata, licenses, spdxLicenses);
				// TODO: icon
			}
		}

		{
			// FIXME: These entries need to be moved when plugins are ready
			// TODO: Mixin
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
	 * Read an array as a collection of strings.
	 *
	 * @param array array to read from
	 * @param inside the name of the array field
	 * @return the entries
	 * @throws ParseException if any entry is not a string
	 */
	private static Collection<String> readStringCollection(JsonLoaderValue.ArrayImpl array, String inside) {
		List<String> entries = new ArrayList<>(array.size());

		for (LoaderValue value : array) {
			if (value.type() != LoaderValue.LType.STRING) {
				throw parseException((JsonLoaderValue) value, String.format("Entry inside %s must be a string", inside));
			}

			entries.add(value.getString());
		}

		return entries;
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

	private V1ModMetadataReader() {
	}
}
