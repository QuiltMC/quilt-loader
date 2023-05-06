/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
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

import java.util.Objects;

import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionConstraint;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@Deprecated
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public final class VersionConstraintImpl implements VersionConstraint {
	public static final VersionConstraintImpl ANY = new VersionConstraintImpl();
	private final String versionString;
	private final Version versionObj;
	private final Type type;

	public static boolean isConstraintCharacter(char c) {
		switch (c) {
			case '<': return true;
			case '>': return true;
			case '=': return true;
			case '~': return true;
			case '*': return true;
			case '^': return true;
			default: return false;
		}
	}

	public static VersionConstraintImpl parse(String raw) {
		if (raw.equals("*")) {
			return ANY;
		}

		for (Type value : Type.values()) {
			if (raw.startsWith(value.prefix())) {
				return new VersionConstraintImpl(raw.substring(value.prefix().length()), value);
			}
		}
		// Spec says that 1.0.0 is the same as ^1.0.0
		return new VersionConstraintImpl(raw, Type.SAME_TO_NEXT_MAJOR);
	}

	/**
	 * For {@link #ANY}
	 */
	private VersionConstraintImpl() {
		this.versionString = "";
		this.type = Type.ANY;
		this.versionObj = null;
	}

	public VersionConstraintImpl(String version, Type type) {
		if (type == Type.ANY) {
			throw new UnsupportedOperationException("Use the ANY field, not this constructor!");
		}
		this.versionString = version;
		this.type = type;
		this.versionObj = Version.of(version);
	}

	public VersionConstraintImpl(Version version, Type type) {
		if (type == Type.ANY) {
			throw new UnsupportedOperationException("Use the ANY field, not this constructor!");
		}
		this.versionString = version.raw();
		this.type = type;
		this.versionObj = version;
	}

	@Override
	public String version() {
		return this.versionString;
	}

	@Override
	public Type type() {
		return this.type;
	}

	@Override
	public String toString() {
		return this.type.prefix() + this.versionString;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		VersionConstraintImpl that = (VersionConstraintImpl) o;
		return versionString.equals(that.versionString) && type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.versionString, this.type);
	}

	@Override
	public boolean matches(Version version) {
		if (type == Type.ANY) {
			return true;
		}

		if (version.raw().equals("${version}") && QuiltLoader.isDevelopmentEnvironment()) {
			// Special cased by QMJ
			return true;
		}

		switch (type) {
			case EQUALS:
				return version.compareTo(versionObj) == 0;
			case GREATER_THAN:
				return version.compareTo(versionObj) > 0;
			case GREATER_THAN_OR_EQUAL:
				return version.compareTo(versionObj) >= 0;
			case LESSER_THAN:
				return version.compareTo(versionObj) < 0;
			case LESSER_THAN_OR_EQUAL:
				return version.compareTo(versionObj) <= 0;
			default: {
				if (version.isSemantic() && versionObj.isSemantic()) {
					Version.Semantic inVer = version.semantic();
					Version.Semantic cmpVer = versionObj.semantic();
					switch (type) {
						case SAME_MAJOR:
							return inVer.major() == cmpVer.major();
						case SAME_MAJOR_AND_MINOR:
							return inVer.major() == cmpVer.major()
								&& inVer.minor() == cmpVer.minor();
						case SAME_TO_NEXT_MAJOR:
							return inVer.compareTo(versionObj) >= 0
								&& inVer.major() == cmpVer.major();
						case SAME_TO_NEXT_MINOR:
							return inVer.compareTo(versionObj) >= 0
								&& inVer.major() == cmpVer.major()
								&& inVer.minor() == cmpVer.minor();
						default:
							throw new IllegalStateException("Unknown VersionConstraint.Type " + type);
					}
				} else {
					return false;
				}
			}
		}
	}
}
