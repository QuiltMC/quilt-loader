package org.quiltmc.loader.impl.metadata.qmj;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;

final class ModDependencyImpl {
	ModDependencyImpl() {
	}

	static final class AnyImpl extends AbstractCollection<ModDependency> implements ModDependency.Any {
		private final Collection<ModDependency> conditions;

		AnyImpl(Collection<ModDependency> conditions) {
			this.conditions = Collections.unmodifiableCollection(conditions);
		}

		@Override
		public Iterator<ModDependency> iterator() {
			return this.conditions.iterator();
		}

		@Override
		public int size() {
			return this.conditions.size();
		}
	}

	static final class OnlyImpl implements ModDependency.Only {
		private static final Collection<VersionConstraint> ANY = Collections.singleton(VersionConstraint.any());
		private final ModDependencyIdentifier id;
		private final Collection<VersionConstraint> constraints;
		private final String reason;
		private final ModDependency unless;

		/**
		 * Creates a ModDependency that matches any version of a specific mod id.
		 */
		OnlyImpl(ModDependencyIdentifier id) {
			this(id, ANY, "", null);
		}
		OnlyImpl(ModDependencyIdentifier id, Collection<VersionConstraint> constraints, @Nullable String reason, @Nullable ModDependency unless) {
			// We need to have at least one constraint
			if (constraints.isEmpty()) {
				throw new IllegalArgumentException("A ModDependency must have at least one constraint");
			}
			this.id = id;
			this.constraints = Collections.unmodifiableCollection(constraints);
			this.reason = reason != null ? reason : "";
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
		public boolean active() {
			throw new UnsupportedOperationException("Implement me!");
		}

		@Override
		public boolean matches(Version version) {
			throw new UnsupportedOperationException("Implement me!");
		}
	}
}
