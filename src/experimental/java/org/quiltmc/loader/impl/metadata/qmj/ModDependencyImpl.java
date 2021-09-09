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
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;

final class ModDependencyImpl {
	ModDependencyImpl() {
	}

	static abstract class CollectionImpl extends AbstractCollection<ModDependency.Only> implements ModDependency {
		private final String location;
		private final Collection<ModDependency.Only> conditions;

		CollectionImpl(String location, Collection<ModDependency> conditions) {
			this.location = location;
			// For simplicities sake we flatten here
			List<ModDependency.Only> flattened = new ArrayList<>();
			for (ModDependency dep : conditions) {
				if (dep instanceof ModDependency.Only) {
					flattened.add((ModDependency.Only) dep);
				} else {
					if (getClass() != dep.getClass()) {
						throw new IllegalArgumentException("You cannot mix any with all!");
					}
					flattened.addAll((CollectionImpl) dep);
				}
			}
			ModDependency.Only[] array = flattened.toArray(new ModDependency.Only[0]);
			this.conditions = Collections.unmodifiableList(Arrays.asList(array));
		}

		@Override
		public Iterator<ModDependency.Only> iterator() {
			return this.conditions.iterator();
		}

		@Override
		public int size() {
			return this.conditions.size();
		}
	}

	static final class AnyImpl extends CollectionImpl implements ModDependency.Any {
		AnyImpl(String location, Collection<ModDependency> conditions) {
			super(location, conditions);
		}
	}

	static final class AllImpl extends CollectionImpl  implements ModDependency.All {
		AllImpl(String location, Collection<ModDependency> conditions) {
			super(location, conditions);
		}
	}

	static final class OnlyImpl implements ModDependency.Only {
		private static final Collection<VersionConstraint> ANY = Collections.singleton(VersionConstraint.any());
		private final String location;
		private final ModDependencyIdentifier id;
		private final Collection<VersionConstraint> constraints;
		private final String reason;
		private final boolean optional;
		private final ModDependency unless;

		/**
		 * Creates a ModDependency that matches any version of a specific mod id.
		 */
		OnlyImpl(String location, ModDependencyIdentifier id) {
			this(location, id, ANY, "", false, null);
		}
		OnlyImpl(String location, ModDependencyIdentifier id, Collection<VersionConstraint> constraints, @Nullable String reason, boolean optional, @Nullable ModDependency unless) {
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
	}
}
