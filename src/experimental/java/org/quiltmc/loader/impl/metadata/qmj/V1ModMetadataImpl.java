package org.quiltmc.loader.impl.metadata.qmj;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.Version;

final class V1ModMetadataImpl implements ModMetadata {
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
			@Nullable Icons icons
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
		// TODO: Implement when we figure out custom objects
		return null;
	}
}
