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

package org.quiltmc.loader.impl.fabric.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

import org.quiltmc.loader.impl.metadata.EntrypointMetadata;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.NestedJarEntry;
import org.quiltmc.loader.impl.metadata.qmj.FabricModMetadataWrapper;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
final class V0ModMetadataFabric extends AbstractModMetadata implements FabricLoaderModMetadata {
	private static final Mixins EMPTY_MIXINS = new Mixins(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	// Required
	private final String id;
	private Version version;

	// Optional (Environment)
	private Collection<ModDependency> dependencies;
	private final String languageAdapter = "org.quiltmc.loader.impl.language.JavaLanguageAdapter"; // TODO: Constants class?
	private final Mixins mixins;
	private final ModEnvironment environment; // REMOVEME: Replacing Side in old metadata with this
	private final String initializer;
	private final Collection<String> initializers;

	// Optional (metadata)
	private final String name;
	private final String description;
	private final Collection<Person> authors;
	private final Collection<Person> contributors;
	private final ContactInformation links;
	private final String license;

	private final FabricModMetadataWrapper wrapped2quilt;

	V0ModMetadataFabric(String id, Version version, Collection<ModDependency> dependencies, Mixins mixins, ModEnvironment environment, String initializer, Collection<String> initializers,
						String name, String description, Collection<Person> authors, Collection<Person> contributors, ContactInformation links, String license) throws ParseMetadataException {
		this.id = id;
		this.version = version;
		this.dependencies = Collections.unmodifiableCollection(dependencies);

		if (mixins == null) {
			this.mixins = V0ModMetadataFabric.EMPTY_MIXINS;
		} else {
			this.mixins = mixins;
		}

		this.environment = environment;
		this.initializer = initializer;
		this.initializers = Collections.unmodifiableCollection(initializers);
		this.name = name;

		if (description == null) {
			this.description = "";
		} else {
			this.description = description;
		}

		this.authors = Collections.unmodifiableCollection(authors);
		this.contributors = Collections.unmodifiableCollection(contributors);
		this.links = links;
		this.license = license;

		// Must come last
		this.wrapped2quilt = new FabricModMetadataWrapper(this);
	}

	@Override
	public InternalModMetadata asQuiltModMetadata() {
		return wrapped2quilt;
	}

	@Override
	public int getSchemaVersion() {
		return 0;
	}

	@Override
	public String getType() {
		return TYPE_FABRIC_MOD;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Collection<String> getProvides() {
		return Collections.emptyList();
	}

	@Override
	public Version getVersion() {
		return this.version;
	}

	@Override
	public void setVersion(Version version) {
		this.version = version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		return this.environment;
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		return this.environment.matches(type);
	}

	@Override
	public Collection<ModDependency> getDependencies() {
		return dependencies;
	}

	@Override
	public void setDependencies(Collection<ModDependency> dependencies) {
		this.dependencies = Collections.unmodifiableCollection(dependencies);
	}

	// General metadata

	@Override
	public String getName() {
		if (this.name != null && this.name.isEmpty()) {
			return this.id;
		}

		return this.name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public Collection<Person> getAuthors() {
		return this.authors;
	}

	@Override
	public Collection<Person> getContributors() {
		return this.contributors;
	}

	@Override
	public ContactInformation getContact() {
		return this.links;
	}

	@Override
	public Collection<String> getLicense() {
		return Collections.singleton(this.license);
	}

	@Override
	public Optional<String> getIconPath(int size) {
		// honor Mod Menu's de-facto standard
		return Optional.of("assets/" + getId() + "/icon.png");
	}

	@Override
	public String getOldStyleLanguageAdapter() {
		return this.languageAdapter;
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		return Collections.emptyMap();
	}

	@Override
	public boolean containsCustomValue(String key) {
		return false;
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return null;
	}

	// Internals

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		return Collections.emptyMap();
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getOldInitializers() {
		if (this.initializer != null) {
			return Collections.singletonList(this.initializer);
		} else if (!this.initializers.isEmpty()) {
			return this.initializers;
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		return Collections.emptyList();
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		return Collections.emptyList();
	}

	@Override
	public void emitFormatWarnings() {
		if (getSchemaVersion() < FabricModMetadataReader.LATEST_VERSION) {
			Log.warn(LogCategory.METADATA, "Mod ID " + getId() + " uses outdated schema version: " + getSchemaVersion() + " < " + FabricModMetadataReader.LATEST_VERSION);
		}
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		List<String> mixinConfigs = new ArrayList<>(this.mixins.common);

		switch (type) {
		case CLIENT:
			mixinConfigs.addAll(this.mixins.client);
			break;
		case SERVER:
			mixinConfigs.addAll(this.mixins.server);
			break;
		}

		return mixinConfigs;
	}

	@Override
	public String getAccessWidener() {
		return null; // intentional null
	}

	static final class Mixins {
		final Collection<String> client;
		final Collection<String> common;
		final Collection<String> server;

		Mixins(Collection<String> client, Collection<String> common, Collection<String> server) {
			this.client = Collections.unmodifiableCollection(client);
			this.common = Collections.unmodifiableCollection(common);
			this.server = Collections.unmodifiableCollection(server);
		}
	}
}
