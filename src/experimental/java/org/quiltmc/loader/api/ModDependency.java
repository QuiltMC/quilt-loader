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

import java.util.Collection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of a mod's dependency.
 */
@ApiStatus.NonExtendable
public interface ModDependency {

	/** Checks if this dependency should apply in the current system. This only checks static values, and doesn't
	 * check anything from the current modset. For example the minecraft loader plugin would check the "environment"
	 * field here, and if it doesn't match the current envrionment, return true. */
	boolean shouldIgnore();

	/**
	 * A mod dependency where there is only one condition that must be satisfied.
	 */
	interface Only extends ModDependency {
		/**
		 * @return the mod identifier that the dependency tries to check for
		 */
		ModDependencyIdentifier id();

		/**
		 * @return version constraints that this dependency has. This collection is never empty.
		 */
		Collection<VersionConstraint> versions();

		/**
		 * @return a reason to describe why this dependency exists. Empty if there is no reason.
		 */
		String reason();

		/**
		 * Gets the dependency that must <b>not</b> be satisfied to allow this dependency to be active.
		 *
		 * @return the mod dependency. May be null if none exists
		 */
		@Nullable
		ModDependency unless();

		/** Checks if the mod dependency is considered optional.
		 *
		 * @return if this mod dependency can be ignored if the target mod is not present or cannot load. */
		boolean optional();

		/**
		 * Checks if {@link #versions()} matches a specific version.
		 *
		 * @param version the version to check
		 * @return true if the version matches or else false
		 */
		default boolean matches(Version version) {
			for (VersionConstraint constraint : versions()) {
				if (constraint.matches(version)) {
					return true;
				}
			}

			return false;
		}
	}

	/**
	 * A mod dependency where at least one condition must be satisfied.
	 */
	interface Any extends Collection<ModDependency.Only>, ModDependency {

		/**
		 * Checks to see if all of the conditions are ignored.
		 */
		@Override
		default boolean shouldIgnore() {
			for (ModDependency.Only dep : this) {
				if (!dep.shouldIgnore()) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * A mod breakage where at all conditions must be satisfied in order to conflict.
	 */
	interface All extends Collection<ModDependency.Only>, ModDependency {
		/**
		 * Checks to see if all of the conditions are ignored.
		 */
		@Override
		default boolean shouldIgnore() {
			for (ModDependency.Only dep : this) {
				if (!dep.shouldIgnore()) {
					return false;
				}
			}

			return true;
		}
	}
}
