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
	public static V1ModMetadataImpl read(Logger logger, JsonLoaderValue.ObjectImpl root) {
		// Read loader category
		@Nullable JsonLoaderValue quiltLoader = root.get("quilt_loader");

		if (quiltLoader == null) {
			throw new ParseException("quilt_loader field is required");
		}

		if (quiltLoader.type() != LoaderValue.LType.OBJECT) {
			throw parseException(quiltLoader, "quilt_loader field must be an object");
		}

		return readFields(logger, root);
	}

	private static V1ModMetadataImpl readFields(Logger logger, JsonLoaderValue.ObjectImpl root) {
		/* Required fields */
		String id = null;
		String group = null;
		Version version = null;
		/* Optional fields */
		String name = null;
		String description = null;
		Set<ModLicense> licenses = null;
		Set<ModContributor> contributors = null;
		Map<String, String> contactInformation = null;
		Set<ModDependency> depends = null;
		Set<ModDependency> breaks = null;
		Icons icons = null;
		/* Internal fields */
		Set<?> provides = null;
		Set<AdapterLoadableClassEntry> entrypoints = null;
		Set<AdapterLoadableClassEntry> plugins = null;
		Set<String> jars = null;
		Map<String, String> languageAdapters = null;
		Set<String> repositories = null;
		/* TODO: Move to plugins */
		Set<String> mixins = null;
		Set<String> accessWideners = null;

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
			JsonLoaderValue metadata = quiltLoader.get("metadata");

			if (metadata != null) {
				if (metadata.type() != LoaderValue.LType.OBJECT) {
					throw parseException(metadata, "metadata must be an object");
				}

				// TODO: name
				// TODO: description
				// TODO: contributors
				// TODO: contact
				// TODO: license
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

	private V1ModMetadataReader() {
	}
}
