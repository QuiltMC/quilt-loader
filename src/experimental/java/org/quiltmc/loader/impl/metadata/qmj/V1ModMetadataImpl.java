package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.Version;

final class V1ModMetadataImpl implements InternalModMetadata {
	private final JsonLoaderValue.ObjectImpl root;
	/* Required fields */
	private final String id;
	private final String group;
	private final Version version;
	/* Optional fields */
	private final String name;
	private final String description;
	private final Collection<ModLicense> licenses;
	private final Collection<ModContributor> contributors;
	private final Map<String, String> contactInformation;
	private final Collection<ModDependency> depends;
	private final Collection<ModDependency> breaks;
	private final Icons icons;
	/* Internal fields */
	private final Collection<?> provides;
	private final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints;
	private final Collection<AdapterLoadableClassEntry> plugins;
	private final Collection<String> jars;
	private final Map<String, String> languageAdapters;
	private final Collection<String> repositories;
	private final Collection<String> mixins;
	private final Collection<String> accessWideners;

	V1ModMetadataImpl(
			JsonLoaderValue.ObjectImpl root,
			/* Required fields */
			String id,
			String group,
			Version version,
			/* Optional fields */
			@Nullable String name,
			@Nullable String description,
			Collection<ModLicense> licenses,
			Collection<ModContributor> contributors,
			Map<String, String> contactInformation,
			Collection<ModDependency> depends,
			Collection<ModDependency> breaks,
			@Nullable Icons icons,
			/* Internal fields */
			Collection<?> provides, // TODO: Data type
			Map<String, List<AdapterLoadableClassEntry>> entrypoints,
			Collection<AdapterLoadableClassEntry> plugins,
			Collection<String> jars,
			Map<String, String> languageAdapters,
			Collection<String> repositories,
			/* TODO: Move to plugins */
			Collection<String> mixins,
			Collection<String> accessWideners
			// TODO: Custom objects - long term
	) {
		this.root = root;
		this.id = id;
		this.group = group;
		this.version = version;

		// Delegate to id if null
		if (name != null) {
			this.name = name;
		} else {
			this.name = id;
		}

		// Empty string if null
		if (description != null) {
			this.description = description;
		} else {
			this.description = "";
		}

		this.licenses = Collections.unmodifiableCollection(licenses);
		this.contributors = Collections.unmodifiableCollection(contributors);
		this.contactInformation = Collections.unmodifiableMap(contactInformation);
		this.depends = Collections.unmodifiableCollection(depends);
		this.breaks = Collections.unmodifiableCollection(breaks);

		if (icons != null) {
			this.icons = icons;
		} else {
			this.icons = new Icons.Single(null);
		}

		// Internal fields
		this.provides = Collections.unmodifiableCollection(provides);
		this.entrypoints = Collections.unmodifiableMap(entrypoints);
		this.plugins = Collections.unmodifiableCollection(plugins);
		this.jars = Collections.unmodifiableCollection(jars);
		this.languageAdapters = Collections.unmodifiableMap(languageAdapters);
		this.repositories = Collections.unmodifiableCollection(repositories);

		// Move to plugins
		this.mixins = Collections.unmodifiableCollection(mixins);
		this.accessWideners = Collections.unmodifiableCollection(accessWideners);
	}

	@Override
	public String id() {
		return this.id;
	}

	@Override
	public String group() {
		return this.group;
	}

	@Override
	public Version version() {
		return this.version;
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public String description() {
		return this.description;
	}

	@Override
	public Collection<ModLicense> licenses() {
		return this.licenses;
	}

	@Override
	public Collection<ModContributor> contributors() {
		return this.contributors;
	}

	@Nullable
	@Override
	public String getContactInfo(String key) {
		return this.contactInformation.get(key);
	}

	@Override
	public Map<String, String> contactInfo() {
		return this.contactInformation;
	}

	@Override
	public Collection<ModDependency> depends() {
		return this.depends;
	}

	@Override
	public Collection<ModDependency> breaks() {
		return this.breaks;
	}

	@Nullable
	@Override
	public String getIcon(int size) {
		return this.icons.getIcon(size);
	}

	@Override
	public boolean containsRootValue(String key) {
		return this.root.containsKey(key);
	}

	@Nullable
	@Override
	public LoaderValue getValue(String key) {
		return this.root.get(key);
	}

	@Override
	public Map<String, LoaderValue> values() {
		return this.root;
	}

	@Override
	public <T> T get(Class<T> type) throws IllegalArgumentException {
		throw new UnsupportedOperationException("Implement me!");
	}

	// Internal

	@Override
	public Collection<?> provides() {
		return this.provides;
	}

	@Nullable
	@Override
	public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints(String key) {
		return this.entrypoints;
	}

	@Override
	public Map<String, Collection<AdapterLoadableClassEntry>> getPlugins() {
		return this.plugins;
	}

	@Override
	public Collection<String> jars() {
		return this.jars;
	}

	@Override
	public Map<String, String> languageAdapters() {
		return this.languageAdapters;
	}

	@Override
	public Collection<String> repositories() {
		return this.repositories;
	}

	@Override
	public Collection<String> mixins() {
		return this.mixins;
	}

	@Override
	public Collection<String> accessWideners() {
		return this.accessWideners;
	}
}
