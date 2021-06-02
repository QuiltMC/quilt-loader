package org.quiltmc.loader.impl.metadata.qmj;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.ModDependency;
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
		private final String id;
		private final Collection<VersionConstraint> constraints;
		private final String reason;
		private final Collection<ModDependency> unless;

		OnlyImpl(String id) {
			this(id, Collections.emptySet(), "", Collections.emptySet());
		}

		OnlyImpl(String id, Collection<VersionConstraint> constraints, @Nullable String reason, Collection<ModDependency> unless) {
			this.id = id;
			this.constraints = Collections.unmodifiableCollection(constraints);
			this.reason = reason != null ? reason : "";
			this.unless = Collections.unmodifiableCollection(unless);
		}

		@Override
		public String id() {
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
		public Collection<ModDependency> unless() {
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
