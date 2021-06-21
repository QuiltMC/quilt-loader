package org.quiltmc.loader.impl;

import java.util.Objects;

import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.impl.util.version.FabricSemanticVersionImpl;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.VersionParsingException;

public final class VersionConstraintImpl implements VersionConstraint {
	public static final VersionConstraintImpl ANY = new VersionConstraintImpl();
	private final String version;
	private final FabricSemanticVersionImpl semanticVersion;
	private final Type type;


	public static VersionConstraintImpl parse(String raw) {
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

	/**
	 * For {@link #ANY}
	 */
	private VersionConstraintImpl() {
		this.version = "";
		this.type = Type.ANY;
		this.semanticVersion = null;
	}

	public VersionConstraintImpl(String version, Type type) {
		if (type == Type.ANY) {
			throw new UnsupportedOperationException("Use the ANY field, not this constructor!");
		}
		this.version = version;
		this.type = type;
		FabricSemanticVersionImpl semVer = null;
		try {
			semVer = new FabricSemanticVersionImpl(version, true);
		} catch (VersionParsingException e) {
			// Ignored
		}
		this.semanticVersion = semVer;
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
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		VersionConstraintImpl that = (VersionConstraintImpl) o;
		return version.equals(that.version) && type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.version, this.type);
	}

	@Override
	public boolean matches(Version version) {
		if (type == Type.ANY) {
			return true;
		}

		if (version.isSemantic()) {
			Version.Semantic semantic = version.semantic();
			int[] components = { semantic.major(), semantic.minor(), semantic.patch() };
			version = new FabricSemanticVersionImpl(components, semantic.preRelease(), semantic.buildMetadata());
		}

		if (version instanceof FabricSemanticVersionImpl && semanticVersion != null) {
			FabricSemanticVersionImpl fVersion = (FabricSemanticVersionImpl) version;
			switch (type) {
				case EQUALS:
					return semanticVersion.compareTo(fVersion) == 0;
				case GREATER_THAN:
					return semanticVersion.compareTo(fVersion) > 0;
				case GREATER_THAN_OR_EQUAL:
					return semanticVersion.compareTo(fVersion) >= 0;
				case LESSER_THAN:
					return semanticVersion.compareTo(fVersion) < 0;
				case LESSER_THAN_OR_EQUAL:
					return semanticVersion.compareTo(fVersion) <= 0;
				case SAME_MAJOR:
					return semanticVersion.getVersionComponent(0) == fVersion.getVersionComponent(0);
				case SAME_MAJOR_AND_MINOR:
					return semanticVersion.getVersionComponent(0) == fVersion.getVersionComponent(0)
						&& semanticVersion.getVersionComponent(1) == fVersion.getVersionComponent(1);
				default:
					throw new IllegalStateException("Unknown VersionConstraint.Type " + type);
			}
		} else {
			if (version.raw().equals("${version}") && FabricLoader.getInstance().isDevelopmentEnvironment()) {
				// Special cased by QMJ
				return true;
			}

			if (semanticVersion != null) {
				return false;
			}
			return type == Type.EQUALS && version.raw().equals(this.version);
		}
	}
}
