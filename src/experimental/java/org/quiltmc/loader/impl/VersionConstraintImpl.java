package org.quiltmc.loader.impl;

import java.util.Objects;

import org.quiltmc.loader.api.VersionConstraint;

public final class VersionConstraintImpl implements VersionConstraint {
	private final String version;
	private final Type type;

	public VersionConstraintImpl(String version, Type type) {
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
