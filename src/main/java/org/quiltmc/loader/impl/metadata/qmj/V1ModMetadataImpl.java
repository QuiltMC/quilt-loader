/*
 * Copyright 2016 FabricMC
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.LoaderValue.LObject;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataBuilder;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import net.fabricmc.api.EnvType;

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
	private final ModLoadType loadType;
	private final Collection<ModProvided> provides;
	private final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints;
	private final ModPlugin plugin;
	private final Collection<String> jars;
	private final Map<String, String> languageAdapters;
	private final Collection<String> repositories;
	private final Collection<String> mixins;
	private final Collection<String> accessWideners;
	private final ModEnvironment environment;

	V1ModMetadataImpl(V1ModMetadataBuilder builder) {
		this.root = builder.root;
		this.id = builder.id;
		this.group = builder.group;
		this.version = builder.version;

		// Delegate to id if null
		if (builder.name != null) {
			this.name = builder.name;
		} else {
			this.name = builder.id;
		}

		// Empty string if null
		if (builder.description != null) {
			this.description = builder.description;
		} else {
			this.description = "";
		}

		this.licenses = uCopyList(builder.licenses);
		this.contributors = uCopyList(builder.contributors);
		this.contactInformation = uCopyHashMap(builder.contactInformation);
		this.depends = uCopyList(builder.depends);
		this.breaks = uCopyList(builder.breaks);

		if (builder.icons != null) {
			this.icons = builder.icons;
		} else {
			this.icons = new Icons.Single(null);
		}

		// Internal fields
		this.loadType = builder.loadType;
		this.provides = uCopyCollection(builder.provides);
		this.entrypoints = uCopyHashMap(builder.entrypoints, list -> uCopyCollection(list));
		this.plugin = builder.plugin;
		this.jars = uCopyList(builder.jars);
		this.languageAdapters = uCopyHashMap(builder.languageAdapters);
		this.repositories = uCopyCollection(builder.repositories);

		// Move to plugins
		this.mixins = uCopyCollection(builder.mixins);
		this.accessWideners = uCopyCollection(builder.accessWideners);
		this.environment = builder.env;
	}

	private static <T> List<T> uCopyList(List<T> src) {
		return Collections.unmodifiableList(new ArrayList<>(src));
	}

	private static <T> Collection<T> uCopyCollection(Collection<T> src) {
		return Collections.unmodifiableCollection(new ArrayList<>(src));
	}

	private static <K, V> Map<K, V> uCopyHashMap(Map<K, V> src) {
		return Collections.unmodifiableMap(new HashMap<>(src));
	}

	private static <K, VF, VT> Map<K, VT> uCopyHashMap(Map<K, VF> src, Function<VF, VT> valueFn) {
		HashMap<K, VT> map = new HashMap<>();
		for (Map.Entry<K, VF> entry : src.entrySet()) {
			map.put(entry.getKey(), valueFn.apply(entry.getValue()));
		}
		return Collections.unmodifiableMap(map);
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
	public boolean isQuiltDeps() {
		return true;
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

	@Nullable
	@Override
	public ModPlugin plugin() {
		return plugin;
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

	@Override
	public boolean hasField(ModMetadataField field) {
		return true;
	}

	@Override
	public ModMetadataBuilder copyToBuilder() {
		return copyToBuilder(root, id, group, version);
	}

	@Override
	public ModMetadataBuilder copyToBuilder(LoaderValue.LObject root, String id, String group, Version version) {
		ModMetadataBuilder builder = V1ModMetadataBuilder.of(root, id, group, version);
		// TODO: Put every field into the builder!
	}
}
