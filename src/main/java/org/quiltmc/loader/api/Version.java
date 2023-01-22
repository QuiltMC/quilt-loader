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

package org.quiltmc.loader.api;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.impl.metadata.qmj.GenericVersionImpl;
import org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl;

/** Representation of a version. <br>
 * All implementations either implement {@link Raw} or {@link Semantic}. <br>
 * Unfortunately (due to {@link Version.Semantic} already implementing {@link Comparable}) this doesn't implement
 * {@link Comparable} directly. Instead you can either use {@link #compareTo(Version)} or {@link #COMPARATOR} for all your comparing needs. */
@ApiStatus.NonExtendable
public interface Version {

	/** Calls {@link Version#compareTo(Version)}: this doesn't accept nulls. */
	static Comparator<Version> COMPARATOR = Version::compareTo;

	static Version of(String raw) {
		try {
			return Semantic.of(raw);
		} catch (VersionFormatException ex) {
			return new GenericVersionImpl(raw);
		}
	}

	/** @return The raw string that this version was constructed from.*/
	String raw();

	default boolean isSemantic() {
		return this instanceof Version.Semantic;
	}

	default Semantic semantic() {
		return (Semantic) this;
	}

	/** If both this and the given version are semantic versions then this compares with the behaviour of
	 * {@link Semantic#compareTo(Semantic)}. <br>
	 * Otherwise both versions are compared with their {@link #raw()} strings, using the
	 * <a href="https://github.com/unascribed/FlexVer/blob/trunk/SPEC.md">FlexVer comparison scheme</a> */
	int compareTo(Version other);

	/**
	 * A string version, sorted with {@link String#compareTo(String)}.
	 */
	@ApiStatus.NonExtendable
	interface Raw extends Version, Comparable<Version> {
		// No additional method definitions
	}

	/**
	 * Representation of a semantic version
	 */
	@ApiStatus.NonExtendable
	interface Semantic extends Version, Comparable<Semantic> {

		/** A special value that represents a {@link #preRelease()} which is both empty but still present - for example
		 * "1.18.0-" (as opposed to "1.18.0", which uses the normal empty string for it's prerelease, due to the missing
		 * dash").
		 * <p>
		 * You can either compare this to {@link #preRelease()} using identity to check, or use
		 * {@link #isPreReleasePresent()}.
		 * <p>
		 * This field exists solely to keep backwards compatibility, since we can't make {@link #preRelease()} return an
		 * {@link Optional}. */
		public static final String EMPTY_BUT_PRESENT_PRERELEASE = new String();

		static Semantic of(String raw) throws VersionFormatException {
			return SemanticVersionImpl.of(raw);
		}

		@Deprecated
		static Semantic of(int major, int minor, int patch, String preRelease, String buildMetadata) throws VersionFormatException {
			return of(new int[] { major, minor, patch }, preRelease, buildMetadata);
		}

		static Semantic of(int[] components, @Nullable String preRelease, @Nullable String buildMetadata) throws VersionFormatException {
			StringBuilder raw = new StringBuilder();
			raw.append(Arrays.stream(components).mapToObj(Integer::toString).collect(Collectors.joining("."))); // 1.0.0

			if (preRelease != null && !preRelease.isEmpty()) {
				raw.append('-').append(preRelease);
			}

			if (buildMetadata != null && !buildMetadata.isEmpty()) {
				raw.append('+').append(buildMetadata);
			}
			// HACK: re-building raw makes sure that everything is verified to be valid
			return SemanticVersionImpl.of(raw.toString());
		}

		/**
		 * Returns the number of components in this version.
		 *
		 * <p>For example, {@code 1.3.x} has 3 components.</p>
		 *
		 * @return the number of components
		 */
		int versionComponentCount();

		/**
		 * Returns the version component at {@code pos}.
		 * If {@code pos} is greater than or equal to the number of components, then it returns {@code 0}.
		 * @param pos the position to check
		 * @return the version component
		 */
		int versionComponent(int index);

		/**
		 * @return An array populated with every version component, except for trailing zeros.
		 */
		int[] versionComponents();

		/**
		 * Must be a positive integer.
		 */
		default int major() {
			return versionComponent(0);
		}

		/**
		 * Must be a positive integer.
		 */
		default int minor() {
			return versionComponent(1);
		}

		/**
		 * Must be a positive integer.
		 */
		default int patch() {
			return versionComponent(2);
		}

		/**
		 * Returns an empty string if not applicable
		 */
		String preRelease();

		/** @return True if the {@link #preRelease()} field is present in this version. This is used to differentiate
		 *         versions with a dash at the end of them (like "1.18.0-", which would return true here), from "1.18.0"
		 *         (which would return false here.
		 * @see #EMPTY_BUT_PRESENT_PRERELEASE */
		default boolean isPreReleasePresent() {
			return !preRelease().isEmpty() || preRelease() == EMPTY_BUT_PRESENT_PRERELEASE;
		}

		/**
		 * Returns an empty string if not applicable
		 */
		String buildMetadata();

		// TODO: docs
		@Override
		int compareTo(Version.Semantic o);
	}
}
