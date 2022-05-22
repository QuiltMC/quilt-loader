/*
 * Copyright 2022 QuiltMC
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import net.fabricmc.api.EnvType;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.*;
import org.quiltmc.loader.api.version.Version;

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
	private final String intermediateMappings;
	private final Icons icons;
	/* Internal fields */
	private final ModLoadType loadType;
	private final Collection<ModProvided> provides;
	private final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints;
	private final Collection<AdapterLoadableClassEntry> plugins;
	private final Collection<String> jars;
	private final Map<String, String> languageAdapters;
	private final Collection<String> repositories;
	private final Collection<String> mixins;
	private final Collection<String> accessWideners;
	private final ModEnvironment environment;
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
			String intermediateMappings,
			@Nullable Icons icons,
			/* Internal fields */
			ModLoadType loadType,
			Collection<ModProvided> provides,
			Map<String, List<AdapterLoadableClassEntry>> entrypoints,
			Collection<AdapterLoadableClassEntry> plugins,
			Collection<String> jars,
			Map<String, String> languageAdapters,
			Collection<String> repositories,
			/* TODO: Move to plugins */
			Collection<String> mixins,
			Collection<String> accessWideners,
			ModEnvironment env
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
		this.intermediateMappings = intermediateMappings;

		if (icons != null) {
			this.icons = icons;
		} else {
			this.icons = new Icons.Single(null);
		}

		// Internal fields
		this.loadType = loadType;
		this.provides = Collections.unmodifiableCollection(provides);
		this.entrypoints = Collections.unmodifiableMap(entrypoints);
		this.plugins = Collections.unmodifiableCollection(plugins);
		this.jars = Collections.unmodifiableCollection(jars);
		this.languageAdapters = Collections.unmodifiableMap(languageAdapters);
		this.repositories = Collections.unmodifiableCollection(repositories);

		// Move to plugins
		this.mixins = Collections.unmodifiableCollection(mixins);
		this.accessWideners = Collections.unmodifiableCollection(accessWideners);
		this.environment = env;
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

	@Override
	public String intermediateMappings() {
		return this.intermediateMappings;
	}

	@Nullable
	@Override
	public String icon(int size) {
		return this.icons.getIcon(size);
	}

	@Override
	public boolean containsValue(String key) {
		return this.root.containsKey(key);
	}

	@Nullable
	@Override
	public LoaderValue value(String key) {
		return this.root.get(key);
	}

	@Override
	public Map<String, LoaderValue> values() {
		return this.root;
	}

	// Internal

	@Override
	public ModLoadType loadType() {
		return loadType;
	}

	@Override
	public Collection<ModProvided> provides() {
		return this.provides;
	}

	@Nullable
	@Override
	public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints() {
		return this.entrypoints;
	}

	@Override
	public Collection<AdapterLoadableClassEntry> getPlugins() {
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
	public Collection<String> mixins(EnvType env) {
		return this.mixins;
	}

	@Override
	public Collection<String> accessWideners() {
		return this.accessWideners;
	}

	@Override
	public ModEnvironment environment() {
		return environment;
	}
}
