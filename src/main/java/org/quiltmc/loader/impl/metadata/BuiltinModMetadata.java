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

package org.quiltmc.loader.impl.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import org.quiltmc.loader.impl.util.version.VersionDeserializer;

public final class BuiltinModMetadata extends AbstractModMetadata {
	private final String id;
	private final Version version;
	private final ModEnvironment environment;
	private final String name;
	private final String description;
	private final Collection<Person> authors;
	private final Collection<Person> contributors;
	private final ContactInformation contact;
	private final Collection<String> license;
	private final NavigableMap<Integer, String> icons;
	private final Collection<ModDependency> depends;
	private final Collection<ModDependency> recommends;
	private final Collection<ModDependency> suggests;
	private final Collection<ModDependency> conflicts;
	private final Collection<ModDependency> breaks;

	private BuiltinModMetadata(String id, Version version,
			ModEnvironment environment,
			String name, String description,
			Collection<Person> authors, Collection<Person> contributors,
			ContactInformation contact,
			Collection<String> license,
			NavigableMap<Integer, String> icons,
			Collection<ModDependency> depends,
			Collection<ModDependency> recommends,
			Collection<ModDependency> suggests,
			Collection<ModDependency> conflicts,
			Collection<ModDependency> breaks) {

		this.id = id;
		this.version = version;
		this.environment = environment;
		this.name = name;
		this.description = description;
		this.authors = authors;
		this.contributors = contributors;
		this.contact = contact;
		this.license = license;
		this.icons = icons;
		this.depends = depends;
		this.recommends = recommends;
		this.suggests = suggests;
		this.conflicts = conflicts;
		this.breaks = breaks;
	}

	@Override
	public String getType() {
		return "builtin";
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Collection<String> getProvides() {
		return Collections.emptyList();
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public ModEnvironment getEnvironment() {
		return environment;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
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
		return license;
	}

	@Override
	public Optional<String> getIconPath(int size) {
		if (icons.isEmpty()) return Optional.empty();

		Integer key = size;
		Entry<Integer, String> ret = icons.ceilingEntry(key);
		if (ret == null) ret = icons.lastEntry();

		return Optional.of(ret.getValue());
	}

	@Override
	public Collection<ModDependency> getDepends() { return depends; }
	@Override
	public Collection<ModDependency> getRecommends() { return recommends; }
	@Override
	public Collection<ModDependency> getSuggests() { return suggests; }
	@Override
	public Collection<ModDependency> getConflicts() { return conflicts; }
	@Override
	public Collection<ModDependency> getBreaks() { return breaks; }
	@Override
	public boolean containsCustomValue(String key) { return false; }
	@Override
	public CustomValue getCustomValue(String key) { return null; }
	@Override
	public Map<String, CustomValue> getCustomValues() { return Collections.emptyMap(); }

	public static class Builder {
		private final String id;
		private final Version version;
		private ModEnvironment environment = ModEnvironment.UNIVERSAL;
		private String name;
		private String description = "";
		private final Collection<Person> authors = new ArrayList<>();
		private final Collection<Person> contributors = new ArrayList<>();
		private ContactInformation contact = ContactInformation.EMPTY;
		private final Collection<String> license = new ArrayList<>();
		private final NavigableMap<Integer, String> icons = new TreeMap<>();
		private final Collection<ModDependency> depends = new ArrayList<>();
		private final Collection<ModDependency> recommends = new ArrayList<>();
		private final Collection<ModDependency> suggests = new ArrayList<>();
		private final Collection<ModDependency> conflicts = new ArrayList<>();
		private final Collection<ModDependency> breaks = new ArrayList<>();

		public Builder(String id, String version) {
			this.name = this.id = id;

			try {
				this.version = VersionDeserializer.deserializeSemantic(version);
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		}

		public Builder setEnvironment(ModEnvironment environment) {
			this.environment = environment;
			return this;
		}

		public Builder setName(String name) {
			this.name = name;
			return this;
		}

		public Builder setDescription(String description) {
			this.description = description;
			return this;
		}

		public Builder addAuthor(String name, Map<String, String> contactMap) {
			this.authors.add(createPerson(name, contactMap));
			return this;
		}

		public Builder addContributor(String name, Map<String, String> contactMap) {
			this.contributors.add(createPerson(name, contactMap));
			return this;
		}

		public Builder setContact(ContactInformation contact) {
			this.contact = contact;
			return this;
		}

		public Builder addLicense(String license) {
			this.license.add(license);
			return this;
		}

		public Builder addIcon(int size, String path) {
			this.icons.put(size, path);
			return this;
		}
		public Builder addDepends(ModDependency dependency) {
			this.depends.add(dependency);
			return this;
		}

		public Builder addRecommends(ModDependency dependency) {
			this.recommends.add(dependency);
			return this;
		}

		public Builder addSuggests(ModDependency dependency) {
			this.suggests.add(dependency);
			return this;
		}

		public Builder addConflicts(ModDependency dependency) {
			this.conflicts.add(dependency);
			return this;
		}

		public Builder addBreaks(ModDependency dependency) {
			this.breaks.add(dependency);
			return this;
		}


		public ModMetadata build() {
			return new BuiltinModMetadata(id, version, environment, name, description, authors, contributors, contact, license, icons, depends, recommends, suggests, conflicts, breaks);
		}

		private static Person createPerson(String name, Map<String, String> contactMap) {
			return new Person() {
				@Override
				public String getName() {
					return name;
				}

				@Override
				public ContactInformation getContact() {
					return contact;
				}

				private final ContactInformation contact = contactMap.isEmpty() ? ContactInformation.EMPTY : new MapBackedContactInformation(contactMap);
			};
		}
	}
}
