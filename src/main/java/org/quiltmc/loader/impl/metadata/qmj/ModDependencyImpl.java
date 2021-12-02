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

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.VersionConstraint;

final class ModDependencyImpl {
	ModDependencyImpl() {
	}

	static class AnyImpl extends AbstractCollection<ModDependency.Entry> implements ModDependency.Any {
		private final String location;
		private final Collection<Entry> conditions;
		private final Kind kind;
		AnyImpl(String location, Collection<ModDependency> conditions, Kind kind) {
			this.location = location;
			// For simplicities sake we flatten here
			List<Entry> flattened = new ArrayList<>();
			for (ModDependency dep : conditions) {
				if (dep instanceof Entry) {
					flattened.add((Entry) dep);
				} else {
					if (getClass() != dep.getClass()) {
						throw new IllegalArgumentException("You cannot mix any with all!");
					}
					flattened.addAll((AnyImpl) dep);
				}
			}
			Entry[] array = flattened.toArray(new Entry[0]);
			this.conditions = Collections.unmodifiableList(Arrays.asList(array));
			this.kind = kind;
		}

		@Override
		public Iterator<Entry> iterator() {
			return this.conditions.iterator();
		}

		@Override
		public int size() {
			return this.conditions.size();
		}

		@Override
		public String toString() {
			return location;
		}

		@Override
		public Kind kind() {
			return kind;
		}
	}


	static final class EntryImpl implements ModDependency.Entry {
		private static final Collection<VersionConstraint> ANY = Collections.singleton(VersionConstraint.any());
		private final String location;
		private final ModDependencyIdentifier id;
		private final Collection<VersionConstraint> constraints;
		private final String reason;
		private final boolean optional;
		private final ModDependency unless;
		private final Kind kind;

		/**
		 * Creates a ModDependency that matches any version of a specific mod id.
		 */
		EntryImpl(String location, ModDependencyIdentifier id, Kind kind) {
			this(location, id, ANY, "", false, null, kind);
		}
		EntryImpl(String location, ModDependencyIdentifier id, Collection<VersionConstraint> constraints, @Nullable String reason, boolean optional, @Nullable ModDependency unless, Kind kind) {
			// We need to have at least one constraint
			if (constraints.isEmpty()) {
				throw new IllegalArgumentException("A ModDependency must have at least one constraint");
			}
			this.location = location;
			this.id = id;
			this.constraints = Collections.unmodifiableCollection(constraints);
			this.reason = reason != null ? reason : "";
			this.optional = optional;
			this.unless = unless;
			this.kind = kind;
		}

		@Override
		public ModDependencyIdentifier id() {
			return this.id;
		}

		@Override
		public Collection<VersionConstraint> versions() {
			return this.constraints;
		}

		@Override
		public String reason() {
			return this.reason;
		}

		@Override
		public @Nullable ModDependency unless() {
			return this.unless;
		}

		@Override
		public boolean optional() {
			return this.optional;
		}

		@Override
		public boolean shouldIgnore() {
			// TODO: Read fields for loader plugins, and check them earlier and store the result in a field!
			return false;
		}

		@Override
		public Kind kind() {
			return kind;
		}

		@Override
		public String toString() {
			return location;
		}
	}
}
