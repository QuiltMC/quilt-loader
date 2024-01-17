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

package org.quiltmc.loader.impl.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.ModMetadata.ProvidedMod;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModEntrypoint;
import org.quiltmc.loader.impl.fabric.metadata.CustomValueImpl;
import org.quiltmc.loader.impl.fabric.metadata.MapBackedContactInformation;
import org.quiltmc.loader.impl.fabric.metadata.SimplePerson;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import net.fabricmc.api.EnvType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class GeneralExt2FabricMetadata implements FabricLoaderModMetadata {

	final ModMetadataExt meta;
	final ModContainerExt container;
	private net.fabricmc.loader.api.Version version;
	private final Collection<String> provides;
	private final Collection<ModDependency> depsAndBreaks;
	private final Collection<ModDependency> depends;
	private final Collection<ModDependency> breaks;
	private final Collection<Person> authors;
	private final Collection<Person> contributors;
	private final ContactInformation contact;
	private final Collection<String> licenses;
	private final Map<String, CustomValue> customValues;

	public GeneralExt2FabricMetadata(ModMetadataExt meta, ModContainerExt container) {
		this.meta = meta;
		this.container = container;

		ArrayList<String> provides = new ArrayList<>();
		for (ProvidedMod provided : meta.provides()) {
			provides.add(provided.id());
		}
		this.provides = Collections.unmodifiableCollection(provides);
		ArrayList<ModDependency> depsAndBreaks = new ArrayList<>();
		ArrayList<ModDependency> depends = new ArrayList<>();
		ArrayList<ModDependency> breaks = new ArrayList<>();
		for (org.quiltmc.loader.api.ModDependency dep : meta.depends()) {
			if (dep instanceof org.quiltmc.loader.api.ModDependency.Only) {
				Quilt2FabricModDependency q2f = new Quilt2FabricModDependency(true, (org.quiltmc.loader.api.ModDependency.Only) dep);
				depsAndBreaks.add(q2f);
				depends.add(q2f);
			}
		}
		for (org.quiltmc.loader.api.ModDependency dep : meta.breaks()) {
			if (dep instanceof org.quiltmc.loader.api.ModDependency.Only) {
				Quilt2FabricModDependency q2f = new Quilt2FabricModDependency(false, (org.quiltmc.loader.api.ModDependency.Only) dep);
				depsAndBreaks.add(q2f);
				breaks.add(q2f);
			}
		}
		this.depsAndBreaks = Collections.unmodifiableCollection(depsAndBreaks);
		this.depends = Collections.unmodifiableCollection(depends);
		this.breaks = Collections.unmodifiableCollection(breaks);
		ArrayList<Person> authors = new ArrayList<>();
		ArrayList<Person> contributors = new ArrayList<>();
		for (ModContributor contributor : meta.contributors()) {
			// "Owner" is the only defined role in the QMJ spec
			if (contributor.roles().contains("Owner")) {
				authors.add(new SimplePerson(contributor.name()));
			} else {
				contributors.add(new SimplePerson(contributor.name()));
			}
		}
		this.authors = Collections.unmodifiableCollection(authors);
		this.contributors = Collections.unmodifiableCollection(contributors);
		this.contact = new MapBackedContactInformation(meta.contactInfo());
		ArrayList<String> licenses = new ArrayList<>();
		for (ModLicense license : this.meta.licenses()) {
			licenses.add(license.id()); // Convention seems to be to use the IDs in fabric metadata
		}
		this.licenses = Collections.unmodifiableCollection(licenses);

		HashMap<String, CustomValue> cvs = new HashMap<>();
		meta.values().forEach((k, v) -> cvs.put(k, convertToCv(v)));
		this.customValues = Collections.unmodifiableMap(cvs);
	}

	private static CustomValue convertToCv(LoaderValue value) {
		switch (value.type()) {
			case OBJECT:
				Map<String, CustomValue> cvMap = new HashMap<>();
				value.asObject().forEach((k, v) -> cvMap.put(k, convertToCv(v)));
				return new CustomValueImpl.ObjectImpl(cvMap);
			case ARRAY:
				List<CustomValue> cvList = new ArrayList<>();
				value.asArray().forEach((v) -> cvList.add(convertToCv(v)));
				return new CustomValueImpl.ArrayImpl(cvList);
			case STRING:
				return new CustomValueImpl.StringImpl(value.asString());
			case NUMBER:
				return new CustomValueImpl.NumberImpl(value.asNumber());
			case BOOLEAN:
				return new CustomValueImpl.BooleanImpl(value.asBoolean());
			case NULL:
				return CustomValueImpl.NULL;
			default:
				throw new IllegalStateException("Unexpected LoaderValue type " + value.type());
		}
	}

	@Override
	public ModMetadataExt asQuiltModMetadata() {
		return meta;
	}

	@Override
	public void setVersion(Version version) {

	}

	@Override
	public String getType() {
		if (container == null) {
			return "quilt";
		}
		switch (container.getSourceType()) {
			case BUILTIN:
				return "builtin";
			case NORMAL_FABRIC:
				return "fabric";
			case NORMAL_QUILT:
				return "quilt";
			case OTHER:
			default:
				return "unknown";
		}
	}

	@Override
	public String getId() {
		return meta.id();
	}

	@Override
	public Collection<String> getProvides() {
		return provides;
	}

	@Override
	public Version getVersion() {
		if (version == null) {
			try {
				version = Version.parse(meta.version().raw());
			} catch (VersionParsingException e) {
				throw new Error(e);
			}
		}
		return version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		switch (meta.environment()) {
			case CLIENT:
				return ModEnvironment.CLIENT;
			case SERVER:
				return ModEnvironment.SERVER;
			case UNIVERSAL:
				return ModEnvironment.UNIVERSAL;
			default:
				throw new AssertionError();
		}
	}

	@Override
	public Collection<ModDependency> getDependencies() {
		return depsAndBreaks;
	}

	@Override
	public void setDependencies(Collection<ModDependency> dependencies) {
		// Not permitted. We will need to create a dependency override system for quilt in the future though.
	}

	@Override
	public Collection<ModDependency> getDepends() {
		return depends;
	}

	@Override
	public Collection<ModDependency> getRecommends() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getSuggests() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getConflicts() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ModDependency> getBreaks() {
		return breaks;
	}

	@Override
	public String getName() {
		return meta.name();
	}

	@Override
	public String getDescription() {
		return meta.description();
	}

	@Override
	public Collection<Person> getAuthors() {
		return authors;
	}

	@Override
	public Collection<Person> getContributors() {
		return contributors;
	}

	@Override
	public ContactInformation getContact() {
		return contact;
	}

	@Override
	public Collection<String> getLicense() {
		return licenses;
	}

	@Override
	public Optional<String> getIconPath(int size) {
		return Optional.ofNullable(meta.icon(size));
	}

	@Override
	public boolean containsCustomValue(String key) {
		return customValues.containsKey(key);
	}

	@Override
	public @Nullable CustomValue getCustomValue(String key) {
		return customValues.get(key);
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		return customValues;
	}

	@Override
	public boolean containsCustomElement(String key) {
		return containsCustomValue(key);
	}

	// Fabric's internal ModMetadata

	private static UnsupportedOperationException internalError() {
		throw new UnsupportedOperationException("Fabric-internal metadata is not exposed for quilt mods - since only quilt loader itself may use this.");
	}

	@Override
	public int getSchemaVersion() {
		throw internalError();
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		throw internalError();
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		throw internalError();
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		throw internalError();
	}

	@Override
	public @Nullable String getAccessWidener() {
		throw internalError();
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		throw internalError();
	}

	@Override
	public Collection<String> getOldInitializers() {
		return Collections.emptyList();
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		List<EntrypointMetadata> list = new ArrayList<>();
		Collection<ModEntrypoint> quiltList = meta.getEntrypoints().get(type);
		if (quiltList == null) {
			return list;
		}
		for (ModEntrypoint entrypoint : quiltList) {
			AdapterLoadableClassEntry data = (AdapterLoadableClassEntry) entrypoint;
			list.add(new EntrypointMetadata() {
				@Override
				public String getValue() {
					return data.getValue();
				}

				@Override
				public String getAdapter() {
					return data.getAdapter();
				}
			});
		}
		return list;
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		return meta.getEntrypoints().keySet();
	}

	@Override
	public void emitFormatWarnings() {

	}

	static final class Quilt2FabricModDependency implements ModDependency {

		final boolean isDepends;
		final org.quiltmc.loader.api.ModDependency.Only quiltDep;

		public Quilt2FabricModDependency(boolean isDepends, org.quiltmc.loader.api.ModDependency.Only quiltDep) {
			this.isDepends = isDepends;
			this.quiltDep = quiltDep;
		}

		@Override
		public Kind getKind() {
			return isDepends ? Kind.DEPENDS : Kind.BREAKS;
		}

		@Override
		public String getModId() {
			return quiltDep.id().id();
		}

		@Override
		public boolean matches(Version version) {
			if (version instanceof org.quiltmc.loader.api.Version) {
				return quiltDep.matches((org.quiltmc.loader.api.Version) version);
			} else {
				return quiltDep.matches(org.quiltmc.loader.api.Version.of(version.getFriendlyString()));
			}
		}

		@Override
		public Collection<VersionPredicate> getVersionRequirements() {
			return Collections.emptyList();
		}

		@Override
		public List<VersionInterval> getVersionIntervals() {
			return Collections.emptyList();
		}
	}
}
