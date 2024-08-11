/*
 * Copyright 2022, 2023 QuiltMC
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
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.json5.exception.ParseException;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.metadata.ModEnvironment;

import net.fabricmc.api.EnvType;

import org.quiltmc.loader.impl.util.SystemProperties;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
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
	final Icons icons;
	/* Internal fields */
	private final ModLoadType loadType;
	private final Collection<ProvidedMod> provides;
	private final Map<String, Collection<ModEntrypoint>> entrypoints;
	private final Collection<String> jars;
	private final Map<String, String> languageAdapters;
	private final Collection<String> repositories;
	private final Map<EnvType, Collection<String>> mixins;
	private final Collection<String> accessWideners;
	private final ModEnvironment environment;
	private final @Nullable ModPlugin plugin;
	private QuiltModMetadataWrapperFabric cache2fabricNoContainer;
	private QuiltModMetadataWrapperFabric cache2fabricWithContainer;

	V1ModMetadataImpl(
			V1ModMetadataBuilder builder
			// TODO: Custom objects - long term
	) {
		this.root = builder.root;
		this.id = builder.id;
		this.group = builder.group;
		this.version = builder.version;
		
		if (id == null || id.isEmpty() || !Patterns.VALID_MOD_ID.matcher(builder.id).matches()) {
			throw new IllegalArgumentException("Invalid / null ID '" + id + "'");
		}

		if (group == null || group.isEmpty() || !Patterns.VALID_MAVEN_GROUP.matcher(builder.group).matches()) {
			throw new IllegalArgumentException("Invalid / null group '" + id + "'");
		}

		if (version == null) {
			throw new NullPointerException("version");
		}

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

		this.licenses = Collections.unmodifiableCollection(builder.licenses);
		this.contributors = Collections.unmodifiableCollection(builder.contributors);
		this.contactInformation = Collections.unmodifiableMap(builder.contactInformation);
		this.depends = Collections.unmodifiableCollection(builder.depends);
		this.breaks = Collections.unmodifiableCollection(builder.breaks);
		this.intermediateMappings = builder.intermediateMappings;

		if (builder.icons != null) {
			this.icons = builder.icons;
		} else {
			this.icons = new Icons.Single(null);
		}

		// Internal fields
		this.loadType = builder.loadType;
		this.provides = Collections.unmodifiableCollection(builder.provides);
		this.entrypoints = Collections.unmodifiableMap(builder.entrypoints);
		this.jars = Collections.unmodifiableCollection(builder.jars);
		this.languageAdapters = Collections.unmodifiableMap(builder.languageAdapters);
		this.repositories = Collections.unmodifiableCollection(builder.repositories);

		// Move to plugins
		this.mixins = Collections.unmodifiableMap(builder.mixins);
		this.accessWideners = Collections.unmodifiableCollection(builder.accessWideners);
		this.environment = builder.env;

		// Experimental
		ModPlugin plugin;
		@Nullable JsonLoaderValue e = root.get("experimental_quilt_loader_plugin");
		if (e == null) {
			plugin = null;
		} else {
			if (!Boolean.getBoolean(SystemProperties.ENABLE_EXPERIMENTAL_LOADING_PLUGINS)) {
				throw new ParseException("Mod " + asQuiltModMetadata().id() + " provides a loader plugin, which is not yet allowed!");
			}
			// humorous error message dirties the log + again makes it clear you shouldn't be doing this
			Log.error(LogCategory.GENERAL, "MOD " + asQuiltModMetadata().id() + " PROVIDES A PLUGIN!" +
					"MOD-PROVIDED PLUGINS ARE FOR AMUSEMENT PURPOSES ONLY." +
					" NO WARRANTY IS PROVIDED, EXPRESS OR IMPLIED. CONTINUE AT YOUR OWN RISK.");

			LoaderValue.LObject obj = e.asObject();
			String clazz = obj.get("class").asString();
			List<String> packages = obj.get("packages").asArray().stream().map(LoaderValue::asString).collect(Collectors.toList());

			plugin = new ModPlugin() {
				@Override
				public String pluginClass() {
					return clazz;
				}

				@Override
				public Collection<String> packages() {
					return packages;
				}
			};
		}
		this.plugin = plugin;
	}

	@Override
	public FabricLoaderModMetadata asFabricModMetadata() {
		if (cache2fabricNoContainer == null) {
			cache2fabricNoContainer = new QuiltModMetadataWrapperFabric(this, null);
		}
		return cache2fabricNoContainer;
	}

	@Override
	public FabricLoaderModMetadata asFabricModMetadata(ModContainerExt quiltContainer) {
		if (cache2fabricWithContainer == null) {
			cache2fabricNoContainer = cache2fabricWithContainer = new QuiltModMetadataWrapperFabric(this, quiltContainer);
		}
		return cache2fabricWithContainer;
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
	public @Nullable ModPlugin plugin() {
		return this.plugin;
	}

	@Override
	public Collection<ProvidedMod> provides() {
		return this.provides;
	}

	@Nullable
	@Override
	public Map<String, Collection<ModEntrypoint>> getEntrypoints() {
		return this.entrypoints;
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
		return this.mixins.getOrDefault(env, Collections.emptyList());
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
