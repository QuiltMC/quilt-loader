package org.quiltmc.loader.impl;

import java.util.Objects;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;

public final class VersionConstraintImpl implements VersionConstraint {
	public static final VersionConstraintImpl ANY = new VersionConstraintImpl("", Type.ANY);
	private final String version;
	private final Type type;


	public static VersionConstraintImpl ofRaw(String raw) {
		if (raw.equals("*")) {
			return ANY;
		}

		for (Type value : Type.values()) {
			if (raw.startsWith(value.prefix())) {
				return new VersionConstraintImpl(raw.substring(raw.indexOf(value.prefix())), value);
			}
		}
		// Spec says that 1.0.0 is the same as ^1.0.0
		return new VersionConstraintImpl(raw, Type.SAME_MAJOR);
	}

	public VersionConstraintImpl(String version, Type type) {
		if (type == Type.ANY && !version.isEmpty()) {
			throw new IllegalArgumentException("Constraint type ANY cannot be associated with a version string!");
		}
		this.version = version;
		this.type = type;
	}

	@Override
	public String version() {
		return this.version;
	}

	@Override
	public Type type() {
		return this.type;
	}

	@Override
	public String toString() {
		return this.type.prefix() + this.version;
	}

	@Override
	public boolean equals(Object o) {
		throw new UnsupportedOperationException("Implement me!");
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.version, this.type);
	}
}
