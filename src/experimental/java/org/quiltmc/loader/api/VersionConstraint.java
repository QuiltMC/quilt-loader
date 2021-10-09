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

package org.quiltmc.loader.api;

import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.loader.impl.VersionConstraintImpl;

@ApiStatus.NonExtendable
public interface VersionConstraint {
	static VersionConstraint any() {
		return VersionConstraintImpl.ANY;
	}

	/**
	 * @return the version of this version constraint. Always an empty string if {@link #type()} is {@link Type#ANY}
	 */
	String version();

	/**
	 * @return the type of version constraint.
	 */
	Type type();

	/**
	 * @return a string representation of this version constraint
	 */
	@Override
	String toString();

	/**
	 * Checks if this version constraint matches the given version.
	 */
	boolean matches(Version version);

	/**
	 * Checks if this version constraint matches another version constraint.
	 *
	 * @param o the other version constraint
	 * @return true if the version constraints match, false if the constraints do not match or the object is not a
	 * version constraint
	 */
	@Override
	boolean equals(Object o);

	/**
	 * Represents a type of version constraint.
	 */
	enum Type {
		/**
		 * A version constraint which allows all versions.
		 */
		ANY("*"),

		/**
		 * A version constraint where the versions must match.
		 */
		EQUALS("="),

		/**
		 * A version constraint where the version must be equal to or greater than the constraint's requirement.
		 */
		GREATER_THAN_OR_EQUAL(">="),

		/**
		 * A version constraint where the version must be equal to or less than the constraint's requirement.
		 */
		LESSER_THAN_OR_EQUAL("<="),

		/**
		 * A version constraint where the version must be greater than the constraint's requirement.
		 */
		GREATER_THAN(">"),

		/**
		 * A version constraint where the version must be less than the constraint's requirement.
		 */
		LESSER_THAN("<"),

		/**
		 * A version constraint where the major component of the version must match the constraint's major component.
		 */
		SAME_MAJOR("^"),

		/**
		 * A version constraint where the major and minor components of the version must match the constraint's major
		 * and minor components.
		 */
		SAME_MAJOR_AND_MINOR("~");

		private final String prefix;

		Type(String prefix) {
			this.prefix = prefix;
		}

		/**
		 * @return a stringified representation of a type of version constraint
		 */
		public String prefix() {
			return this.prefix;
		}
	}
}
