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

import java.util.*;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.impl.metadata.*;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;

import net.fabricmc.api.EnvType;

public class QuiltModMetadataWrapperFabric implements FabricLoaderModMetadata {
	private final InternalModMetadata quiltMeta;
	private Version version;
	private final Collection<Person> authors;
	private final Collection<Person> contributors;
	private final ContactInformation contact;
	private final Collection<String> licenses;
	private final Map<String, CustomValue> customValues;

	public QuiltModMetadataWrapperFabric(InternalModMetadata quiltMeta) {
		this.quiltMeta = quiltMeta;
		ArrayList<Person> authors = new ArrayList<>();
		ArrayList<Person> contributors = new ArrayList<>();
		for (ModContributor contributor : quiltMeta.contributors()) {
			// "Owner" is the only defined role in the QMJ spec
			if (contributor.role().equals("Owner")) {
				authors.add(new SimplePerson(contributor.name()));
			} else {
				contributors.add(new SimplePerson(contributor.name()));
			}
		}
		this.authors = Collections.unmodifiableCollection(authors);
		this.contributors = Collections.unmodifiableCollection(contributors);
		this.contact = new MapBackedContactInformation(quiltMeta.contactInfo());
		ArrayList<String> licenses = new ArrayList<>();
		for (ModLicense license : this.quiltMeta.licenses()) {
			licenses.add(license.id()); // Convention seems to be to use the IDs in fabric metadata
		}
		this.licenses = Collections.unmodifiableCollection(licenses);

		HashMap<String, CustomValue> cvs = new HashMap<>();
		quiltMeta.values().forEach((k, v) -> cvs.put(k, convertToCv(v)));
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
	public InternalModMetadata asQuiltModMetadata() {
		return quiltMeta;
	}

	@Override
	public void setVersion(Version version) {

	}

	@Override
	public String getType() {
		return "quilt";
	}

	@Override
	public String getId() {
		return quiltMeta.id();
	}

	@Override
	public Collection<String> getProvides() {
		throw new UnsupportedOperationException("Provides cannot be represented as a Fabric construct");
	}

	@Override
	public Version getVersion() {
		if (version == null) {
			try {
				version = Version.parse(quiltMeta.version().raw());
			} catch (VersionParsingException e) {
				throw new Error(e);
			}
		}
		return version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		switch (quiltMeta.environment()) {
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
		// TODO: we might want to approximate this in the future, but for now let's see how this is actually used
		throw new UnsupportedOperationException("Quilt dependencies cannot be represented as a Fabric construct");
	}

	@Override
	public void setDependencies(Collection<ModDependency> dependencies) {
		// Not permitted. We will need to create a dependency override system for quilt in the future though.
	}

	@Override
	public Collection<ModDependency> getDepends() {
		// TODO: we might want to approximate this in the future, but for now let's see how this is actually used
		throw new UnsupportedOperationException("Quilt dependencies cannot be represented as a Fabric construct!");
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
		// TODO: we might want to approximate this in the future, but for now let's see how this is actually used
		throw new UnsupportedOperationException("Quilt dependencies cannot be represented as a Fabric construct!");
	}

	@Override
	public String getName() {
		return quiltMeta.name();
	}

	@Override
	public String getDescription() {
		return quiltMeta.description();
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
		return Optional.ofNullable(quiltMeta.icon(size));
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
		throw internalError();
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		throw internalError();
	}

	@Override
	public void emitFormatWarnings() {

	}
}
